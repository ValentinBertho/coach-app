package com.coachrun.service;

import com.coachrun.dto.request.WellnessCheckInRequest;
import com.coachrun.dto.response.WellnessCheckInResponse;
import com.coachrun.entity.WellnessCheckIn;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WellnessCheckInRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Check-in matinal de l'athlète — le signal de forme <em>avant</em> la séance.
 *
 * <p>Un seul enregistrement par athlète et par jour : la saisie est idempotente, on corrige son
 * check-in du matin sans empiler les doublons. C'est aussi ce qui permet de la proposer sans
 * risque à chaque ouverture de l'app.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WellnessCheckInService {

    private final WellnessCheckInRepository repository;
    private final AthleteRepository athleteRepository;

    /** Check-in d'un jour donné, ou {@code null} si l'athlète n'a rien déclaré ce jour-là. */
    public WellnessCheckInResponse forDate(UUID athleteId, LocalDate date) {
        return repository.findByAthleteIdAndCheckDate(athleteId, date)
                .map(WellnessCheckInResponse::from)
                .orElse(null);
    }

    /**
     * Heure habituelle de séance déclarée par l'athlète (ou {@code null}). Elle ne sert qu'à
     * caler la relance de ressenti : c'est un réglage de notification, pas une donnée
     * d'entraînement — aucune séance n'est planifiée à une heure.
     */
    public Integer usualSessionHour(UUID athleteId) {
        return athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."))
                .getUsualSessionHour();
    }

    /** Règle l'heure habituelle de séance ({@code null} = revenir au défaut du serveur). */
    @Transactional
    public Integer setUsualSessionHour(UUID athleteId, Integer hour) {
        var athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        athlete.setUsualSessionHour(hour != null && hour >= 0 && hour <= 23 ? hour : null);
        return athlete.getUsualSessionHour();
    }

    /** Crée ou met à jour le check-in du jour (upsert sur la contrainte athlète + date). */
    @Transactional
    public WellnessCheckInResponse save(UUID athleteId, WellnessCheckInRequest req) {
        LocalDate date = req.checkDate() != null ? req.checkDate() : LocalDate.now();
        WellnessCheckIn checkIn = repository.findByAthleteIdAndCheckDate(athleteId, date)
                .orElseGet(() -> {
                    WellnessCheckIn fresh = new WellnessCheckIn();
                    fresh.setAthlete(athleteRepository.findById(athleteId)
                            .orElseThrow(() -> new NotFoundException("Athlète introuvable.")));
                    fresh.setCheckDate(date);
                    return fresh;
                });
        checkIn.setSleep(req.sleep());
        checkIn.setFatigue(req.fatigue());
        checkIn.setPain(req.pain());
        return WellnessCheckInResponse.from(repository.save(checkIn));
    }
}
