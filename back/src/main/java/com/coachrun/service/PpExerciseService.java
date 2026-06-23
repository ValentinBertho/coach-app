package com.coachrun.service;

import com.coachrun.dto.request.PpExerciseRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.PpExerciseResponse;
import com.coachrun.entity.PpExercise;
import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.PpExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.UUID;

/** Bibliothèque d'exercices de préparation physique : CRUD + recherche filtrable, scopé club. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PpExerciseService {

    private final PpExerciseRepository exerciseRepository;
    private final ClubRepository clubRepository;

    public PageResponse<PpExerciseResponse> search(UUID clubId, ExerciseCategory category,
                                                   ExerciseLevel level, MuscleGroup muscle,
                                                   EquipmentType equipment, String query, Pageable pageable) {
        String q = StringUtils.hasText(query) ? query.trim() : "";
        return PageResponse.from(
                exerciseRepository.search(clubId, category, level, muscle, equipment, q, pageable),
                PpExerciseResponse::from);
    }

    public PpExerciseResponse get(UUID clubId, UUID id) {
        return PpExerciseResponse.from(require(clubId, id));
    }

    @Transactional
    public PpExerciseResponse create(UUID clubId, PpExerciseRequest req) {
        PpExercise e = new PpExercise();
        e.setClub(clubRepository.getReferenceById(clubId));
        apply(e, req);
        return PpExerciseResponse.from(exerciseRepository.save(e));
    }

    @Transactional
    public PpExerciseResponse update(UUID clubId, UUID id, PpExerciseRequest req) {
        PpExercise e = require(clubId, id);
        apply(e, req);
        return PpExerciseResponse.from(e);
    }

    @Transactional
    public void archive(UUID clubId, UUID id) {
        require(clubId, id).setArchived(true);
    }

    private void apply(PpExercise e, PpExerciseRequest req) {
        e.setName(req.name().trim());
        e.setCategory(req.category());
        e.setLevel(req.level() != null ? req.level() : ExerciseLevel.INTERMEDIAIRE);
        e.setObjective(req.objective());
        e.setMuscleGroups(req.muscleGroups() == null ? new HashSet<>() : new HashSet<>(req.muscleGroups()));
        e.setEquipment(req.equipment() == null ? new HashSet<>() : new HashSet<>(req.equipment()));
        e.setVideoUrl(req.videoUrl());
        e.setImageUrl(req.imageUrl());
        e.setInstructions(req.instructions());
        e.setTechnicalNotes(req.technicalNotes());
        e.setContraindications(req.contraindications());
        e.setProgressionId(req.progressionId());
        e.setRegressionId(req.regressionId());
    }

    private PpExercise require(UUID clubId, UUID id) {
        return exerciseRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Exercice introuvable."));
    }
}
