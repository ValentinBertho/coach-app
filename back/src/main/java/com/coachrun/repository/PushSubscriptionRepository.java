package com.coachrun.repository;

import com.coachrun.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserId(UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    /**
     * Retrait d'un abonnement caduc (réponse 404/410 du service de push).
     *
     * <p>Une seule instruction, dans sa propre transaction : la remise des notifications
     * s'exécute délibérément <b>hors</b> transaction métier, sur un thread dédié, et n'a donc
     * aucun contexte transactionnel à réutiliser. Un {@code @Transactional} porté par le service
     * ne s'appliquerait pas ici — l'appel est interne à la classe, donc hors proxy.</p>
     *
     * @return nombre de lignes supprimées (0 si un autre thread l'a déjà retiré).
     */
    @Modifying
    @Transactional
    int deleteByEndpoint(String endpoint);

    /** Abonnements d'un utilisateur, le plus récemment ajouté d'abord (écran « mes appareils »). */
    List<PushSubscription> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PushSubscription> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Marque un endpoint comme joignable, <b>au plus une fois par heure</b> — d'où le
     * {@code staleBefore} : sans lui, chaque notification écrirait une ligne par appareil, pour
     * une information qui se lit à l'heure près.
     *
     * <p>Transaction portée par le dépôt : l'appel vient du fil de remise, délibérément hors de
     * toute transaction métier.</p>
     */
    @Modifying
    @Transactional
    @org.springframework.data.jpa.repository.Query("""
            update PushSubscription s set s.lastSuccessAt = :now
            where s.endpoint = :endpoint
              and (s.lastSuccessAt is null or s.lastSuccessAt < :staleBefore)
            """)
    int markUsed(@org.springframework.data.repository.query.Param("endpoint") String endpoint,
                 @org.springframework.data.repository.query.Param("now") java.time.Instant now,
                 @org.springframework.data.repository.query.Param("staleBefore") java.time.Instant staleBefore);

    /**
     * Abonnements de cette personne dont on a une raison de croire qu'ils fonctionnent : ceux qui
     * ont déjà accepté une remise, et ceux qui viennent d'être créés — on leur laisse le bénéfice
     * du doute le temps qu'une première notification passe.
     *
     * <p>Sert à décider du repli e-mail. Compter tout abonnement <b>existant</b> comme joignable
     * revenait à faire taire l'e-mail à cause d'un appareil mort : application désinstallée,
     * permission révoquée ou téléphone changé ne se signalent qu'à la remise suivante, qui échoue
     * — et le message ayant servi à le découvrir n'aura alerté par aucun canal.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
            select count(s) from PushSubscription s
            where s.userId = :userId
              and (s.lastSuccessAt is not null or s.createdAt > :graceStart)
            """)
    long countLikelyReachable(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId,
                              @org.springframework.data.repository.query.Param("graceStart") java.time.Instant graceStart);
}
