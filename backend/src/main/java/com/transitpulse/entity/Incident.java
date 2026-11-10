package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * An operator disruption annotation.
 *
 * <p>
 * Incidents are the human evidence that turns a statistical correlation
 * into a defensible causal claim, so the propagation engine consults them
 * when assigning confidence.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @Column(name = "incident_id", length = 16, nullable = false)
    private String incidentId;

    @NotBlank
    @Column(name = "incident_type", length = 32, nullable = false)
    private String incidentType;

    @NotBlank
    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @Column(name = "stop_id", length = 16)
    private String stopId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "severity", length = 16, nullable = false)
    private String severity = "MEDIUM";

    @Column(name = "description", length = 1024)
    private String description;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_diversion", columnDefinition = "CHAR(1)")
    private String isDiversion = "N";

    @Column(name = "status", length = 16, nullable = false)
    private String status = "OPEN";

    @Column(name = "reported_by", length = 64)
    private String reportedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Incident() {
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
