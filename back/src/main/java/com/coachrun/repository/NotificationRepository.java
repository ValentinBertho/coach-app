package com.coachrun.repository;

import com.coachrun.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    /**
     * Anti-rafale : une notification de même type et de même destination existe-t-elle déjà pour
     * cet utilisateur dans la fenêtre ?
     *
     * <p>Le centre de notifications sert ici de mémoire du canal — il n'y a rien d'autre à tenir
     * à jour, et la trace est déjà écrite avant tout envoi. Le lien identifie le sujet (le fil de
     * messagerie, le calendrier de l'athlète), donc deux sujets distincts ne se masquent jamais.
     * Couvert par {@code idx_notifications_user_created} sur sa colonne de tête.</p>
     */
    boolean existsByUserIdAndTypeAndLinkAndCreatedAtAfter(
            UUID userId, String type, String link, Instant createdAt);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Purge de rétention : la table n'en avait aucune, et grossissait d'une ligne par séance
     * planifiée, par rappel et par message, indéfiniment.
     *
     * @return nombre de lignes retirées.
     */
    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
