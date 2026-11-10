package com.transitpulse.service;

import com.transitpulse.entity.SystemConfiguration;
import com.transitpulse.exception.InvalidRequestException;
import com.transitpulse.exception.ResourceNotFoundException;
import com.transitpulse.repository.SystemConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytical parameters, read and written by the Settings page.
 *
 * <p>Updates are bounded by each parameter's min/max and, for the delay
 * thresholds, checked for mutual consistency: an ordering like
 * {@code on_time > minor} would make classification meaningless, so it is
 * rejected rather than stored.
 */
@Service
@Transactional(readOnly = true)
public class ConfigurationService {

    public static final String ON_TIME = "on_time_threshold_min";
    public static final String MINOR = "minor_delay_threshold_min";
    public static final String MODERATE = "moderate_delay_threshold_min";
    public static final String EARLY = "early_arrival_threshold_min";
    public static final String TURNAROUND = "vehicle_turnaround_min";
    public static final String PROPAGATION_WINDOW = "propagation_window_min";
    public static final String TRANSFER_BUFFER = "required_transfer_buffer_min";
    public static final String RECOVERY_REDUCTION = "recovery_min_reduction_min";
    public static final String LOW_VOLUME = "low_volume_stop_threshold";
    public static final String MISSING_GPS_STRATEGY = "missing_gps_strategy";

    private final SystemConfigurationRepository repository;

    public ConfigurationService(SystemConfigurationRepository repository) {
        this.repository = repository;
    }

    public List<SystemConfiguration> findAll() {
        return repository.findAllByOrderByCategoryAscConfigKeyAsc();
    }

    public List<SystemConfiguration> findByCategory(String category) {
        return repository.findByCategoryOrderByConfigKeyAsc(category);
    }

    public SystemConfiguration findByKey(String key) {
        return repository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Configuration key", key));
    }

    /** Settings grouped by category, which is how the Settings page renders them. */
    public Map<String, List<SystemConfiguration>> grouped() {
        Map<String, List<SystemConfiguration>> out = new LinkedHashMap<>();
        for (SystemConfiguration cfg : findAll()) {
            out.computeIfAbsent(cfg.getCategory(), k -> new java.util.ArrayList<>())
               .add(cfg);
        }
        return out;
    }

    @Transactional
    public SystemConfiguration update(String key, String rawValue) {
        SystemConfiguration cfg = findByKey(key);
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw new InvalidRequestException("Value for '" + key + "' cannot be empty");
        }

        switch (cfg.getValueType()) {
            case "NUMBER" -> validateNumber(cfg, value);
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new InvalidRequestException(
                            "Value for '" + key + "' must be true or false");
                }
            }
            case "LIST" -> validateList(key, value);
            default -> validateString(key, value);
        }

        cfg.setConfigValue(value);
        SystemConfiguration saved = repository.save(cfg);
        validateThresholdOrdering();
        return saved;
    }

    private void validateNumber(SystemConfiguration cfg, String value) {
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(
                    "Value for '" + cfg.getConfigKey() + "' must be a number, got: " + value);
        }
        if (cfg.getMinValue() != null && parsed.compareTo(cfg.getMinValue()) < 0) {
            throw new InvalidRequestException("Value for '" + cfg.getConfigKey()
                    + "' must be at least " + cfg.getMinValue());
        }
        if (cfg.getMaxValue() != null && parsed.compareTo(cfg.getMaxValue()) > 0) {
            throw new InvalidRequestException("Value for '" + cfg.getConfigKey()
                    + "' must be at most " + cfg.getMaxValue());
        }
    }

    private void validateList(String key, String value) {
        String[] parts = value.split(",");
        if (parts.length == 0) {
            throw new InvalidRequestException("Value for '" + key + "' must be a "
                    + "comma separated list, for example 3,5,8,10");
        }
        for (String part : parts) {
            try {
                Double.parseDouble(part.trim());
            } catch (NumberFormatException e) {
                throw new InvalidRequestException("Value for '" + key + "' must contain "
                        + "only numbers separated by commas, got: " + part.trim());
            }
        }
    }

    private void validateString(String key, String value) {
        if (MISSING_GPS_STRATEGY.equals(key)
                && !List.of("EXCLUDE", "ESTIMATE").contains(value.toUpperCase())) {
            throw new InvalidRequestException(
                    "missing_gps_strategy must be EXCLUDE or ESTIMATE. Missing GPS "
                            + "events are never treated as zero delay.");
        }
    }

    /** Delay thresholds must stay strictly increasing to remain meaningful. */
    private void validateThresholdOrdering() {
        double onTime = onTimeThresholdMin();
        double minor = numeric(MINOR, 5.0);
        double moderate = numeric(MODERATE, 15.0);
        if (!(onTime < minor && minor < moderate)) {
            throw new InvalidRequestException(String.format(
                    "Delay thresholds must be strictly increasing, but would become "
                            + "on_time=%.1f, minor=%.1f, moderate=%.1f. Change rejected.",
                    onTime, minor, moderate));
        }
    }

    // ---------------- typed accessors used by the services ----------------

    public double numeric(String key, double fallback) {
        return repository.findById(key)
                .map(c -> {
                    try {
                        return Double.parseDouble(c.getConfigValue());
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    public String text(String key, String fallback) {
        return repository.findById(key)
                .map(SystemConfiguration::getConfigValue)
                .orElse(fallback);
    }

    public double onTimeThresholdMin() {
        return numeric(ON_TIME, 2.0);
    }

    public double vehicleTurnaroundMin() {
        return numeric(TURNAROUND, 5.0);
    }

    public double propagationWindowMin() {
        return numeric(PROPAGATION_WINDOW, 90.0);
    }

    public double requiredTransferBufferMin() {
        return numeric(TRANSFER_BUFFER, 5.0);
    }

    public double recoveryMinReductionMin() {
        return numeric(RECOVERY_REDUCTION, 1.0);
    }

    public int lowVolumeStopThreshold() {
        return (int) numeric(LOW_VOLUME, 20.0);
    }

    /**
     * Classify a delay using the configured thresholds. Mirrors
     * {@code EngineConfig.classify_delay} in the Python engine exactly.
     */
    public String classifyDelay(Double delayMin) {
        if (delayMin == null) {
            return "MISSING";
        }
        if (delayMin <= numeric(EARLY, -2.0)) {
            return "EARLY";
        }
        if (delayMin < onTimeThresholdMin()) {
            return "ON_TIME";
        }
        if (delayMin < numeric(MINOR, 5.0)) {
            return "MINOR";
        }
        if (delayMin < numeric(MODERATE, 15.0)) {
            return "MODERATE";
        }
        return "SEVERE";
    }
}
