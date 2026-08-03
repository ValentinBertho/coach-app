package com.coachrun.service;

import com.coachrun.dto.request.DailyCheckInRequest;
import com.coachrun.dto.response.DailyCheckInResponse;
import com.coachrun.entity.DailyCheckIn;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DailyCheckInRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Check-in matinal de l'athlète (sommeil, fatigue, douleur).
 *
 * <p>Un seul enregistrement par athlète et par jour : rouvrir le check-in du matin le corrige,
 * il ne l'empile pas. C'est ce qui permet de le proposer sans crainte à chaque ouverture.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyCheckInService {

    private final DailyCheckInRepository repository;
    private final AthleteRepository athleteRepository;
    private final com.coachrun.security.HealthDataConsentValidator consentValidator;

    /** Check-in du jour, ou {@code null} si l'athlète ne l'a pas encore rempli. */
    public DailyCheckInResponse forDay(UUID athleteId, LocalDate day) {
        return repository.findByAthleteIdAndCheckDate(athleteId, day)
                .map(DailyCheckInResponse::of)
                .orElse(null);
    }

    /** Crée ou met à jour le check-in du jour (idempotent). */
    @Transactional
    public DailyCheckInResponse save(UUID athleteId, LocalDate day, DailyCheckInRequest request) {
        DailyCheckIn checkIn = repository.findByAthleteIdAndCheckDate(athleteId, day)
                .orElseGet(() -> {
                    DailyCheckIn fresh = new DailyCheckIn();
                    fresh.setAthlete(athleteRepository.findById(athleteId)
                            .orElseThrow(() -> new NotFoundException("Athlète introuvable.")));
                    fresh.setCheckDate(day);
                    return fresh;
                });
        checkIn.setSleep(request.sleep());
        // Fatigue et douleur sont des données de l'article 9 ; le sommeil relève du contexte
        // d'entraînement. Sans consentement actif, le check-in se enregistre quand même, amputé de
        // ses deux mesures de santé — l'athlète n'est pas empêché de faire son geste du matin.
        checkIn.setFatigue(consentValidator.keepIfAllowed(checkIn.getAthlete(), request.fatigue()));
        checkIn.setPain(consentValidator.keepIfAllowed(checkIn.getAthlete(), request.pain()));
        return DailyCheckInResponse.of(repository.save(checkIn));
    }
}
