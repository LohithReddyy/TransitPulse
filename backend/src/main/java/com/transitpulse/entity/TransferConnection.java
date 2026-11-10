package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A timetabled passenger transfer opportunity at an interchange.
 *
 * <p>Named {@code TransferConnection} rather than {@code Connection} to avoid
 * colliding with {@code java.sql.Connection} in this codebase.
 */
@Entity
@Table(name = "connections")
public class TransferConnection {

    @Id
    @Column(name = "connection_id", length = 16, nullable = false)
    private String connectionId;

    @Column(name = "interchange_stop_id", length = 16, nullable = false)
    private String interchangeStopId;

    @Column(name = "feeder_trip_id", length = 16, nullable = false)
    private String feederTripId;

    @Column(name = "feeder_route_id", length = 16, nullable = false)
    private String feederRouteId;

    @Column(name = "connecting_trip_id", length = 16, nullable = false)
    private String connectingTripId;

    @Column(name = "connecting_route_id", length = 16, nullable = false)
    private String connectingRouteId;

    @Column(name = "feeder_scheduled_arrival", nullable = false)
    private LocalDateTime feederScheduledArrival;

    @Column(name = "connecting_scheduled_departure", nullable = false)
    private LocalDateTime connectingScheduledDeparture;

    @Column(name = "planned_transfer_min", precision = 6, scale = 2, nullable = false)
    private BigDecimal plannedTransferMin;

    @Column(name = "required_buffer_min", precision = 6, scale = 2, nullable = false)
    private BigDecimal requiredBufferMin = new BigDecimal("5.00");

    @Column(name = "typical_transferring_passengers", nullable = false)
    private Integer typicalTransferringPassengers = 0;

    public TransferConnection() {
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getInterchangeStopId() {
        return interchangeStopId;
    }

    public void setInterchangeStopId(String interchangeStopId) {
        this.interchangeStopId = interchangeStopId;
    }

    public String getFeederTripId() {
        return feederTripId;
    }

    public void setFeederTripId(String feederTripId) {
        this.feederTripId = feederTripId;
    }

    public String getFeederRouteId() {
        return feederRouteId;
    }

    public void setFeederRouteId(String feederRouteId) {
        this.feederRouteId = feederRouteId;
    }

    public String getConnectingTripId() {
        return connectingTripId;
    }

    public void setConnectingTripId(String connectingTripId) {
        this.connectingTripId = connectingTripId;
    }

    public String getConnectingRouteId() {
        return connectingRouteId;
    }

    public void setConnectingRouteId(String connectingRouteId) {
        this.connectingRouteId = connectingRouteId;
    }

    public LocalDateTime getFeederScheduledArrival() {
        return feederScheduledArrival;
    }

    public void setFeederScheduledArrival(LocalDateTime feederScheduledArrival) {
        this.feederScheduledArrival = feederScheduledArrival;
    }

    public LocalDateTime getConnectingScheduledDeparture() {
        return connectingScheduledDeparture;
    }

    public void setConnectingScheduledDeparture(LocalDateTime connectingScheduledDeparture) {
        this.connectingScheduledDeparture = connectingScheduledDeparture;
    }

    public BigDecimal getPlannedTransferMin() {
        return plannedTransferMin;
    }

    public void setPlannedTransferMin(BigDecimal plannedTransferMin) {
        this.plannedTransferMin = plannedTransferMin;
    }

    public BigDecimal getRequiredBufferMin() {
        return requiredBufferMin;
    }

    public void setRequiredBufferMin(BigDecimal requiredBufferMin) {
        this.requiredBufferMin = requiredBufferMin;
    }

    public Integer getTypicalTransferringPassengers() {
        return typicalTransferringPassengers;
    }

    public void setTypicalTransferringPassengers(Integer typicalTransferringPassengers) {
        this.typicalTransferringPassengers = typicalTransferringPassengers;
    }
}
