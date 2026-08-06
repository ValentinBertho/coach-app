package com.coachrun.repository;

import com.coachrun.entity.CalendarNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarNoteRepository extends JpaRepository<CalendarNote, UUID> {

    /**
     * Notes <b>chevauchant</b> la fenêtre affichée, et non celles qui y commencent.
     *
     * <p>Depuis qu'une note peut couvrir une période (un cycle), la dérivation par
     * {@code noteDateBetween} ne suffit plus : un cycle commencé le 27 juillet et courant jusqu'au
     * 24 août disparaissait entièrement du mois d'août — le mois qu'il occupe pourtant en entier.
     * Une note sans date de fin reste comparée sur son seul jour.</p>
     */
    @Query("""
            select n from CalendarNote n
            where n.club.id = :clubId and n.athlete.id = :athleteId
              and n.noteDate <= :to and coalesce(n.endDate, n.noteDate) >= :from
            order by n.noteDate asc
            """)
    List<CalendarNote> findOverlapping(@Param("clubId") UUID clubId, @Param("athleteId") UUID athleteId,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select n from CalendarNote n
            where n.athlete.id = :athleteId
              and n.noteDate <= :to and coalesce(n.endDate, n.noteDate) >= :from
            order by n.noteDate asc
            """)
    List<CalendarNote> findOverlappingForAthlete(@Param("athleteId") UUID athleteId,
                                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<CalendarNote> findByIdAndClubId(UUID id, UUID clubId);
}
