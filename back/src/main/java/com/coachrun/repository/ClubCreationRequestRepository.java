package com.coachrun.repository;

import com.coachrun.entity.ClubCreationRequest;
import com.coachrun.entity.enums.ClubRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClubCreationRequestRepository extends JpaRepository<ClubCreationRequest, UUID> {

    /** File d'arbitrage, filtrable par statut. Paginée : elle grossit à chaque dépôt. */
    Page<ClubCreationRequest> findByStatusOrderByCreatedAtDesc(ClubRequestStatus status,
                                                               Pageable pageable);

    Page<ClubCreationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ClubRequestStatus status);

    /**
     * La demande en attente d'une adresse donnée.
     *
     * <p>Sert à répondre « votre demande est déjà enregistrée » plutôt que d'empiler dix lignes
     * identiques : un candidat qui n'a pas de nouvelle renvoie le formulaire, c'est le
     * comportement normal.</p>
     */
    Optional<ClubCreationRequest> findFirstByEmailIgnoreCaseAndStatus(String email,
                                                                     ClubRequestStatus status);
}
