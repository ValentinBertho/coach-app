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

    /** Fil borné (les plus récents d'abord) — le fil entier était chargé à chaque ouverture. */
    List<Message> findByClubIdAndAthleteIdOrderByCreatedAtDesc(
            UUID clubId, UUID athleteId, org.springframework.data.domain.Pageable pageable);

    List<Message> findByAthleteIdOrderByCreatedAtAsc(UUID athleteId);

    List<Message> findByAthleteIdOrderByCreatedAtDesc(
            UUID athleteId, org.springframework.data.domain.Pageable pageable);

    // --- Boîte de réception coach ---

    /** Dernier message du fil d'un athlète (aperçu de la conversation). */
    Optional<Message> findFirstByClubIdAndAthleteIdOrderByCreatedAtDesc(UUID clubId, UUID athleteId);

    /** Non-lus d'un fil : messages de l'athlète que le coach n'a pas encore ouverts. */
    long countByClubIdAndAthleteIdAndSenderRoleAndCoachReadAtIsNull(UUID clubId, UUID athleteId, UserRole senderRole);

    /**
     * Non-lus par athlète, en une requête — le badge de navigation se rafraîchit à chaque
     * changement d'écran, il ne doit pas coûter deux requêtes par athlète du club.
     * Chaque ligne : [athleteId, count].
     */
    @Query("""
            select m.athlete.id, count(m) from Message m
            where m.club.id = :clubId and m.senderRole = :senderRole and m.coachReadAt is null
            group by m.athlete.id
            """)
    List<Object[]> countUnreadByAthlete(@Param("clubId") UUID clubId, @Param("senderRole") UserRole senderRole);

    // --- Fils de conversation -------------------------------------------------------------

    /** Fil borné, les plus récents d'abord. */
    List<Message> findByConversationIdOrderByCreatedAtDesc(
            UUID conversationId, org.springframework.data.domain.Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /** Non-lus d'un fil pour une personne : ce qui est arrivé après son dernier passage. */
    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.senderUserId <> :userId
              and (:since is null or m.createdAt > :since)
            """)
    long countUnread(@Param("conversationId") UUID conversationId,
                     @Param("userId") UUID userId, @Param("since") Instant since);

    /** Messages sans fil : le backfill des échanges antérieurs au modèle de conversations. */
    List<Message> findByConversationIsNull();

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
