package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Dashboard, propagation, impact and sensitivity")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard KPIs")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.dashboard()));
    }

    @GetMapping("/route-performance")
    @Operation(summary = "Per-route performance metrics")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> routePerformance() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.routePerformance()));
    }

    @GetMapping("/stop-performance")
    @Operation(summary = "Per-stop delay frequency")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> stopPerformance() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.stopPerformance()));
    }

    @GetMapping("/trip-summary")
    @Operation(summary = "Per-trip delay summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tripSummary() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.tripSummary()));
    }

    @GetMapping("/delay-distribution")
    @Operation(summary = "Distribution of delay classes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delayDistribution() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.delayDistribution()));
    }

    @GetMapping("/propagation")
    @Operation(summary = "Delay propagation links with evidence and confidence")
    public ResponseEntity<ApiResponse<Map<String, Object>>> propagation(
            @RequestParam(required = false) String mechanism,
            @RequestParam(required = false) String confidence,
            @RequestParam(required = false, defaultValue = "200") Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                analytics.propagation(mechanism, confidence, limit)));
    }

    @GetMapping("/connection-risk")
    @Operation(summary = "Connection risk assessment per transfer opportunity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> connectionRisk(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "200") Integer limit) {
        return ResponseEntity.ok(ApiResponse.ok(analytics.connectionRisk(status, limit)));
    }

    @GetMapping("/passenger-impact")
    @Operation(summary = "Estimated passenger impact by route and stop")
    public ResponseEntity<ApiResponse<Map<String, Object>>> passengerImpact() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.passengerImpact()));
    }

    @GetMapping("/sensitivity")
    @Operation(summary = "Sensitivity of connection risk to the transfer buffer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sensitivity() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.sensitivity()));
    }

    @GetMapping("/recovery")
    @Operation(summary = "Delay recovery analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recovery() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.recovery()));
    }

    @GetMapping("/worked-example")
    @Operation(summary = "The hand-worked propagation example, step by step")
    public ResponseEntity<ApiResponse<Map<String, Object>>> workedExample() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.workedExample()));
    }

    @GetMapping("/validation")
    @Operation(summary = "Data quality and validation report from ingestion")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validation() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.validation()));
    }

    @GetMapping("/engine-status")
    @Operation(summary = "Whether the Python engine has produced analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> engineStatus() {
        return ResponseEntity.ok(ApiResponse.ok(analytics.engineStatus()));
    }
}
