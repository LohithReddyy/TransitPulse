package com.transitpulse.service;

import com.transitpulse.dto.RouteSummaryDto;
import com.transitpulse.entity.Route;
import com.transitpulse.entity.RouteStop;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.RouteRepository;
import com.transitpulse.repository.RouteStopRepository;
import com.transitpulse.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Routes, enriched with performance figures from the engine where available. */
@Service
@Transactional(readOnly = true)
public class RouteService {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripRepository tripRepository;
    private final EngineOutputService engineOutput;

    public RouteService(RouteRepository routeRepository,
                        RouteStopRepository routeStopRepository,
                        TripRepository tripRepository,
                        EngineOutputService engineOutput) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.tripRepository = tripRepository;
        this.engineOutput = engineOutput;
    }

    public List<Route> findAll() {
        return routeRepository.findAll();
    }

    public Route findById(String routeId) {
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route", routeId));
    }

    public List<Route> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        return routeRepository.search(term.trim());
    }

    public List<RouteStop> stopsOf(String routeId) {
        findById(routeId);
        return routeStopRepository.findByRouteIdOrderByStopSequenceAsc(routeId);
    }

    /** Routes with stop counts, trip counts and engine performance metrics. */
    public List<RouteSummaryDto> summaries(String search, String mode) {
        List<Route> routes = (search == null || search.isBlank())
                ? routeRepository.findAll()
                : routeRepository.search(search.trim());

        if (mode != null && !mode.isBlank() && !"ALL".equalsIgnoreCase(mode)) {
            routes = routes.stream()
                    .filter(r -> mode.equalsIgnoreCase(r.getMode()))
                    .collect(Collectors.toList());
        }

        Map<String, Map<String, Object>> performance = performanceByRoute();

        List<RouteSummaryDto> out = new ArrayList<>();
        for (Route route : routes) {
            RouteSummaryDto dto = new RouteSummaryDto();
            dto.setRouteId(route.getRouteId());
            dto.setRouteName(route.getRouteName());
            dto.setMode(route.getMode());
            dto.setOriginName(route.getOriginName());
            dto.setDestinationName(route.getDestinationName());
            dto.setStatus(route.getStatus());
            dto.setStopCount(routeStopRepository.countByRouteId(route.getRouteId()));
            dto.setScheduledTrips(tripRepository.countByRouteId(route.getRouteId()));

            Map<String, Object> perf = performance.get(route.getRouteId());
            if (perf != null) {
                dto.setAvgDelayMin(asDouble(perf.get("avg_delay_min")));
                dto.setMaxDelayMin(asDouble(perf.get("max_delay_min")));
                dto.setOnTimePercentage(asDouble(perf.get("on_time_percentage")));
                Double delayed = asDouble(perf.get("trips_delayed"));
                dto.setDelayedTrips(delayed == null ? null : delayed.longValue());
                Object band = perf.get("performance_band");
                dto.setPerformanceBand(band == null ? null : band.toString());
            }
            out.add(dto);
        }
        return out;
    }

    /** Engine route metrics keyed by route id; empty if the engine has not run. */
    public Map<String, Map<String, Object>> performanceByRoute() {
        try {
            return engineOutput.rows("route_performance", "routes").stream()
                    .filter(r -> r.get("route_id") != null)
                    .collect(Collectors.toMap(
                            r -> r.get("route_id").toString(), r -> r, (a, b) -> a));
        } catch (RuntimeException ex) {
            // The engine has not produced results yet. Routes are still useful
            // without performance figures, so this is not fatal.
            return Map.of();
        }
    }

    static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
