package com.transitpulse.repository;

import com.transitpulse.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StopRepository extends JpaRepository<Stop, String> {

    List<Stop> findByIsInterchange(String isInterchange);

    @Query("SELECT s FROM Stop s WHERE "
            + "LOWER(s.stopName) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(s.stopId) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Stop> search(@Param("term") String term);
}
