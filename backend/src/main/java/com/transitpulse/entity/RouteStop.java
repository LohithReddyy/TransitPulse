package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** One position in a route's ordered stop pattern. */
@Entity
@Table(name = "route_stops")
public class RouteStop {

    @Id
    @Column(name = "route_stop_id", length = 32, nullable = false)
    private String routeStopId;

    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @Column(name = "stop_id", length = 16, nullable = false)
    private String stopId;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "distance_from_origin_km", precision = 8, scale = 2)
    private BigDecimal distanceFromOriginKm = BigDecimal.ZERO;

    @Column(name = "scheduled_runtime_min")
    private Integer scheduledRuntimeMin = 0;

    public RouteStop() {
    }

    public String getRouteStopId() {
        return routeStopId;
    }

    public void setRouteStopId(String routeStopId) {
        this.routeStopId = routeStopId;
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

    public BigDecimal getDistanceFromOriginKm() {
        return distanceFromOriginKm;
    }

    public void setDistanceFromOriginKm(BigDecimal distanceFromOriginKm) {
        this.distanceFromOriginKm = distanceFromOriginKm;
    }

    public Integer getScheduledRuntimeMin() {
        return scheduledRuntimeMin;
    }

    public void setScheduledRuntimeMin(Integer scheduledRuntimeMin) {
        this.scheduledRuntimeMin = scheduledRuntimeMin;
    }
}
