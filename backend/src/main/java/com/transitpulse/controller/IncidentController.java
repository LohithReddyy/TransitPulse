package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.dto.IncidentRequest;
import com.transitpulse.entity.Incident;
import com.transitpulse.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Operator disruption annotations and diversions")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    @Operation(summary = "List incidents, optionally filtered by route or status")
    public ResponseEntity<ApiResponse<List<Incident>>> list(
            @RequestParam(required = false) String routeId,
            @RequestParam(required = false) String status) {
        List<Incident> incidents;
        if (routeId != null && !routeId.isBlank()) {
            incidents = incidentService.findByRoute(routeId);
        } else if (status != null && !status.isBlank()) {
            incidents = incidentService.findByStatus(status);
        } else {
            incidents = incidentService.findAll();
        }
        return ResponseEntity.ok(ApiResponse.ok(incidents));
    }

    @GetMapping("/diversions")
    @Operation(summary = "List diversion incidents")
    public ResponseEntity<ApiResponse<List<Incident>>> diversions() {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.findDiversions()));
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Get one incident")
    public ResponseEntity<ApiResponse<Incident>> get(@PathVariable String incidentId) {
        return ResponseEntity.ok(ApiResponse.ok(incidentService.findById(incidentId)));
    }

    @PostMapping
    @Operation(summary = "Create a disruption annotation")
    public ResponseEntity<ApiResponse<Incident>> create(
            @Valid @RequestBody IncidentRequest request) {
        Incident created = incidentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Incident created"));
    }

    @PutMapping("/{incidentId}")
    @Operation(summary = "Update a disruption annotation")
    public ResponseEntity<ApiResponse<Incident>> update(
            @PathVariable String incidentId,
            @Valid @RequestBody IncidentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                incidentService.update(incidentId, request), "Incident updated"));
    }

    @DeleteMapping("/{incidentId}")
    @Operation(summary = "Delete a disruption annotation")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String incidentId) {
        incidentService.delete(incidentId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Incident deleted"));
    }
}
