package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/** The planned arrival and departure of a trip at one stop. */
@Entity
@Table(name = "scheduled_movements")
public class ScheduledMovement {

    @Id
    @Column(name = "scheduled_movement_id", length = 32, nullable = false)
    private String scheduledMovementId;

    @Column(name = "trip_id", length = 16, nullable = false)
    private String tripId;

    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @Column(name = "stop_id", length = 16, nullable = false)
    private String stopId;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "scheduled_arrival", nullable = false)
    private LocalDateTime scheduledArrival;

    @Column(name = "scheduled_departure", nullable = false)
    private LocalDateTime scheduledDeparture;

    @JdbcTypeCode(Types.TINYINT)
    @Column(name = "is_origin", nullable = false)
    private Integer isOrigin = 0;

    @JdbcTypeCode(Types.TINYINT)
    @Column(name = "is_destination", nullable = false)
    private Integer isDestination = 0;

    public ScheduledMovement() {
    }

    public String getScheduledMovementId() {
        return scheduledMovementId;
    }

    public void setScheduledMovementId(String scheduledMovementId) {
        this.scheduledMovementId = scheduledMovementId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
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

    public Integer getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(Integer stopSequence) {
        this.stopSequence = stopSequence;
    }

    public LocalDateTime getScheduledArrival() {
        return scheduledArrival;
    }

    public void setScheduledArrival(LocalDateTime scheduledArrival) {
        this.scheduledArrival = scheduledArrival;
    }

    public LocalDateTime getScheduledDeparture() {
        return scheduledDeparture;
    }

    public void setScheduledDeparture(LocalDateTime scheduledDeparture) {
        this.scheduledDeparture = scheduledDeparture;
    }

    public Integer getIsOrigin() {
        return isOrigin;
    }

    public void setIsOrigin(Integer isOrigin) {
        this.isOrigin = isOrigin;
    }

    public Integer getIsDestination() {
        return isDestination;
    }

    public void setIsDestination(Integer isDestination) {
        this.isDestination = isDestination;
    }
}
