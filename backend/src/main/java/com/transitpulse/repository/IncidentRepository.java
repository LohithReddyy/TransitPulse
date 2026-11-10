package com.transitpulse.repository;

import com.transitpulse.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    List<Incident> findByRouteIdOrderByStartTimeDesc(String routeId);

    List<Incident> findByStatus(String status);

    List<Incident> findBySeverity(String severity);

    List<Incident> findByIsDiversion(String isDiversion);

    long countByStatusNot(String status);

    /** Incidents covering a moment in time on a route, used as causal evidence. */
    @Query("SELECT i FROM Incident i WHERE i.routeId = :routeId "
            + "AND i.startTime <= :at "
            + "AND (i.endTime IS NULL OR i.endTime >= :at)")
    List<Incident> findCovering(@Param("routeId") String routeId,
                                @Param("at") LocalDateTime at);
}
