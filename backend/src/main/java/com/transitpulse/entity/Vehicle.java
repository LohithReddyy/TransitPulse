package com.transitpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A vehicle in the fleet. Vehicle continuity drives delay propagation. */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @Column(name = "vehicle_id", length = 16, nullable = false)
    private String vehicleId;

    @Column(name = "fleet_number", length = 32, nullable = false)
    private String fleetNumber;

    @Column(name = "vehicle_type", length = 16, nullable = false)
    private String vehicleType = "BUS";

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 0;

    @Column(name = "depot", length = 64)
    private String depot;

    @Column(name = "status", length = 24, nullable = false)
    private String status = "IN_SERVICE";

    public Vehicle() {
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getFleetNumber() {
        return fleetNumber;
    }

    public void setFleetNumber(String fleetNumber) {
        this.fleetNumber = fleetNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getDepot() {
        return depot;
    }

    public void setDepot(String depot) {
        this.depot = depot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
