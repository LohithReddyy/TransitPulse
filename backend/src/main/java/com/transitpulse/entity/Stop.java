package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.sql.Types;
import org.hibernate.annotations.JdbcTypeCode;

/** A physical stop or station served by one or more routes. */
@Entity
@Table(name = "stops")
public class Stop {

    @Id
    @Column(name = "stop_id", length = 16, nullable = false)
    private String stopId;

    @NotBlank
    @Column(name = "stop_name", length = 128, nullable = false)
    private String stopName;

    @Column(name = "stop_type", length = 24, nullable = false)
    private String stopType = "STANDARD";

    @Column(name = "latitude", precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "is_interchange", columnDefinition = "CHAR(1)", nullable = false)
    private String isInterchange = "N";

    @JdbcTypeCode(Types.TINYINT)
    @Column(name = "step_free", nullable = false)
    private Integer stepFree = 0;

    public Stop() {
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

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getIsInterchange() {
        return isInterchange;
    }

    public void setIsInterchange(String isInterchange) {
        this.isInterchange = isInterchange;
    }

    public Integer getStepFree() {
        return stepFree;
    }

    public void setStepFree(Integer stepFree) {
        this.stepFree = stepFree;
    }
}
