package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * The observed arrival and departure of a trip at one stop.
 *
 * <p>
 * {@code actualArrival} is deliberately nullable. A missing GPS event means
 * "we do not know", and must never be stored or interpreted as an on-time
 * arrival. {@link #dataQualityFlag} records why a value is absent.
 */
@Entity
@Table(name = "actual_movements")
public class ActualMovement {

    public static final String FLAG_OK = "OK";
    public static final String FLAG_MISSING_GPS = "MISSING_GPS";
    public static final String FLAG_DIVERTED_SKIPPED = "DIVERTED_SKIPPED";

    @Id
    @Column(name = "actual_movement_id", length = 32, nullable = false)
    private String actualMovementId;

    @Column(name = "trip_id", length = 16, nullable = false)
    private String tripId;

    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @Column(name = "stop_id", length = 16, nullable = false)
    private String stopId;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;

    @Column(name = "actual_departure")
    private LocalDateTime actualDeparture;

    @Column(name = "observed_delay_min", precision = 8, scale = 2)
    private BigDecimal observedDelayMin;

    @Column(name = "data_quality_flag", length = 24, nullable = false)
    private String dataQualityFlag = FLAG_OK;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_diverted", columnDefinition = "CHAR(1)")
    private String isDiverted = "N";

    public ActualMovement() {
    }

    /** True only when this record can be compared against the schedule. */
    public boolean isComparable() {
        return actualArrival != null && !"Y".equals(isDiverted);
    }

    public String getActualMovementId() {
        return actualMovementId;
    }

    public void setActualMovementId(String actualMovementId) {
        this.actualMovementId = actualMovementId;
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

    public LocalDateTime getActualArrival() {
        return actualArrival;
    }

    public void setActualArrival(LocalDateTime actualArrival) {
        this.actualArrival = actualArrival;
    }

    public LocalDateTime getActualDeparture() {
        return actualDeparture;
    }

    public void setActualDeparture(LocalDateTime actualDeparture) {
        this.actualDeparture = actualDeparture;
    }

    public BigDecimal getObservedDelayMin() {
        return observedDelayMin;
    }

    public void setObservedDelayMin(BigDecimal observedDelayMin) {
        this.observedDelayMin = observedDelayMin;
    }

    public String getDataQualityFlag() {
        return dataQualityFlag;
    }

    public void setDataQualityFlag(String dataQualityFlag) {
        this.dataQualityFlag = dataQualityFlag;
    }

    public String getIsDiverted() {
        return isDiverted;
    }

    public void setIsDiverted(String isDiverted) {
        this.isDiverted = isDiverted;
    }
}
