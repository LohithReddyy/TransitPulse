package com.transitpulse.service;

import com.transitpulse.dto.StopDelayDto;
import com.transitpulse.dto.TripInvestigationDto;
import com.transitpulse.entity.ActualMovement;
import com.transitpulse.entity.Incident;
import com.transitpulse.entity.Route;
import com.transitpulse.entity.ScheduledMovement;
import com.transitpulse.entity.Stop;
import com.transitpulse.entity.Trip;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.ActualMovementRepository;
import com.transitpulse.repository.IncidentRepository;
import com.transitpulse.repository.RouteRepository;
import com.transitpulse.repository.ScheduledMovementRepository;
import com.transitpulse.repository.StopRepository;
import com.transitpulse.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delay investigation for a single trip.
 *
 * <p>Implements the same arithmetic as the Python engine:
 * {@code delay = actual_arrival - scheduled_arrival}, computed only where an
 * arrival was actually observed. A stop with no GPS event, or one skipped by
 * a diversion, yields a null delay and a MISSING class. It is never treated
 * as an on-time arrival, which would understate the disruption.
 */
@Service
@Transactional(readOnly = true)
public class DelayAnalysisService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final ScheduledMovementRepository scheduledRepository;
    private final ActualMovementRepository actualRepository;
    private final IncidentRepository incidentRepository;
    private final ConfigurationService configuration;
    private final EngineOutputService engineOutput;

    public DelayAnalysisService(TripRepository tripRepository,
                                RouteRepository routeRepository,
                                StopRepository stopRepository,
                                ScheduledMovementRepository scheduledRepository,
                                ActualMovementRepository actualRepository,
                                IncidentRepository incidentRepository,
                                ConfigurationService configuration,
                                EngineOutputService engineOutput) {
        this.tripRepository = tripRepository;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.scheduledRepository = scheduledRepository;
        this.actualRepository = actualRepository;
        this.incidentRepository = incidentRepository;
        this.configuration = configuration;
        this.engineOutput = engineOutput;
    }

    /** Delay at every stop of a trip, in schedule order. */
    public List<StopDelayDto> stopDelays(String tripId) {
        List<ScheduledMovement> scheduled =
                scheduledRepository.findByTripIdOrderByStopSequenceAsc(tripId);
        if (scheduled.isEmpty()) {
            throw new ResourceNotFoundException("Trip", tripId);
        }
        Map<Integer, ActualMovement> actuals =
                actualRepository.findByTripIdOrderByStopSequenceAsc(tripId).stream()
                        .collect(Collectors.toMap(ActualMovement::getStopSequence,
                                a -> a, (a, b) -> a));
        Map<String, String> stopNames = stopRepository.findAll().stream()
                .collect(Collectors.toMap(Stop::getStopId, Stop::getStopName,
                        (a, b) -> a));

        List<StopDelayDto> out = new ArrayList<>();
        for (ScheduledMovement sm : scheduled) {
            ActualMovement am = actuals.get(sm.getStopSequence());
            StopDelayDto dto = new StopDelayDto();
            dto.setStopSequence(sm.getStopSequence());
            dto.setStopId(sm.getStopId());
            dto.setStopName(stopNames.getOrDefault(sm.getStopId(), sm.getStopId()));
            dto.setScheduledArrival(sm.getScheduledArrival());

            if (am == null) {
                dto.setDelayClass("MISSING");
                dto.setDataQualityFlag("NO_RECORD");
                dto.setObserved(false);
                dto.setNote("No actual movement record exists for this stop. "
                        + "Excluded from delay statistics.");
                out.add(dto);
                continue;
            }

            dto.setDataQualityFlag(am.getDataQualityFlag());
            dto.setDiverted("Y".equals(am.getIsDiverted()));
            dto.setActualArrival(am.getActualArrival());
            dto.setObserved(am.isComparable());

            if (am.isComparable()) {
                double delay = minutesBetween(sm.getScheduledArrival(),
                        am.getActualArrival());
                dto.setDelayMin(delay);
                dto.setDelayClass(configuration.classifyDelay(delay));
            } else {
                dto.setDelayClass("MISSING");
                dto.setNote("Y".equals(am.getIsDiverted())
                        ? "Stop not served: the vehicle was diverted. Comparing it "
                          + "against the planned schedule would be invalid."
                        : "No GPS arrival event recorded. Never counted as zero delay.");
            }
            out.add(dto);
        }
        return out;
    }

    /** The full investigation payload for one trip. */
    public TripInvestigationDto investigate(String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        List<StopDelayDto> delays = stopDelays(tripId);

        TripInvestigationDto dto = new TripInvestigationDto();
        dto.setTripId(tripId);
        dto.setRouteId(trip.getRouteId());
        dto.setRouteName(routeRepository.findById(trip.getRouteId())
                .map(Route::getRouteName).orElse(trip.getRouteId()));
        dto.setVehicleId(trip.getVehicleId());
        dto.setServiceDate(String.valueOf(trip.getServiceDate()));
        dto.setStopDelays(delays);

        List<StopDelayDto> observed = delays.stream()
                .filter(d -> d.getDelayMin() != null).toList();
        dto.setStopsTotal(delays.size());
        dto.setStopsObserved(observed.size());
        dto.setStopsMissing((int) delays.stream()
                .filter(d -> d.getDelayMin() == null).count());

        if (!observed.isEmpty()) {
            double max = observed.stream().mapToDouble(StopDelayDto::getDelayMin)
                    .max().orElse(0.0);
            double finalDelay = observed.get(observed.size() - 1).getDelayMin();
            double onTime = configuration.onTimeThresholdMin();
            double recovery = Math.max(0.0, round(max - finalDelay));

            dto.setMaxDelayMin(round(max));
            dto.setFinalDelayMin(round(finalDelay));
            dto.setDelayClass(configuration.classifyDelay(max));
            dto.setRecoveryMin(recovery);
            // Only a trip that was genuinely late can be said to have recovered.
            dto.setRecovered(max >= onTime
                    && recovery >= configuration.recoveryMinReductionMin()
                    && finalDelay < onTime);
            observed.stream().filter(d -> d.getDelayMin() >= onTime).findFirst()
                    .ifPresent(d -> dto.setFirstDelayStopSequence(d.getStopSequence()));
        } else {
            dto.setDelayClass("MISSING");
        }

        double coverage = delays.isEmpty() ? 0.0
                : (double) observed.size() / delays.size();
        dto.setDataConfidence(coverage >= 0.9 ? "HIGH"
                : coverage >= 0.6 ? "MEDIUM" : "LOW");

        dto.setPropagation(propagationFor(tripId));
        dto.setAffectedConnections(connectionsFor(tripId));
        dto.setRelatedIncidents(incidentsFor(trip));
        dto.setExplanation(explain(dto));
        return dto;
    }

    /** Propagation links where this trip is the source, from the engine. */
    public List<Map<String, Object>> propagationFor(String tripId) {
        try {
            return engineOutput.rows("propagation", "links").stream()
                    .filter(r -> tripId.equals(String.valueOf(r.get("source_trip_id"))))
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> connectionsFor(String tripId) {
        try {
            return engineOutput.rows("connection_risk", "connections").stream()
                    .filter(r -> tripId.equals(String.valueOf(r.get("feeder_trip_id"))))
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> incidentsFor(Trip trip) {
        List<Incident> incidents =
                incidentRepository.findByRouteIdOrderByStartTimeDesc(trip.getRouteId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Incident i : incidents) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("incidentId", i.getIncidentId());
            m.put("incidentType", i.getIncidentType());
            m.put("severity", i.getSeverity());
            m.put("startTime", i.getStartTime());
            m.put("endTime", i.getEndTime());
            m.put("description", i.getDescription());
            m.put("isDiversion", i.getIsDiversion());
            out.add(m);
        }
        return out;
    }

    /** Plain-language account of what happened, for the investigation page. */
    private String explain(TripInvestigationDto dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getMaxDelayMin() == null) {
            return "No arrival was observed anywhere on trip " + dto.getTripId()
                    + ", so no delay can be calculated. This trip is excluded from "
                    + "performance statistics rather than counted as on time.";
        }
        sb.append("Trip ").append(dto.getTripId())
          .append(" on route ").append(dto.getRouteName())
          .append(" reached a maximum delay of ").append(dto.getMaxDelayMin())
          .append(" min");
        if (dto.getFirstDelayStopSequence() != null) {
            sb.append(", first falling behind at stop ")
              .append(dto.getFirstDelayStopSequence());
        }
        sb.append(". It finished ").append(dto.getFinalDelayMin()).append(" min late");
        if (dto.isRecovered()) {
            sb.append(", having recovered ").append(dto.getRecoveryMin())
              .append(" min along the way");
        }
        sb.append(". ");
        if (dto.getStopsMissing() > 0) {
            sb.append(dto.getStopsMissing())
              .append(" of ").append(dto.getStopsTotal())
              .append(" stops had no usable observation and were excluded, so data "
                      + "confidence is ").append(dto.getDataConfidence()).append(". ");
        }
        int links = dto.getPropagation() == null ? 0 : dto.getPropagation().size();
        if (links > 0) {
            sb.append("The engine identified ").append(links)
              .append(" downstream service(s) this delay could have reached; each "
                      + "carries its own evidence and confidence level. ");
        }
        int conns = dto.getAffectedConnections() == null
                ? 0 : dto.getAffectedConnections().size();
        if (conns > 0) {
            sb.append(conns).append(" timetabled transfer connection(s) depend on this trip.");
        }
        return sb.toString().trim();
    }

    /** Vehicle continuity: the other trips worked by this trip's vehicle. */
    public Map<String, Object> vehicleChain(String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tripId", tripId);
        out.put("vehicleId", trip.getVehicleId());
        if (trip.getVehicleId() == null) {
            out.put("chain", List.of());
            out.put("note", "This trip has no vehicle assigned, so vehicle-continuity "
                    + "propagation cannot be assessed for it.");
            return out;
        }
        List<Map<String, Object>> chain = new ArrayList<>();
        for (Trip t : tripRepository
                .findByVehicleIdOrderByScheduledStartTimeAsc(trip.getVehicleId())) {
            Map<String, Object> m = new HashMap<>();
            m.put("tripId", t.getTripId());
            m.put("routeId", t.getRouteId());
            m.put("scheduledStartTime", t.getScheduledStartTime());
            m.put("isCurrent", t.getTripId().equals(tripId));
            chain.add(m);
        }
        out.put("chain", chain);
        out.put("turnaroundMin", configuration.vehicleTurnaroundMin());
        return out;
    }

    /** Minutes from {@code from} to {@code to}, rounded to 2 decimal places. */
    public static double minutesBetween(java.time.LocalDateTime from,
                                        java.time.LocalDateTime to) {
        return round(Duration.between(from, to).toSeconds() / 60.0);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
