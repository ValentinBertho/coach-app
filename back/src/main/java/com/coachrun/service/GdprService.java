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
    private final AthleteZoneValueRepository zoneValueRepository;

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
}
