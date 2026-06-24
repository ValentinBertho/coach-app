package com.coachrun.repository;

import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.DeviceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeviceConnectionRepository extends JpaRepository<DeviceConnection, UUID> {

    Optional<DeviceConnection> findByAthleteIdAndProvider(UUID athleteId, DeviceProvider provider);
}
