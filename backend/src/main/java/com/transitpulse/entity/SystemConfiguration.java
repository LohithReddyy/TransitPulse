package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An analytical parameter, editable from the Settings page.
 *
 * <p>{@link #minValue} and {@link #maxValue} bound what the API will accept,
 * so the UI cannot push the engine into a meaningless configuration.
 */
@Entity
@Table(name = "system_configuration")
public class SystemConfiguration {

    @Id
    @Column(name = "config_key", length = 64, nullable = false)
    private String configKey;

    @Column(name = "config_value", length = 256, nullable = false)
    private String configValue;

    @Column(name = "value_type", length = 16, nullable = false)
    private String valueType = "NUMBER";

    @Column(name = "category", length = 32, nullable = false)
    private String category = "GENERAL";

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "min_value", precision = 12, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 12, scale = 4)
    private BigDecimal maxValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SystemConfiguration() {
    }

    public SystemConfiguration(String configKey, String configValue, String valueType,
                               String category, String description,
                               BigDecimal minValue, BigDecimal maxValue) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.valueType = valueType;
        this.category = category;
        this.description = description;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public void setMinValue(BigDecimal minValue) {
        this.minValue = minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(BigDecimal maxValue) {
        this.maxValue = maxValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
