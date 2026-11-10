package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * Average passenger volumes for a route and stop in a time band.
 *
 * <p>
 * These are profile averages, which is why every passenger figure derived
 * from them is reported as an estimate. {@link #isLowVolume} marks samples too
 * small to support confident statistics.
 */
@Entity
@Table(name = "passenger_volumes")
public class PassengerVolume {

    @Id
    @Column(name = "passenger_volume_id", length = 48, nullable = false)
    private String passengerVolumeId;

    @Column(name = "route_id", length = 16, nullable = false)
    private String routeId;

    @Column(name = "stop_id", length = 16, nullable = false)
    private String stopId;

    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence = 0;

    @Column(name = "time_band", length = 16, nullable = false)
    private String timeBand;

    @Column(name = "boardings", nullable = false)
    private Integer boardings = 0;

    @Column(name = "alightings", nullable = false)
    private Integer alightings = 0;

    @Column(name = "average_load", nullable = false)
    private Integer averageLoad = 0;

    @Column(name = "average_wait_min", precision = 6, scale = 2, nullable = false)
    private BigDecimal averageWaitMin = BigDecimal.ZERO;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_low_volume", columnDefinition = "CHAR(1)")
    private String isLowVolume = "N";

    public PassengerVolume() {
    }

    public String getPassengerVolumeId() {
        return passengerVolumeId;
    }

    public void setPassengerVolumeId(String passengerVolumeId) {
        this.passengerVolumeId = passengerVolumeId;
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

    public String getTimeBand() {
        return timeBand;
    }

    public void setTimeBand(String timeBand) {
        this.timeBand = timeBand;
    }

    public Integer getBoardings() {
        return boardings;
    }

    public void setBoardings(Integer boardings) {
        this.boardings = boardings;
    }

    public Integer getAlightings() {
        return alightings;
    }

    public void setAlightings(Integer alightings) {
        this.alightings = alightings;
    }

    public Integer getAverageLoad() {
        return averageLoad;
    }

    public void setAverageLoad(Integer averageLoad) {
        this.averageLoad = averageLoad;
    }

    public BigDecimal getAverageWaitMin() {
        return averageWaitMin;
    }

    public void setAverageWaitMin(BigDecimal averageWaitMin) {
        this.averageWaitMin = averageWaitMin;
    }

    public String getIsLowVolume() {
        return isLowVolume;
    }

    public void setIsLowVolume(String isLowVolume) {
        this.isLowVolume = isLowVolume;
    }
}
