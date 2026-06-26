package com.coachrun.repository;

import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.DeviceProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceConnectionRepository extends JpaRepository<DeviceConnection, UUID> {

    Optional<DeviceConnection> findByAthleteIdAndProvider(UUID athleteId, DeviceProvider provider);

    /** Identifiants (athlète + club) des connexions d'un provider, pour la synchro planifiée. */
    @Query("""
            select c.athlete.id as athleteId, c.athlete.club.id as clubId
            from DeviceConnection c
            where c.provider = :provider and c.athlete.club is not null
            """)
    List<AthleteClubIds> findAthleteClubIdsByProvider(@Param("provider") DeviceProvider provider);

    /** Projection légère pour la synchro (évite de charger les entités/tokens). */
    interface AthleteClubIds {
        UUID getAthleteId();
        UUID getClubId();
    }
}
