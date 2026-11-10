package com.transitpulse.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for updating one analytical parameter from the Settings page. */
public class ConfigurationRequest {

    @NotBlank(message = "configValue is required")
    private String configValue;

    public ConfigurationRequest() {
    }

    public ConfigurationRequest(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }
}
