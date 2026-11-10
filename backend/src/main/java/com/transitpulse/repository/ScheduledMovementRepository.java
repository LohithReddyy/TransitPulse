package com.transitpulse.repository;

import com.transitpulse.entity.ScheduledMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScheduledMovementRepository
        extends JpaRepository<ScheduledMovement, String> {

    List<ScheduledMovement> findByTripIdOrderByStopSequenceAsc(String tripId);

    List<ScheduledMovement> findByRouteId(String routeId);

    List<ScheduledMovement> findByStopId(String stopId);
}
