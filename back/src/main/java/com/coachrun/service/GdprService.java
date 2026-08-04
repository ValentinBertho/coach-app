package com.coachrun.service;

import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.dto.response.AthleteExportResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.AthleteZoneValueResponse;
import com.coachrun.dto.response.DailyCheckInResponse;
import com.coachrun.dto.response.LactateTestResponse;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.dto.response.PerformanceResponse;
import com.coachrun.dto.response.RaceObjectiveResponse;
import com.coachrun.dto.response.StrengthResultExportResponse;
import com.coachrun.dto.response.StrengthTestResponse;
import com.coachrun.dto.response.UnavailabilityResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthletePerformanceRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteUnavailabilityRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.DailyCheckInRepository;
import com.coachrun.repository.LactateTestRepository;
import com.coachrun.repository.MessageRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.StrengthResultRepository;
import com.coachrun.repository.StrengthTestRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RGPD : portabilité (export) et droit à l'oubli (suppression). La suppression de
 * l'athlète purge en cascade ses séances, activités et son compte (FK ON DELETE CASCADE).
 *
 * <p>L'export couvre l'ensemble des familles de données rattachées à l'athlète, et pas seulement
 * son profil et son calendrier : ce qu'il a déclaré (check-ins, indisponibilités, objectifs),
 * ce qu'il a produit (retours de séance, séries de force, records) et ce qui a été mesuré sur lui
 * (tests lactate, valeurs de zones). Un dossier de portabilité incomplet ne remplit pas
 * l'obligation de l'art. 20.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GdprService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;
    private final DailyCheckInRepository dailyCheckInRepository;
    private final LactateTestRepository lactateTestRepository;
    private final AthletePerformanceRepository performanceRepository;
    private final RaceObjectiveRepository raceObjectiveRepository;
    private final AthleteUnavailabilityRepository unavailabilityRepository;
    private final MessageRepository messageRepository;
    private final StrengthTestRepository strengthTestRepository;
    private final StrengthResultRepository strengthResultRepository;
    private final com.coachrun.repository.ScheduledStrengthSessionRepository scheduledStrengthRepository;
    private final AthleteZoneValueRepository zoneValueRepository;
    private final NotificationService notificationService;

    public AthleteExportResponse export(UUID athleteId) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        LocalDate today = LocalDate.now();

        var workouts = workoutRepository.findByAthleteIdOrderByScheduledDateAsc(athleteId)
                .stream().map(WorkoutResponse::from).toList();
        var activities = activityRepository.findByAthleteIdOrderByActivityDateDesc(athleteId)
                .stream().map(ActivityResponse::from).toList();
        var checkIns = dailyCheckInRepository.findByAthleteIdOrderByCheckDateDesc(athleteId)
                .stream().map(DailyCheckInResponse::of).toList();
        // Tests lactate exportés en entier (paliers compris) et non en résumé : les mesures
        // brutes font partie des données de l'athlète.
        var lactateTests = lactateTestRepository.findByAthleteIdOrderByTestDateDesc(athleteId)
                .stream().map(LactateTestResponse::from).toList();
        var performances = performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(athleteId)
                .stream().map(p -> PerformanceResponse.from(p, null)).toList();
        var races = raceObjectiveRepository.findByAthleteIdOrderByRaceDateAsc(athleteId)
                .stream().map(r -> RaceObjectiveResponse.from(r, today)).toList();
        var unavailabilities = unavailabilityRepository.findByAthleteIdOrderByStartDateDesc(athleteId)
                .stream().map(UnavailabilityResponse::from).toList();
        // Messagerie : métadonnées de pièces jointes uniquement — le binaire se récupère par
        // l'API dédiée, l'inclure ici rendrait le JSON inexploitable.
        var messages = messageRepository.findByAthleteIdOrderByCreatedAtAsc(athleteId)
                .stream().map(MessageResponse::from).toList();
        var strengthTests = strengthTestRepository.findByAthleteIdOrderByTestDateDesc(athleteId)
                .stream().map(StrengthTestResponse::from).toList();
        var strengthResults = strengthResultRepository.findByAthleteIdOrderByCreatedAtDesc(athleteId)
                .stream().map(StrengthResultExportResponse::from).toList();
        var zoneValues = zoneValueRepository.findByAthleteId(athleteId)
                .stream().map(AthleteZoneValueResponse::from).toList();

        log.info("[RGPD] Export de l'athlète {} : {} séances, {} activités, {} check-ins, {} messages",
                athleteId, workouts.size(), activities.size(), checkIns.size(), messages.size());

        return new AthleteExportResponse(
                Instant.now(), athlete.getHealthDataConsentAt(),
                AthleteResponse.from(athlete), workouts, activities,
                checkIns, lactateTests, performances, races, unavailabilities,
                messages, strengthTests, strengthResults, zoneValues);
    }

    @Transactional
    public void deleteAthleteData(UUID athleteId) {
        if (!athleteRepository.existsById(athleteId)) {
            throw new NotFoundException("Athlète introuvable.");
        }
        athleteRepository.deleteById(athleteId);
        log.warn("[RGPD] Données de l'athlète {} supprimées (droit à l'oubli).", athleteId);
    }

    /** État courant du consentement au traitement des données de santé. */
    public com.coachrun.dto.response.HealthConsentResponse consent(UUID athleteId) {
        Athlete athlete = require(athleteId);
        return new com.coachrun.dto.response.HealthConsentResponse(
                athlete.isHealthDataConsentActive(),
                athlete.getHealthDataConsentAt(),
                athlete.getHealthDataConsentWithdrawnAt());
    }

    /**
     * RGPD art. 7-3 — retrait du consentement au traitement des données de santé.
     *
     * <p><b>Périmètre de l'effacement.</b> Il suit exactement ce que la politique de
     * confidentialité publiée désigne comme donnée de l'article 9 : « mesures de lactate,
     * niveaux de douleur et de fatigue, indisponibilités (blessure, maladie) ». Rien de plus,
     * rien de moins — élargir détruirait des données dont la base légale est l'exécution du
     * contrat (allures, distances, RPE, qui mesure un effort et non un état de santé), et
     * restreindre laisserait des données sans base légale.</p>
     *
     * <p>Concrètement : les tests de lactate sont supprimés ; la douleur et la fatigue déclarées
     * sont effacées des check-ins et des séances ; le motif médical d'une indisponibilité est
     * ramené à « autre » et son commentaire libre effacé — la période reste, car c'est une
     * information de planification que le coach a besoin de conserver et qui ne dit plus rien de
     * l'état de santé ; les notes médicales libres du profil sont effacées.</p>
     *
     * <p>Le compte, lui, n'est pas touché : le retrait de consentement et le droit à l'oubli sont
     * deux droits distincts, et confondre les deux dissuaderait d'exercer le premier.</p>
     */
    @Transactional
    public void withdrawHealthConsent(UUID athleteId) {
        Athlete athlete = require(athleteId);
        if (!athlete.isHealthDataConsentActive()) {
            return; // déjà retiré : idempotent, pas d'erreur
        }
        athlete.setHealthDataConsentWithdrawnAt(Instant.now());

        var lactateTests = lactateTestRepository.findByAthleteIdOrderByTestDateDesc(athleteId);
        int lactate = lactateTests.size();
        lactateTestRepository.deleteAll(lactateTests);

        int checkIns = 0;
        for (var checkIn : dailyCheckInRepository.findByAthleteIdOrderByCheckDateDesc(athleteId)) {
            if (checkIn.getPain() != null || checkIn.getFatigue() != null) {
                checkIn.setPain(null);
                checkIn.setFatigue(null);
                checkIns++;
            }
        }

        int workouts = 0;
        for (var workout : workoutRepository.findByAthleteIdOrderByScheduledDateAsc(athleteId)) {
            // La fatigue déclarée était oubliée ici alors que la politique la désigne au même titre
            // que la douleur : le retrait laissait donc en base une partie exacte de ce qu'il
            // annonçait effacer, et le journal ci-dessous affirmait le contraire.
            if (workout.getPain() != null || workout.getFatigue() != null) {
                workout.setPain(null);
                workout.setFatigue(null);
                workouts++;
            }
        }

        // Préparation physique : la douleur et la fatigue s'y déclarent exactement comme en course
        // (retour de séance et série par série) et n'étaient pas touchées du tout.
        int strengthSessions = 0;
        for (var session : scheduledStrengthRepository.findByAthleteIdOrderByScheduledDateAsc(athleteId)) {
            if (session.getSessionPain() != null || session.getSessionFatigue() != null) {
                session.setSessionPain(null);
                session.setSessionFatigue(null);
                strengthSessions++;
            }
        }

        int strengthSets = 0;
        for (var result : strengthResultRepository.findByAthleteIdOrderByCreatedAtDesc(athleteId)) {
            if (result.getPain() != null) {
                result.setPain(null);
                strengthSets++;
            }
        }

        int unavailabilities = 0;
        for (var unavailability : unavailabilityRepository.findByAthleteIdOrderByStartDateDesc(athleteId)) {
            if (unavailability.getReason() != com.coachrun.entity.enums.UnavailabilityReason.OTHER
                    || unavailability.getNotes() != null) {
                unavailability.setReason(com.coachrun.entity.enums.UnavailabilityReason.OTHER);
                unavailability.setNotes(null);
                unavailabilities++;
            }
        }

        athlete.setMedicalNotes(null);

        log.warn("[RGPD] Consentement santé retiré (athlète={}) — effacé : {} test(s) lactate, "
                        + "{} check-in(s), {} séance(s) course, {} séance(s) de force, {} série(s), "
                        + "{} indisponibilité(s), notes médicales.",
                athleteId, lactate, checkIns, workouts, strengthSessions, strengthSets, unavailabilities);

        // Le coach doit le savoir : sinon il continue de saisir des mesures désormais refusées,
        // et constate une fiche qui se vide sans explication.
        notificationService.notifyHealthConsentWithdrawn(athlete);
    }

    /** L'athlète redonne son consentement : la collecte reprend, le passé effacé ne revient pas. */
    @Transactional
    public void grantHealthConsent(UUID athleteId) {
        Athlete athlete = require(athleteId);
        if (athlete.isHealthDataConsentActive()) {
            return;
        }
        athlete.setHealthDataConsentAt(Instant.now());
        log.info("[RGPD] Consentement santé redonné (athlète={}).", athleteId);
    }

    private Athlete require(UUID athleteId) {
        return athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
