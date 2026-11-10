package com.transitpulse.service;

import com.transitpulse.repository.ActualMovementRepository;
import com.transitpulse.repository.IncidentRepository;
import com.transitpulse.repository.RouteRepository;
import com.transitpulse.repository.StopRepository;
import com.transitpulse.repository.TransferConnectionRepository;
import com.transitpulse.repository.TripRepository;
import com.transitpulse.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics endpoints: dashboard, propagation, connection risk, passenger
 * impact, sensitivity and the worked example.
 *
 * <p>Counts of physical things (routes, stops, trips) come from the database.
 * Everything analytical comes from the Python engine, so the engine remains
 * the single definition of each metric.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final EngineOutputService engineOutput;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final ActualMovementRepository actualMovementRepository;
    private final IncidentRepository incidentRepository;
    private final TransferConnectionRepository connectionRepository;

    public AnalyticsService(EngineOutputService engineOutput,
                            RouteRepository routeRepository,
                            StopRepository stopRepository,
                            TripRepository tripRepository,
                            VehicleRepository vehicleRepository,
                            ActualMovementRepository actualMovementRepository,
                            IncidentRepository incidentRepository,
                            TransferConnectionRepository connectionRepository) {
        this.engineOutput = engineOutput;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.actualMovementRepository = actualMovementRepository;
        this.incidentRepository = incidentRepository;
        this.connectionRepository = connectionRepository;
    }

    /** Dashboard KPIs. Falls back to database counts if the engine has not run. */
    public Map<String, Object> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean engineReady = engineOutput.isAvailable("dashboard");

        if (engineReady) {
            out.putAll(engineOutput.loadObject("dashboard"));
        }

        // Physical counts always come from the database, which is authoritative
        // for what exists in the network.
        out.put("totalRoutes", routeRepository.count());
        out.put("totalStops", stopRepository.count());
        out.put("totalVehicles", vehicleRepository.count());
        out.put("totalScheduledTrips", tripRepository.count());
        out.put("totalConnections", connectionRepository.count());
        out.put("totalIncidents", incidentRepository.count());
        out.put("openIncidents", incidentRepository.countByStatusNot("CLOSED"));
        out.put("missingGpsRecords",
                actualMovementRepository.countByDataQualityFlag("MISSING_GPS"));
        out.put("divertedRecords",
                actualMovementRepository.countByDataQualityFlag("DIVERTED_SKIPPED"));
        out.put("analyticsAvailable", engineReady);
        if (!engineReady) {
            out.put("analyticsNotice",
                    "Delay analytics have not been generated yet. Run: "
                            + "cd data-engine && python -m transitpulse_engine.pipeline");
        }
        return out;
    }

    public Map<String, Object> propagation(String mechanism, String confidence,
                                           Integer limit) {
        Map<String, Object> payload = engineOutput.loadObject("propagation");
        List<Map<String, Object>> links = engineOutput.rows("propagation", "links");

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> link : links) {
            if (mechanism != null && !mechanism.isBlank()
                    && !"ALL".equalsIgnoreCase(mechanism)
                    && !mechanism.equalsIgnoreCase(String.valueOf(link.get("mechanism")))) {
                continue;
            }
            if (confidence != null && !confidence.isBlank()
                    && !"ALL".equalsIgnoreCase(confidence)
                    && !confidence.equalsIgnoreCase(String.valueOf(link.get("confidence")))) {
                continue;
            }
            filtered.add(link);
        }
        filtered.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> numeric(m.get("propagated_delay_min")))
                .reversed());
        if (limit != null && limit > 0 && filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", payload.get("summary"));
        out.put("links", filtered);
        out.put("returned", filtered.size());
        out.put("totalAvailable", links.size());
        out.put("note", "Each link carries its mechanism, evidence and a confidence "
                + "level. SHARED_STOP links are correlation only and are never "
                + "presented as a demonstrated cause.");
        return out;
    }

    public Map<String, Object> connectionRisk(String status, Integer limit) {
        Map<String, Object> payload = engineOutput.loadObject("connection_risk");
        List<Map<String, Object>> conns =
                engineOutput.rows("connection_risk", "connections");

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> c : conns) {
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)
                    && !status.equalsIgnoreCase(String.valueOf(c.get("status")))) {
                continue;
            }
            filtered.add(c);
        }
        filtered.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> numeric(m.get("risk_score"))).reversed());
        if (limit != null && limit > 0 && filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", payload.get("summary"));
        out.put("connections", filtered);
        out.put("returned", filtered.size());
        out.put("totalAvailable", conns.size());
        return out;
    }

    public Map<String, Object> passengerImpact() {
        Map<String, Object> payload = engineOutput.loadObject("passenger_impact");
        Map<String, Object> out = new LinkedHashMap<>(payload);
        out.put("isEstimate", true);
        out.put("caveat", "All passenger figures are modelled estimates derived from "
                + "average volume profiles. They are not confirmed passenger counts "
                + "and should not be reported as such.");
        return out;
    }

    public Map<String, Object> sensitivity() {
        return engineOutput.loadObject("sensitivity");
    }

    public Map<String, Object> workedExample() {
        return engineOutput.loadObject("worked_example");
    }

    public Map<String, Object> validation() {
        return engineOutput.loadObject("validation");
    }

    public List<Map<String, Object>> routePerformance() {
        return engineOutput.rows("route_performance", "routes");
    }

    public List<Map<String, Object>> stopPerformance() {
        return engineOutput.rows("stop_performance", "stops");
    }

    public List<Map<String, Object>> tripSummary() {
        return engineOutput.rows("trip_delay_summary", "trips");
    }

    /** Delay class distribution, for the Analytics page charts. */
    public Map<String, Object> delayDistribution() {
        Map<String, Object> dashboard = engineOutput.loadObject("dashboard");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("distribution", dashboard.get("delayClassDistribution"));
        out.put("averageDelayMin", dashboard.get("averageDelayMin"));
        out.put("p90DelayMin", dashboard.get("p90DelayMin"));
        out.put("maxDelayMin", dashboard.get("maxDelayMin"));
        out.put("onTimePercentage", dashboard.get("onTimePercentage"));
        return out;
    }

    /** Recovery analytics derived from the engine's per-trip summary. */
    public Map<String, Object> recovery() {
        List<Map<String, Object>> trips = tripSummary();
        long delayed = 0;
        long recovered = 0;
        double totalRecoveryMin = 0.0;
        List<Map<String, Object>> recoveredTrips = new ArrayList<>();

        for (Map<String, Object> t : trips) {
            double max = numeric(t.get("max_delay_min"));
            if (max >= 2.0) {
                delayed++;
                if (Boolean.TRUE.equals(t.get("recovered"))) {
                    recovered++;
                    totalRecoveryMin += numeric(t.get("recovery_min"));
                    recoveredTrips.add(t);
                }
            }
        }
        recoveredTrips.sort(Comparator.comparingDouble(
                (Map<String, Object> m) -> numeric(m.get("recovery_min"))).reversed());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("delayedTrips", delayed);
        out.put("recoveredTrips", recovered);
        out.put("recoveryPercentage", delayed == 0 ? 0.0
                : Math.round((double) recovered / delayed * 10000.0) / 100.0);
        out.put("averageRecoveryMin", recovered == 0 ? 0.0
                : Math.round(totalRecoveryMin / recovered * 100.0) / 100.0);
        out.put("topRecoveries", recoveredTrips.size() > 10
                ? recoveredTrips.subList(0, 10) : recoveredTrips);
        return out;
    }

    public Map<String, Object> engineStatus() {
        return engineOutput.status();
    }

    private static double numeric(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
