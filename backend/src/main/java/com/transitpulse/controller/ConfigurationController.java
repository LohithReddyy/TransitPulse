package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.dto.ConfigurationRequest;
import com.transitpulse.entity.SystemConfiguration;
import com.transitpulse.service.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@Tag(name = "Configuration", description = "Analytical parameters for the Settings page")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    public ConfigurationController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    @Operation(summary = "List all analytical parameters")
    public ResponseEntity<ApiResponse<List<SystemConfiguration>>> list(
            @RequestParam(required = false) String category) {
        List<SystemConfiguration> items = (category == null || category.isBlank())
                ? configurationService.findAll()
                : configurationService.findByCategory(category);
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @GetMapping("/grouped")
    @Operation(summary = "Parameters grouped by category")
    public ResponseEntity<ApiResponse<Map<String, List<SystemConfiguration>>>> grouped() {
        return ResponseEntity.ok(ApiResponse.ok(configurationService.grouped()));
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get one parameter")
    public ResponseEntity<ApiResponse<SystemConfiguration>> get(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.ok(configurationService.findByKey(key)));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update one parameter; bounds and ordering are enforced")
    public ResponseEntity<ApiResponse<SystemConfiguration>> update(
            @PathVariable String key,
            @Valid @RequestBody ConfigurationRequest request) {
        SystemConfiguration updated =
                configurationService.update(key, request.getConfigValue());
        return ResponseEntity.ok(ApiResponse.ok(updated,
                "Updated " + key + ". Re-run the data engine to apply it to analytics."));
    }
}
