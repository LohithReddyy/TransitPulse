package com.transitpulse.repository;

import com.transitpulse.entity.TransferConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransferConnectionRepository
        extends JpaRepository<TransferConnection, String> {

    List<TransferConnection> findByInterchangeStopId(String interchangeStopId);

    List<TransferConnection> findByFeederTripId(String feederTripId);

    List<TransferConnection> findByFeederRouteId(String feederRouteId);

    /** Connections that are tight even when every service runs on time. */
    @Query("SELECT c FROM TransferConnection c "
            + "WHERE c.plannedTransferMin < c.requiredBufferMin "
            + "ORDER BY c.plannedTransferMin ASC")
    List<TransferConnection> findStructurallyTight();
}
