package com.transitpulse.repository;

import com.transitpulse.entity.PassengerVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PassengerVolumeRepository
        extends JpaRepository<PassengerVolume, String> {

    List<PassengerVolume> findByRouteId(String routeId);

    List<PassengerVolume> findByStopId(String stopId);

    List<PassengerVolume> findByIsLowVolume(String isLowVolume);

    @Query("SELECT COALESCE(SUM(p.boardings), 0) FROM PassengerVolume p "
            + "WHERE p.stopId = :stopId")
    Long totalBoardingsForStop(@Param("stopId") String stopId);

    @Query("SELECT COALESCE(AVG(p.averageWaitMin), 0) FROM PassengerVolume p "
            + "WHERE p.stopId = :stopId")
    Double averageWaitForStop(@Param("stopId") String stopId);
}
