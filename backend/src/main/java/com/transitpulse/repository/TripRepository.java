package com.transitpulse.repository;

import com.transitpulse.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {

    List<Trip> findByRouteIdOrderByScheduledStartTimeAsc(String routeId);

    List<Trip> findByServiceDate(LocalDate serviceDate);

    /** Ordered trip chain of one vehicle: the basis of continuity analysis. */
    List<Trip> findByVehicleIdOrderByScheduledStartTimeAsc(String vehicleId);

    List<Trip> findByBlockIdOrderByScheduledStartTimeAsc(String blockId);

    long countByRouteId(String routeId);
}
