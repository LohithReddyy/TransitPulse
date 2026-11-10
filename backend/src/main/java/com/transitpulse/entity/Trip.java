package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/** One scheduled run of a route by a vehicle on a service date. */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @Column(name = "trip_id", length = 16, nullable = false)
    private String tripId;

    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @Column(name = "vehicle_id", length = 16)
    private String vehicleId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalDateTime scheduledStartTime;

    @JdbcTypeCode(Types.TINYINT)
    @Column(name = "direction", nullable = false)
    private Integer direction = 0;

    @Column(name = "service_pattern", length = 24, nullable = false)
    private String servicePattern = "WEEKDAY";

    /** Groups the consecutive trips worked by one vehicle. */
    @Column(name = "block_id", length = 32)
    private String blockId;

    @Column(name = "status", length = 24, nullable = false)
    private String status = "SCHEDULED";

    public Trip() {
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

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public LocalDateTime getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(LocalDateTime scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public Integer getDirection() {
        return direction;
    }

    public void setDirection(Integer direction) {
        this.direction = direction;
    }

    public String getServicePattern() {
        return servicePattern;
    }

    public void setServicePattern(String servicePattern) {
        this.servicePattern = servicePattern;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
