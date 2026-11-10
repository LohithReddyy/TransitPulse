package com.transitpulse;

import com.transitpulse.entity.SystemConfiguration;
import com.transitpulse.exception.InvalidRequestException;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.SystemConfigurationRepository;
import com.transitpulse.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Delay classification and the guard rails on configuration updates. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ConfigurationServiceTest {

    @Autowired
    private ConfigurationService service;

    @Autowired
    private SystemConfigurationRepository repository;

    @BeforeEach
    void ensureSeeded() {
        if (repository.count() == 0) {
            repository.save(new SystemConfiguration("on_time_threshold_min", "2.0",
                    "NUMBER", "DELAY", "", BigDecimal.ZERO, new BigDecimal("15")));
            repository.save(new SystemConfiguration("minor_delay_threshold_min", "5.0",
                    "NUMBER", "DELAY", "", BigDecimal.ONE, new BigDecimal("30")));
            repository.save(new SystemConfiguration("moderate_delay_threshold_min", "15.0",
                    "NUMBER", "DELAY", "", new BigDecimal("5"), new BigDecimal("60")));
        }
    }

    @Test
    @DisplayName("delay classification matches the Python engine's boundaries")
    void classifyDelay() {
        assertThat(service.classifyDelay(null)).isEqualTo("MISSING");
        assertThat(service.classifyDelay(-5.0)).isEqualTo("EARLY");
        assertThat(service.classifyDelay(0.0)).isEqualTo("ON_TIME");
        assertThat(service.classifyDelay(1.9)).isEqualTo("ON_TIME");
        assertThat(service.classifyDelay(2.0)).isEqualTo("MINOR");
        assertThat(service.classifyDelay(4.9)).isEqualTo("MINOR");
        assertThat(service.classifyDelay(5.0)).isEqualTo("MODERATE");
        assertThat(service.classifyDelay(14.9)).isEqualTo("MODERATE");
        assertThat(service.classifyDelay(15.0)).isEqualTo("SEVERE");
    }

    @Test
    @DisplayName("a null delay is never classified as on time")
    void nullDelayIsMissingNotOnTime() {
        assertThat(service.classifyDelay(null)).isNotEqualTo("ON_TIME");
    }

    @Test
    @DisplayName("values outside a parameter's bounds are rejected")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> service.update("on_time_threshold_min", "999"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    @DisplayName("non-numeric values are rejected for numeric parameters")
    void rejectsNonNumeric() {
        assertThatThrownBy(() -> service.update("on_time_threshold_min", "soon"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must be a number");
    }

    @Test
    @DisplayName("an update that would invert the delay thresholds is rejected")
    void rejectsInvertedThresholds() {
        assertThatThrownBy(() -> service.update("on_time_threshold_min", "9"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("strictly increasing");
    }

    @Test
    @DisplayName("a valid update is persisted")
    void acceptsValidUpdate() {
        SystemConfiguration updated = service.update("on_time_threshold_min", "3.0");
        assertThat(updated.getConfigValue()).isEqualTo("3.0");
        assertThat(service.onTimeThresholdMin()).isEqualTo(3.0);
        assertThat(service.classifyDelay(2.5)).isEqualTo("ON_TIME");
    }

    @Test
    @DisplayName("missing_gps_strategy only accepts EXCLUDE or ESTIMATE")
    void rejectsBadMissingGpsStrategy() {
        if (repository.existsById("missing_gps_strategy")) {
            assertThatThrownBy(
                    () -> service.update("missing_gps_strategy", "ASSUME_ZERO"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("EXCLUDE or ESTIMATE");
        }
    }

    @Test
    @DisplayName("an unknown key yields a not-found error")
    void unknownKeyNotFound() {
        assertThatThrownBy(() -> service.findByKey("no_such_key"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
