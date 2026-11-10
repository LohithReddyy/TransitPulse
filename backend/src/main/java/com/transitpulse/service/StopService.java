package com.transitpulse.service;

import com.transitpulse.dto.StopSummaryDto;
import com.transitpulse.entity.Stop;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.PassengerVolumeRepository;
import com.transitpulse.repository.RouteStopRepository;
import com.transitpulse.repository.StopRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stops, with route connections, passenger volume and delay behaviour.
 *
 * <p>Low passenger volume is reported rather than hidden: statistics drawn
 * from a handful of boardings are marked LOW confidence so they are not
 * read as strongly as well-sampled ones.
 */
@Service
@Transactional(readOnly = true)
public class StopService {

    private final StopRepository stopRepository;
    private final RouteStopRepository routeStopRepository;
    private final PassengerVolumeRepository passengerVolumeRepository;
    private final EngineOutputService engineOutput;
    private final int lowVolumeThreshold;

    public StopService(StopRepository stopRepository,
                       RouteStopRepository routeStopRepository,
                       PassengerVolumeRepository passengerVolumeRepository,
                       EngineOutputService engineOutput,
                       @Value("${transitpulse.analysis.low-volume-stop-threshold:20}")
                       int lowVolumeThreshold) {
        this.stopRepository = stopRepository;
        this.routeStopRepository = routeStopRepository;
        this.passengerVolumeRepository = passengerVolumeRepository;
        this.engineOutput = engineOutput;
        this.lowVolumeThreshold = lowVolumeThreshold;
    }

    public List<Stop> findAll() {
        return stopRepository.findAll();
    }

    public Stop findById(String stopId) {
        return stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop", stopId));
    }

    public List<StopSummaryDto> summaries(String search, boolean interchangeOnly,
                                          boolean lowVolumeOnly) {
        List<Stop> stops = (search == null || search.isBlank())
                ? stopRepository.findAll()
                : stopRepository.search(search.trim());

        if (interchangeOnly) {
            stops = stops.stream()
                    .filter(s -> "Y".equals(s.getIsInterchange()))
                    .collect(Collectors.toList());
        }

        Map<String, Map<String, Object>> performance = performanceByStop();

        List<StopSummaryDto> out = new ArrayList<>();
        for (Stop stop : stops) {
            StopSummaryDto dto = new StopSummaryDto();
            dto.setStopId(stop.getStopId());
            dto.setStopName(stop.getStopName());
            dto.setStopType(stop.getStopType());
            dto.setInterchange("Y".equals(stop.getIsInterchange()));
            dto.setRouteConnections(routeStopRepository.findByStopId(stop.getStopId())
                    .stream().map(rs -> rs.getRouteId()).distinct().sorted().toList());

            Long boardings = passengerVolumeRepository.totalBoardingsForStop(stop.getStopId());
            long volume = boardings == null ? 0L : boardings;
            dto.setPassengerVolume(volume);
            Double wait = passengerVolumeRepository.averageWaitForStop(stop.getStopId());
            dto.setAverageWaitMin(wait == null ? null : round(wait));

            Map<String, Object> perf = performance.get(stop.getStopId());
            if (perf != null) {
                dto.setAvgDelayMin(RouteService.asDouble(perf.get("avg_delay_min")));
                dto.setDelayFrequencyPct(
                        RouteService.asDouble(perf.get("delay_frequency_pct")));
                Double obs = RouteService.asDouble(perf.get("observations"));
                dto.setObservations(obs == null ? 0L : obs.longValue());
            }

            boolean low = volume < lowVolumeThreshold;
            dto.setLowVolume(low);
            dto.setConfidence(low || dto.getObservations() < 5 ? "LOW"
                    : dto.getObservations() < 15 ? "MEDIUM" : "HIGH");
            out.add(dto);
        }

        if (lowVolumeOnly) {
            out = out.stream().filter(StopSummaryDto::isLowVolume)
                    .collect(Collectors.toList());
        }
        return out;
    }

    public Map<String, Map<String, Object>> performanceByStop() {
        try {
            return engineOutput.rows("stop_performance", "stops").stream()
                    .filter(r -> r.get("stop_id") != null)
                    .collect(Collectors.toMap(
                            r -> r.get("stop_id").toString(), r -> r, (a, b) -> a));
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
