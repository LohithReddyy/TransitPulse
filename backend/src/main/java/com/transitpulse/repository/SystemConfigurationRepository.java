package com.transitpulse.repository;

import com.transitpulse.entity.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SystemConfigurationRepository
        extends JpaRepository<SystemConfiguration, String> {

    List<SystemConfiguration> findByCategoryOrderByConfigKeyAsc(String category);

    List<SystemConfiguration> findAllByOrderByCategoryAscConfigKeyAsc();
}
