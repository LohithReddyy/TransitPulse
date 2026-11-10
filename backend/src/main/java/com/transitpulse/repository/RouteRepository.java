package com.transitpulse.repository;

import com.transitpulse.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, String> {

    List<Route> findByStatus(String status);

    List<Route> findByModeIgnoreCase(String mode);

    @Query("SELECT r FROM Route r WHERE "
            + "LOWER(r.routeName) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(r.routeId) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Route> search(@Param("term") String term);
}
