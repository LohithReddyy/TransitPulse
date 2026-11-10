package com.transitpulse.dto;

/** A route with its operational performance, for the Routes page. */
public class RouteSummaryDto {

    private String routeId;
    private String routeName;
    private String mode;
    private String originName;
    private String destinationName;
    private String status;
    private long stopCount;
    private long scheduledTrips;
    private Double avgDelayMin;
    private Double maxDelayMin;
    private Double onTimePercentage;
    private Long delayedTrips;
    private String performanceBand;

    public RouteSummaryDto() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getStopCount() {
        return stopCount;
    }

    public void setStopCount(long stopCount) {
        this.stopCount = stopCount;
    }

    public long getScheduledTrips() {
        return scheduledTrips;
    }

    public void setScheduledTrips(long scheduledTrips) {
        this.scheduledTrips = scheduledTrips;
    }

    public Double getAvgDelayMin() {
        return avgDelayMin;
    }

    public void setAvgDelayMin(Double avgDelayMin) {
        this.avgDelayMin = avgDelayMin;
    }

    public Double getMaxDelayMin() {
        return maxDelayMin;
    }

    public void setMaxDelayMin(Double maxDelayMin) {
        this.maxDelayMin = maxDelayMin;
    }

    public Double getOnTimePercentage() {
        return onTimePercentage;
    }

    public void setOnTimePercentage(Double onTimePercentage) {
        this.onTimePercentage = onTimePercentage;
    }

    public Long getDelayedTrips() {
        return delayedTrips;
    }

    public void setDelayedTrips(Long delayedTrips) {
        this.delayedTrips = delayedTrips;
    }

    public String getPerformanceBand() {
        return performanceBand;
    }

    public void setPerformanceBand(String performanceBand) {
        this.performanceBand = performanceBand;
    }
}
