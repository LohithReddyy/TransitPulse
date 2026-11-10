package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.dto.StopDelayDto;
import com.transitpulse.dto.TripInvestigationDto;
import com.transitpulse.entity.ActualMovement;
import com.transitpulse.entity.ScheduledMovement;
import com.transitpulse.entity.Trip;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.ActualMovementRepository;
import com.transitpulse.repository.ScheduledMovementRepository;
import com.transitpulse.repository.TripRepository;
import com.transitpulse.service.DelayAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@Tag(name = "Trips", description = "Trips, movements and delay investigation")
public class TripController {

    private final TripRepository tripRepository;
    private final ScheduledMovementRepository scheduledRepository;
    private final ActualMovementRepository actualRepository;
    private final DelayAnalysisService delayAnalysis;

    public TripController(TripRepository tripRepository,
                          ScheduledMovementRepository scheduledRepository,
                          ActualMovementRepository actualRepository,
                          DelayAnalysisService delayAnalysis) {
        this.tripRepository = tripRepository;
        this.scheduledRepository = scheduledRepository;
        this.actualRepository = actualRepository;
        this.delayAnalysis = delayAnalysis;
    }

    @GetMapping
    @Operation(summary = "List trips, optionally filtered by route")
    public ResponseEntity<ApiResponse<List<Trip>>> list(
            @RequestParam(required = false) String routeId) {
        List<Trip> trips = (routeId == null || routeId.isBlank())
                ? tripRepository.findAll()
                : tripRepository.findByRouteIdOrderByScheduledStartTimeAsc(routeId);
        return ResponseEntity.ok(ApiResponse.ok(trips));
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "Get one trip")
    public ResponseEntity<ApiResponse<Trip>> get(@PathVariable String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        return ResponseEntity.ok(ApiResponse.ok(trip));
    }

    @GetMapping("/{tripId}/scheduled-movements")
    @Operation(summary = "Planned arrivals and departures of a trip")
    public ResponseEntity<ApiResponse<List<ScheduledMovement>>> scheduled(
            @PathVariable String tripId) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduledRepository.findByTripIdOrderByStopSequenceAsc(tripId)));
    }

    @GetMapping("/{tripId}/actual-movements")
    @Operation(summary = "Observed arrivals of a trip; a null arrival means no GPS event")
    public ResponseEntity<ApiResponse<List<ActualMovement>>> actual(
            @PathVariable String tripId) {
        return ResponseEntity.ok(ApiResponse.ok(
                actualRepository.findByTripIdOrderByStopSequenceAsc(tripId)));
    }

    @GetMapping("/{tripId}/delays")
    @Operation(summary = "Delay at each stop; MISSING where nothing was observed")
    public ResponseEntity<ApiResponse<List<StopDelayDto>>> delays(
            @PathVariable String tripId) {
        return ResponseEntity.ok(ApiResponse.ok(delayAnalysis.stopDelays(tripId)));
    }

    @GetMapping("/{tripId}/investigation")
    @Operation(summary = "Full delay investigation: delays, propagation, "
            + "connections, incidents and an explanation")
    public ResponseEntity<ApiResponse<TripInvestigationDto>> investigate(
            @PathVariable String tripId) {
        return ResponseEntity.ok(ApiResponse.ok(delayAnalysis.investigate(tripId)));
    }

    @GetMapping("/{tripId}/vehicle-chain")
    @Operation(summary = "The other trips worked by this trip's vehicle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vehicleChain(
            @PathVariable String tripId) {
        return ResponseEntity.ok(ApiResponse.ok(delayAnalysis.vehicleChain(tripId)));
    }
}
