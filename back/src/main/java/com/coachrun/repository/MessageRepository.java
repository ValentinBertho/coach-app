package com.coachrun.repository;

import com.coachrun.entity.Message;
import com.coachrun.entity.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByClubIdAndAthleteIdOrderByCreatedAtAsc(UUID clubId, UUID athleteId);

    List<Message> findByAthleteIdOrderByCreatedAtAsc(UUID athleteId);

    // --- Boîte de réception coach ---

    /** Dernier message du fil d'un athlète (aperçu de la conversation). */
    Optional<Message> findFirstByClubIdAndAthleteIdOrderByCreatedAtDesc(UUID clubId, UUID athleteId);

    /** Non-lus d'un fil : messages de l'athlète que le coach n'a pas encore ouverts. */
    long countByClubIdAndAthleteIdAndSenderRoleAndCoachReadAtIsNull(UUID clubId, UUID athleteId, UserRole senderRole);

    /** Marque le fil comme lu (accusé de lecture à l'ouverture de la conversation). */
    @Modifying
    @Query("""
            update Message m set m.coachReadAt = :now
            where m.club.id = :clubId and m.athlete.id = :athleteId
              and m.senderRole = :senderRole and m.coachReadAt is null
            """)
    int markThreadRead(@Param("clubId") UUID clubId, @Param("athleteId") UUID athleteId,
                       @Param("senderRole") UserRole senderRole, @Param("now") Instant now);
}
