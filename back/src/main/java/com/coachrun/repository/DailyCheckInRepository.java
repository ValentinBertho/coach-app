package com.coachrun.repository;

import com.coachrun.entity.DailyCheckIn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyCheckInRepository extends JpaRepository<DailyCheckIn, UUID> {

    Optional<DailyCheckIn> findByAthleteIdAndCheckDate(UUID athleteId, LocalDate checkDate);

    /**
     * Dernier check-in porteur d'un signal de forme (fatigue ou douleur). Un check-in où
     * l'athlète n'a bougé que le curseur de sommeil ne dit rien de sa forme : il ne doit pas
     * masquer un retour de séance plus ancien mais réellement informatif.
     */
    @Query("""
            select c from DailyCheckIn c
            where c.athlete.id = :athleteId
              and (c.fatigue is not null or c.pain is not null)
            order by c.checkDate desc, c.createdAt desc
            """)
    List<DailyCheckIn> findLatestWithSignal(@Param("athleteId") UUID athleteId, Pageable pageable);
}
