package com.coachrun.repository;

import com.coachrun.entity.CoachProfileReport;
import com.coachrun.entity.enums.CoachReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoachProfileReportRepository extends JpaRepository<CoachProfileReport, UUID> {

    /**
     * La file de modération, du plus ancien au plus récent.
     *
     * <p>L'ordre n'est pas cosmétique : un signalement qui attend depuis trois jours passe avant
     * celui d'hier, faute de quoi une file chargée enterre définitivement ses plus vieux dossiers.</p>
     */
    List<CoachProfileReport> findByStatusOrderByCreatedAtAsc(CoachReportStatus status);

    /** Le plus ancien signalement non traité : c'est son âge qui dit si la file dérape. */
    Optional<CoachProfileReport> findFirstByStatusOrderByCreatedAtAsc(CoachReportStatus status);

    /** La pastille « signalements à traiter » : un compte, pas la file entière. */
    long countByStatus(CoachReportStatus status);

    /** Combien de fois cette fiche est signalée sans réponse — la première question de l'arbitre. */
    long countByProfileIdAndStatus(UUID profileId, CoachReportStatus status);

    /** Garde anti-acharnement : combien cette adresse a-t-elle déjà signalé cette fiche. */
    long countByProfileIdAndIpAddress(UUID profileId, String ipAddress);

    /** Garde anti-inondation : le volume déposé par une adresse sur la journée écoulée. */
    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant since);
}
