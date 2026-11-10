package com.transitpulse.dto;

import java.util.List;
import java.util.Map;

/** Everything the Delay Investigation page needs for one trip. */
public class TripInvestigationDto {

    private String tripId;
    private String routeId;
    private String routeName;
    private String vehicleId;
    private String serviceDate;

    private List<StopDelayDto> stopDelays;

    private Double maxDelayMin;
    private Double finalDelayMin;
    private Integer firstDelayStopSequence;
    private String delayClass;
    private boolean recovered;
    private Double recoveryMin;

    private int stopsTotal;
    private int stopsObserved;
    private int stopsMissing;
    private String dataConfidence;

    /** Downstream services this trip's delay reached, with evidence. */
    private List<Map<String, Object>> propagation;
    /** Transfer connections put at risk by this trip. */
    private List<Map<String, Object>> affectedConnections;
    /** Operator incidents overlapping this trip. */
    private List<Map<String, Object>> relatedIncidents;

    private String explanation;

    public TripInvestigationDto() {
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

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate) {
        this.serviceDate = serviceDate;
    }

    public List<StopDelayDto> getStopDelays() {
        return stopDelays;
    }

    public void setStopDelays(List<StopDelayDto> stopDelays) {
        this.stopDelays = stopDelays;
    }

    public Double getMaxDelayMin() {
        return maxDelayMin;
    }

    public void setMaxDelayMin(Double maxDelayMin) {
        this.maxDelayMin = maxDelayMin;
    }

    public Double getFinalDelayMin() {
        return finalDelayMin;
    }

    public void setFinalDelayMin(Double finalDelayMin) {
        this.finalDelayMin = finalDelayMin;
    }

    public Integer getFirstDelayStopSequence() {
        return firstDelayStopSequence;
    }

    public void setFirstDelayStopSequence(Integer firstDelayStopSequence) {
        this.firstDelayStopSequence = firstDelayStopSequence;
    }

    public String getDelayClass() {
        return delayClass;
    }

    public void setDelayClass(String delayClass) {
        this.delayClass = delayClass;
    }

    public boolean isRecovered() {
        return recovered;
    }

    public void setRecovered(boolean recovered) {
        this.recovered = recovered;
    }

    public Double getRecoveryMin() {
        return recoveryMin;
    }

    public void setRecoveryMin(Double recoveryMin) {
        this.recoveryMin = recoveryMin;
    }

    public int getStopsTotal() {
        return stopsTotal;
    }

    public void setStopsTotal(int stopsTotal) {
        this.stopsTotal = stopsTotal;
    }

    public int getStopsObserved() {
        return stopsObserved;
    }

    public void setStopsObserved(int stopsObserved) {
        this.stopsObserved = stopsObserved;
    }

    public int getStopsMissing() {
        return stopsMissing;
    }

    public void setStopsMissing(int stopsMissing) {
        this.stopsMissing = stopsMissing;
    }

    public String getDataConfidence() {
        return dataConfidence;
    }

    public void setDataConfidence(String dataConfidence) {
        this.dataConfidence = dataConfidence;
    }

    public List<Map<String, Object>> getPropagation() {
        return propagation;
    }

    public void setPropagation(List<Map<String, Object>> propagation) {
        this.propagation = propagation;
    }

    public List<Map<String, Object>> getAffectedConnections() {
        return affectedConnections;
    }

    public void setAffectedConnections(List<Map<String, Object>> affectedConnections) {
        this.affectedConnections = affectedConnections;
    }

    public List<Map<String, Object>> getRelatedIncidents() {
        return relatedIncidents;
    }

    public void setRelatedIncidents(List<Map<String, Object>> relatedIncidents) {
        this.relatedIncidents = relatedIncidents;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
