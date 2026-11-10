package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.entity.TransferConnection;
import com.transitpulse.entity.Vehicle;
import com.transitpulse.repository.TransferConnectionRepository;
import com.transitpulse.repository.VehicleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Vehicles, transfer connections and a health endpoint for the UI. */
@RestController
@RequestMapping("/api")
@Tag(name = "Reference data", description = "Vehicles, connections and health")
public class ReferenceDataController {

    private final VehicleRepository vehicleRepository;
    private final TransferConnectionRepository connectionRepository;

    public ReferenceDataController(VehicleRepository vehicleRepository,
                                   TransferConnectionRepository connectionRepository) {
        this.vehicleRepository = vehicleRepository;
        this.connectionRepository = connectionRepository;
    }

    @GetMapping("/vehicles")
    @Operation(summary = "List fleet vehicles")
    public ResponseEntity<ApiResponse<List<Vehicle>>> vehicles() {
        return ResponseEntity.ok(ApiResponse.ok(vehicleRepository.findAll()));
    }

    @GetMapping("/connections")
    @Operation(summary = "List timetabled transfer opportunities")
    public ResponseEntity<ApiResponse<List<TransferConnection>>> connections(
            @RequestParam(required = false) String interchangeStopId,
            @RequestParam(defaultValue = "false") boolean structurallyTightOnly) {
        List<TransferConnection> items;
        if (structurallyTightOnly) {
            items = connectionRepository.findStructurallyTight();
        } else if (interchangeStopId != null && !interchangeStopId.isBlank()) {
            items = connectionRepository.findByInterchangeStopId(interchangeStopId);
        } else {
            items = connectionRepository.findAll();
        }
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @GetMapping("/health")
    @Operation(summary = "Simple health check for the UI")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "status", "UP",
                "application", "TransitPulse",
                "version", "1.0.0")));
    }
}
