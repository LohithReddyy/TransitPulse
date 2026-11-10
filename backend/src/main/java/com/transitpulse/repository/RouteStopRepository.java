package com.transitpulse.repository;

import com.transitpulse.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, String> {

    List<RouteStop> findByRouteIdOrderByStopSequenceAsc(String routeId);

    List<RouteStop> findByStopId(String stopId);

    long countByRouteId(String routeId);
}
