package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.dto.RouteSummaryDto;
import com.transitpulse.entity.Route;
import com.transitpulse.entity.RouteStop;
import com.transitpulse.entity.Trip;
import com.transitpulse.repository.TripRepository;
import com.transitpulse.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Routes", description = "Transit routes and their performance")
public class RouteController {

    private final RouteService routeService;
    private final TripRepository tripRepository;

    public RouteController(RouteService routeService, TripRepository tripRepository) {
        this.routeService = routeService;
        this.tripRepository = tripRepository;
    }

    @GetMapping
    @Operation(summary = "List routes with performance metrics")
    public ResponseEntity<ApiResponse<List<RouteSummaryDto>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String mode) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.summaries(search, mode)));
    }

    @GetMapping("/{routeId}")
    @Operation(summary = "Get one route")
    public ResponseEntity<ApiResponse<Route>> get(@PathVariable String routeId) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.findById(routeId)));
    }

    @GetMapping("/{routeId}/stops")
    @Operation(summary = "Get a route's ordered stop pattern")
    public ResponseEntity<ApiResponse<List<RouteStop>>> stops(@PathVariable String routeId) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.stopsOf(routeId)));
    }

    @GetMapping("/{routeId}/trips")
    @Operation(summary = "Get a route's trips in schedule order")
    public ResponseEntity<ApiResponse<List<Trip>>> trips(@PathVariable String routeId) {
        routeService.findById(routeId);
        return ResponseEntity.ok(ApiResponse.ok(
                tripRepository.findByRouteIdOrderByScheduledStartTimeAsc(routeId)));
    }
}
