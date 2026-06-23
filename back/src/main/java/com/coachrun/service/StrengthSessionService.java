package com.coachrun.service;

import com.coachrun.dto.request.StrengthSessionRequest;
import com.coachrun.dto.request.StrengthStructureRequest;
import com.coachrun.dto.response.CalculatedStrengthResponse;
import com.coachrun.dto.response.CalculatedStrengthResponse.CalculatedExercise;
import com.coachrun.dto.response.CalculatedStrengthResponse.CalculatedStrengthBlock;
import com.coachrun.dto.response.ChargeTarget;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.StrengthSessionResponse;
import com.coachrun.dto.strength.StrengthBlock;
import com.coachrun.dto.strength.StrengthExerciseItem;
import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.engine.StrengthChargeEngine;
import com.coachrun.entity.Athlete1rmProfile;
import com.coachrun.entity.StrengthSession;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.Athlete1rmProfileRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.StrengthSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bibliothèque de séances de force : CRUD + structure (blocs/exercices) + calcul des charges pour
 * un athlète à partir de son profil 1RM. Structure sérialisée en JSON (cf. bibliothèque course).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthSessionService {

    private final StrengthSessionRepository sessionRepository;
    private final Athlete1rmProfileRepository profileRepository;
    private final AthleteRepository athleteRepository;
    private final ClubRepository clubRepository;
    private final StrengthChargeEngine chargeEngine;
    private final ObjectMapper objectMapper;

    public PageResponse<StrengthSessionResponse> search(UUID clubId, String query, Pageable pageable) {
        String q = StringUtils.hasText(query) ? query.trim() : "";
        return PageResponse.from(sessionRepository.search(clubId, q, pageable),
                s -> StrengthSessionResponse.of(s, StrengthStructure.empty()));
    }

    public StrengthSessionResponse get(UUID clubId, UUID id) {
        StrengthSession s = require(clubId, id);
        return StrengthSessionResponse.of(s, readStructure(s.getStructureJson()));
    }

    @Transactional
    public StrengthSessionResponse create(UUID clubId, StrengthSessionRequest req) {
        StrengthSession s = new StrengthSession();
        s.setClub(clubRepository.getReferenceById(clubId));
        applyMeta(s, req);
        return StrengthSessionResponse.of(sessionRepository.save(s), StrengthStructure.empty());
    }

    @Transactional
    public StrengthSessionResponse update(UUID clubId, UUID id, StrengthSessionRequest req) {
        StrengthSession s = require(clubId, id);
        applyMeta(s, req);
        return StrengthSessionResponse.of(s, readStructure(s.getStructureJson()));
    }

    @Transactional
    public void archive(UUID clubId, UUID id) {
        require(clubId, id).setArchived(true);
    }

    @Transactional
    public StrengthSessionResponse putStructure(UUID clubId, UUID id, StrengthStructureRequest req) {
        StrengthSession s = require(clubId, id);
        StrengthStructure structure = req.structure() == null ? StrengthStructure.empty() : req.structure();
        s.setStructureJson(writeStructure(structure));
        return StrengthSessionResponse.of(s, structure);
    }

    /** Calcule les charges cibles (kg) de toute la séance pour un athlète, via son profil 1RM. */
    public CalculatedStrengthResponse calculateForAthlete(UUID clubId, UUID athleteId, UUID sessionId) {
        if (athleteRepository.findByIdAndClubId(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        StrengthSession s = require(clubId, sessionId);
        return calculate(athleteId, readStructure(s.getStructureJson()));
    }

    /** Aperçu live : calcule les charges d'une structure non encore enregistrée (éditeur). */
    public CalculatedStrengthResponse previewForAthlete(UUID clubId, UUID athleteId, StrengthStructure structure) {
        if (athleteRepository.findByIdAndClubId(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        return calculate(athleteId, structure == null ? StrengthStructure.empty() : structure);
    }

    private CalculatedStrengthResponse calculate(UUID athleteId, StrengthStructure structure) {
        Map<UUID, Double> oneRmByExercise = new HashMap<>();
        for (Athlete1rmProfile p : profileRepository.findByAthleteId(athleteId)) {
            oneRmByExercise.put(p.getExerciseId(), p.getRmKg().doubleValue());
        }

        List<CalculatedStrengthBlock> blocks = new ArrayList<>();
        for (StrengthBlock block : structure.blocks()) {
            List<CalculatedExercise> exercises = new ArrayList<>();
            for (StrengthExerciseItem item : block.exercises()) {
                Double oneRm = item.exerciseId() == null ? null : oneRmByExercise.get(item.exerciseId());
                ChargeTarget charge = chargeEngine.resolve(item.prescription(), oneRm);
                exercises.add(new CalculatedExercise(item, charge));
            }
            blocks.add(new CalculatedStrengthBlock(block, exercises));
        }
        return new CalculatedStrengthResponse(blocks);
    }

    // --- Helpers --------------------------------------------------------------

    private void applyMeta(StrengthSession s, StrengthSessionRequest req) {
        s.setName(req.name().trim());
        s.setNotes(req.notes());
        if (req.favorite() != null) {
            s.setFavorite(req.favorite());
        }
    }

    private StrengthSession require(UUID clubId, UUID id) {
        return sessionRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable."));
    }

    private String writeStructure(StrengthStructure structure) {
        try {
            return objectMapper.writeValueAsString(structure);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation de la structure impossible.", e);
        }
    }

    private StrengthStructure readStructure(String json) {
        if (!StringUtils.hasText(json)) {
            return StrengthStructure.empty();
        }
        try {
            return objectMapper.readValue(json, StrengthStructure.class);
        } catch (Exception e) {
            return StrengthStructure.empty();
        }
    }
}
