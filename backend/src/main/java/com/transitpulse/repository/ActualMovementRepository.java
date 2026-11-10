package com.transitpulse.repository;

import com.transitpulse.entity.ActualMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActualMovementRepository
        extends JpaRepository<ActualMovement, String> {

    List<ActualMovement> findByTripIdOrderByStopSequenceAsc(String tripId);

    List<ActualMovement> findByDataQualityFlag(String dataQualityFlag);

    long countByDataQualityFlag(String dataQualityFlag);

    /** Records with no observed arrival: unknown, never on time. */
    List<ActualMovement> findByActualArrivalIsNull();
}
