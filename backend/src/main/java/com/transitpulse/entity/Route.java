package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/** A transit route: an ordered pattern of stops operated as one service. */
@Entity
@Table(name = "routes")
public class Route {

    @Id
    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @NotBlank
    @Column(name = "route_name", length = 128, nullable = false)
    private String routeName;

    @Column(name = "mode", length = 16, nullable = false)
    private String mode = "BUS";

    @Column(name = "origin_name", length = 128)
    private String originName;

    @Column(name = "destination_name", length = 128)
    private String destinationName;

    @PositiveOrZero
    @Column(name = "stop_count", nullable = false)
    private Integer stopCount = 0;

    @Column(name = "status", length = 16, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "description", length = 512)
    private String description;

    public Route() {
    }

    public Route(String routeId, String routeName, String mode) {
        this.routeId = routeId;
        this.routeName = routeName;
        this.mode = mode;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOriginName() {
        return originName;
    }

    public void setOriginName(String originName) {
        this.originName = originName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public Integer getStopCount() {
        return stopCount;
    }

    public void setStopCount(Integer stopCount) {
        this.stopCount = stopCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
