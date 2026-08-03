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
}
