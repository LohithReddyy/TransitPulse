package com.transitpulse;

import com.transitpulse.dto.StopDelayDto;
import com.transitpulse.dto.TripInvestigationDto;
import com.transitpulse.repository.TripRepository;
import com.transitpulse.service.DelayAnalysisService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delay analysis against the loaded synthetic dataset.
 *
 * <p>Tests skip rather than fail when datasets have not been generated, so a
 * fresh checkout does not report false failures.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DelayAnalysisServiceTest {

    @Autowired
    private DelayAnalysisService delayAnalysis;

    @Autowired
    private TripRepository tripRepository;

    private String anyTripId() {
        var trips = tripRepository.findAll();
        Assumptions.assumeFalse(trips.isEmpty(),
                "datasets not loaded; run generate_datasets.py");
        return trips.get(0).getTripId();
    }

    @Test
    @DisplayName("delay equals actual arrival minus scheduled arrival")
    void delayArithmetic() {
        String tripId = anyTripId();
        List<StopDelayDto> delays = delayAnalysis.stopDelays(tripId);
        assertThat(delays).isNotEmpty();
        for (StopDelayDto d : delays) {
            if (d.getDelayMin() != null && d.getActualArrival() != null) {
                double expected = DelayAnalysisService.minutesBetween(
                        d.getScheduledArrival(), d.getActualArrival());
                assertThat(d.getDelayMin()).isCloseTo(expected,
                        org.assertj.core.data.Offset.offset(0.02));
            }
        }
    }

    @Test
    @DisplayName("stops with no observation report a null delay and MISSING class")
    void missingObservationsAreNotZero() {
        var trips = tripRepository.findAll();
        Assumptions.assumeFalse(trips.isEmpty(), "datasets not loaded");
        boolean sawMissing = false;
        for (var trip : trips) {
            for (StopDelayDto d : delayAnalysis.stopDelays(trip.getTripId())) {
                if (!d.isObserved()) {
                    sawMissing = true;
                    assertThat(d.getDelayMin())
                            .as("an unobserved stop must have no delay value")
                            .isNull();
                    assertThat(d.getDelayClass()).isEqualTo("MISSING");
                    assertThat(d.getNote()).isNotBlank();
                }
            }
            if (sawMissing) {
                break;
            }
        }
        Assumptions.assumeTrue(sawMissing, "dataset contains no missing observations");
    }

    @Test
    @DisplayName("stop delays are returned in schedule order")
    void delaysAreOrdered() {
        List<StopDelayDto> delays = delayAnalysis.stopDelays(anyTripId());
        for (int i = 1; i < delays.size(); i++) {
            assertThat(delays.get(i).getStopSequence())
                    .isGreaterThan(delays.get(i - 1).getStopSequence());
        }
    }

    @Test
    @DisplayName("investigation reports consistent counts and an explanation")
    void investigationIsConsistent() {
        TripInvestigationDto dto = delayAnalysis.investigate(anyTripId());
        assertThat(dto.getStopsObserved() + dto.getStopsMissing())
                .isEqualTo(dto.getStopsTotal());
        assertThat(dto.getDataConfidence()).isIn("HIGH", "MEDIUM", "LOW");
        assertThat(dto.getExplanation()).isNotBlank();
        assertThat(dto.getStopDelays()).hasSize(dto.getStopsTotal());
    }

    @Test
    @DisplayName("a recovered trip must have been delayed first")
    void recoveryRequiresAnEarlierDelay() {
        var trips = tripRepository.findAll();
        Assumptions.assumeFalse(trips.isEmpty(), "datasets not loaded");
        for (var trip : trips) {
            TripInvestigationDto dto = delayAnalysis.investigate(trip.getTripId());
            if (dto.isRecovered()) {
                assertThat(dto.getMaxDelayMin()).isGreaterThanOrEqualTo(2.0);
                assertThat(dto.getFinalDelayMin()).isLessThan(2.0);
                assertThat(dto.getRecoveryMin()).isGreaterThan(0.0);
            }
        }
    }

    @Test
    @DisplayName("vehicle chain lists the trips worked by the same vehicle")
    void vehicleChain() {
        var result = delayAnalysis.vehicleChain(anyTripId());
        assertThat(result).containsKeys("tripId", "vehicleId");
    }
}
