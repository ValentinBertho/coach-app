package com.coachrun.service;

import com.coachrun.dto.request.Athlete1rmRequest;
import com.coachrun.dto.response.Athlete1rmResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Athlete1rmProfile;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.Athlete1rmProfileRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.PpExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Profil 1RM d'un athlète par exercice (base de calcul des charges %RM). Scoping tenant. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Athlete1rmService {

    private final Athlete1rmProfileRepository profileRepository;
    private final AthleteRepository athleteRepository;
    private final PpExerciseRepository exerciseRepository;

    public List<Athlete1rmResponse> list(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        return profileRepository.findByAthleteId(athleteId).stream()
                .map(Athlete1rmResponse::from)
                .toList();
    }

    @Transactional
    public Athlete1rmResponse set(UUID clubId, UUID athleteId, Athlete1rmRequest req) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        if (exerciseRepository.findByIdAndClubId(req.exerciseId(), clubId).isEmpty()) {
            throw new NotFoundException("Exercice introuvable.");
        }
        Athlete1rmProfile profile = profileRepository
                .findByAthleteIdAndExerciseId(athleteId, req.exerciseId())
                .orElseGet(() -> {
                    Athlete1rmProfile p = new Athlete1rmProfile();
                    p.setAthlete(athlete);
                    p.setExerciseId(req.exerciseId());
                    return p;
                });
        profile.setRmKg(BigDecimal.valueOf(req.rmKg()));
        profile.setSource("manual");
        return Athlete1rmResponse.from(profileRepository.save(profile));
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
