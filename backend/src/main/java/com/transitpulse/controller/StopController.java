package com.transitpulse.controller;

import com.transitpulse.dto.ApiResponse;
import com.transitpulse.dto.StopSummaryDto;
import com.transitpulse.entity.PassengerVolume;
import com.transitpulse.entity.Stop;
import com.transitpulse.repository.PassengerVolumeRepository;
import com.transitpulse.service.StopService;
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
@RequestMapping("/api/stops")
@Tag(name = "Stops", description = "Stops, passenger volume and delay behaviour")
public class StopController {

    private final StopService stopService;
    private final PassengerVolumeRepository passengerVolumeRepository;

    public StopController(StopService stopService,
                          PassengerVolumeRepository passengerVolumeRepository) {
        this.stopService = stopService;
        this.passengerVolumeRepository = passengerVolumeRepository;
    }

    @GetMapping
    @Operation(summary = "List stops; low-volume stops are marked LOW confidence")
    public ResponseEntity<ApiResponse<List<StopSummaryDto>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean interchangeOnly,
            @RequestParam(defaultValue = "false") boolean lowVolumeOnly) {
        return ResponseEntity.ok(ApiResponse.ok(
                stopService.summaries(search, interchangeOnly, lowVolumeOnly)));
    }

    @GetMapping("/{stopId}")
    @Operation(summary = "Get one stop")
    public ResponseEntity<ApiResponse<Stop>> get(@PathVariable String stopId) {
        return ResponseEntity.ok(ApiResponse.ok(stopService.findById(stopId)));
    }

    @GetMapping("/{stopId}/passenger-volumes")
    @Operation(summary = "Get a stop's passenger volume profile by time band")
    public ResponseEntity<ApiResponse<List<PassengerVolume>>> volumes(
            @PathVariable String stopId) {
        stopService.findById(stopId);
        return ResponseEntity.ok(ApiResponse.ok(
                passengerVolumeRepository.findByStopId(stopId)));
    }
}
