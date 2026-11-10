package com.transitpulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Payload for creating or updating an operator disruption annotation. */
public class IncidentRequest {

    @NotBlank(message = "incidentType is required")
    @Pattern(regexp = "SIGNAL_FAILURE|ROAD_WORKS|TRAFFIC_CONGESTION|VEHICLE_BREAKDOWN"
            + "|DIVERSION|WEATHER|SPECIAL_EVENT|OTHER",
            message = "incidentType must be one of SIGNAL_FAILURE, ROAD_WORKS, "
                    + "TRAFFIC_CONGESTION, VEHICLE_BREAKDOWN, DIVERSION, WEATHER, "
                    + "SPECIAL_EVENT, OTHER")
    private String incidentType;

    @NotBlank(message = "routeId is required")
    private String routeId;

    private String stopId;

    @NotBlank(message = "startTime is required")
    private String startTime;

    private String endTime;

    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
            message = "severity must be LOW, MEDIUM, HIGH or CRITICAL")
    private String severity = "MEDIUM";

    @Size(max = 1024, message = "description must be at most 1024 characters")
    private String description;

    @Pattern(regexp = "Y|N", message = "isDiversion must be Y or N")
    private String isDiversion = "N";

    @Pattern(regexp = "OPEN|MONITORING|CLOSED",
            message = "status must be OPEN, MONITORING or CLOSED")
    private String status = "OPEN";

    private String reportedBy;

    /** Diversion detail: the planned stop that was not served. */
    private String skippedStopId;
    /** Diversion detail: the stop served instead. */
    private String replacementStopId;

    public IncidentRequest() {
    }

    public LocalDateTime parsedStartTime() {
        return LocalDateTime.parse(startTime);
    }

    public LocalDateTime parsedEndTime() {
        return (endTime == null || endTime.isBlank()) ? null : LocalDateTime.parse(endTime);
    }

    public String getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(String incidentType) {
        this.incidentType = incidentType;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIsDiversion() {
        return isDiversion;
    }

    public void setIsDiversion(String isDiversion) {
        this.isDiversion = isDiversion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public String getSkippedStopId() {
        return skippedStopId;
    }

    public void setSkippedStopId(String skippedStopId) {
        this.skippedStopId = skippedStopId;
    }

    public String getReplacementStopId() {
        return replacementStopId;
    }

    public void setReplacementStopId(String replacementStopId) {
        this.replacementStopId = replacementStopId;
    }
}
