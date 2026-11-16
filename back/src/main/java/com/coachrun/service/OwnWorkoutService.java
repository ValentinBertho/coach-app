package com.coachrun.service;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.response.WorkoutTemplateResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Les séances que l'athlète s'ajoute lui-même.
 *
 * <p>Sans coach, c'est ainsi qu'il construit sa semaine — à la main, ou depuis le catalogue de
 * séances de son espace personnel. Avec un coach, il peut y glisser une sortie de club ou un
 * footing entre amis : la séance porte la marque « ajoutée par l'athlète », le coach la voit
 * dans son calendrier comme les autres, et seul l'athlète peut la retirer. Les séances du coach,
 * elles, restent intouchables (l'athlète ne peut que les déplacer).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnWorkoutService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutService workoutService;
    private final WorkoutTemplateService templateService;

    /** Ajoute une séance construite à la main. Personne n'est notifié : c'est son propre geste. */
    @Transactional
    public WorkoutResponse create(UUID athleteId, WorkoutRequest request) {
        Athlete athlete = requireAthlete(athleteId);
        WorkoutResponse created = workoutService.create(
                athlete.getClub().getId(), athleteId, request, null, false);
        return markAsOwn(created.id());
    }

    /** Le catalogue de séances de l'espace personnel. Fermé à l'athlète suivi : c'est celui du coach. */
    @Transactional(readOnly = true)
    public PageResponse<WorkoutTemplateResponse> catalog(UUID athleteId, String q) {
        Athlete athlete = requirePersonal(athleteId);
        return templateService.list(athlete.getClub().getId(), q,
                PageRequest.of(0, 200, Sort.by("name")));
    }

    /** Pose une séance du catalogue à une date. */
    @Transactional
    public WorkoutResponse schedule(UUID athleteId, UUID templateId, LocalDate date) {
        if (date == null) {
            throw new ConflictException("Choisissez une date.");
        }
        Athlete athlete = requirePersonal(athleteId);
        WorkoutResponse created = templateService.apply(athlete.getClub().getId(), templateId, athleteId, date);
        return markAsOwn(created.id());
    }

    /** Retire une séance que l'athlète a posée lui-même, tant qu'elle n'est pas faite. */
    @Transactional
    public void delete(UUID athleteId, UUID workoutId) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, requireId(athleteId))
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        if (!workout.isCreatedByAthlete()) {
            throw new ForbiddenException("Cette séance a été posée par votre coach : vous pouvez la déplacer, "
                    + "pas la supprimer.");
        }
        if (workout.getStatus() != WorkoutStatus.PLANNED) {
            throw new ConflictException("Une séance déjà faite reste dans l'historique.");
        }
        workoutRepository.delete(workout);
    }

    private WorkoutResponse markAsOwn(UUID workoutId) {
        Workout workout = workoutRepository.findById(workoutId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        workout.setCreatedByAthlete(true);
        log.info("Séance {} ajoutée par l'athlète {}", workoutId, workout.getAthlete().getId());
        return WorkoutResponse.from(workout);
    }

    private Athlete requirePersonal(UUID athleteId) {
        Athlete athlete = requireAthlete(athleteId);
        if (!PersonalSpaceService.isPersonal(athlete)) {
            throw new ForbiddenException("Le catalogue de séances est celui de votre espace personnel. "
                    + "Votre coach construit votre programme.");
        }
        return athlete;
    }

    private Athlete requireAthlete(UUID athleteId) {
        return athleteRepository.findById(requireId(athleteId))
                .orElseThrow(() -> new NotFoundException("Fiche introuvable."));
    }

    private static UUID requireId(UUID athleteId) {
        if (athleteId == null) {
            throw new ForbiddenException("Aucune fiche athlète.");
        }
        return athleteId;
    }
}
