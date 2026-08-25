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

    /**
     * Athlètes locaux rattachés à un compte externe donné, pour router un événement entrant.
     *
     * <p>Une liste, pas un {@code Optional} : rien n'interdit à deux athlètes de cette instance
     * d'être liés au même compte Strava (un coach qui teste sur sa propre fiche, par exemple), et
     * une contrainte d'unicité n'existe que sur le couple (athlète, provider).</p>
     *
     * <p>On ne renvoie que des identifiants : l'événement est traité hors requête, où la relation
     * paresseuse vers l'athlète ne serait plus chargeable.</p>
     */
    @Query("""
            select c.athlete.id from DeviceConnection c
            where c.provider = :provider and c.providerAthleteId = :providerAthleteId
            """)
    List<UUID> findAthleteIdsByProviderAndProviderAthleteId(@Param("provider") DeviceProvider provider,
                                                            @Param("providerAthleteId") String providerAthleteId);

    /** Projection légère pour la synchro (évite de charger les entités/tokens). */
    interface AthleteClubIds {
        UUID getAthleteId();
        UUID getClubId();
    }

    long countByProvider(DeviceProvider provider);

    /** Connexions d'appareil d'un club : « la synchro Strava est-elle en place ici ? ». */
    @Query("select count(c) from DeviceConnection c where c.athlete.club.id = :clubId")
    long countByClub(@Param("clubId") UUID clubId);
}
