package com.transitpulse.dto;

import java.util.List;

/** A stop with its connections, passenger volume and delay behaviour. */
public class StopSummaryDto {

    private String stopId;
    private String stopName;
    private String stopType;
    private boolean interchange;
    private List<String> routeConnections;
    private long passengerVolume;
    private Double averageWaitMin;
    private Double avgDelayMin;
    private Double delayFrequencyPct;
    private long observations;
    /** True when the passenger sample is too small to quote confidently. */
    private boolean lowVolume;
    private String confidence;

    public StopSummaryDto() {
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getStopName() {
        return stopName;
    }

    public void setStopName(String stopName) {
        this.stopName = stopName;
    }

    public String getStopType() {
        return stopType;
    }

    public void setStopType(String stopType) {
        this.stopType = stopType;
    }

    public boolean isInterchange() {
        return interchange;
    }

    public void setInterchange(boolean interchange) {
        this.interchange = interchange;
    }

    public List<String> getRouteConnections() {
        return routeConnections;
    }

    public void setRouteConnections(List<String> routeConnections) {
        this.routeConnections = routeConnections;
    }

    public long getPassengerVolume() {
        return passengerVolume;
    }

    public void setPassengerVolume(long passengerVolume) {
        this.passengerVolume = passengerVolume;
    }

    public Double getAverageWaitMin() {
        return averageWaitMin;
    }

    public void setAverageWaitMin(Double averageWaitMin) {
        this.averageWaitMin = averageWaitMin;
    }

    public Double getAvgDelayMin() {
        return avgDelayMin;
    }

    public void setAvgDelayMin(Double avgDelayMin) {
        this.avgDelayMin = avgDelayMin;
    }

    public Double getDelayFrequencyPct() {
        return delayFrequencyPct;
    }

    public void setDelayFrequencyPct(Double delayFrequencyPct) {
        this.delayFrequencyPct = delayFrequencyPct;
    }

    public long getObservations() {
        return observations;
    }

    public void setObservations(long observations) {
        this.observations = observations;
    }

    public boolean isLowVolume() {
        return lowVolume;
    }

    public void setLowVolume(boolean lowVolume) {
        this.lowVolume = lowVolume;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}
