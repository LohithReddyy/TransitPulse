package com.transitpulse.dto;

import java.time.LocalDateTime;

/**
 * Scheduled versus actual at a single stop of a trip.
 *
 * <p>{@code delayMin} is null when nothing was observed; {@code delayClass}
 * is then {@code MISSING}. A null delay is never rendered as zero.
 */
public class StopDelayDto {

    private int stopSequence;
    private String stopId;
    private String stopName;
    private LocalDateTime scheduledArrival;
    private LocalDateTime actualArrival;
    private Double delayMin;
    private String delayClass;
    private String dataQualityFlag;
    private boolean observed;
    private boolean diverted;
    private String note;

    public StopDelayDto() {
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(int stopSequence) {
        this.stopSequence = stopSequence;
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

    public LocalDateTime getScheduledArrival() {
        return scheduledArrival;
    }

    public void setScheduledArrival(LocalDateTime scheduledArrival) {
        this.scheduledArrival = scheduledArrival;
    }

    public LocalDateTime getActualArrival() {
        return actualArrival;
    }

    public void setActualArrival(LocalDateTime actualArrival) {
        this.actualArrival = actualArrival;
    }

    public Double getDelayMin() {
        return delayMin;
    }

    public void setDelayMin(Double delayMin) {
        this.delayMin = delayMin;
    }

    public String getDelayClass() {
        return delayClass;
    }

    public void setDelayClass(String delayClass) {
        this.delayClass = delayClass;
    }

    public String getDataQualityFlag() {
        return dataQualityFlag;
    }

    public void setDataQualityFlag(String dataQualityFlag) {
        this.dataQualityFlag = dataQualityFlag;
    }

    public boolean isObserved() {
        return observed;
    }

    public void setObserved(boolean observed) {
        this.observed = observed;
    }

    public boolean isDiverted() {
        return diverted;
    }

    public void setDiverted(boolean diverted) {
        this.diverted = diverted;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
