package com.coachrun.service;

import com.coachrun.dto.request.StrengthCycleRequest;
import com.coachrun.dto.response.StrengthCycleResponse;
import com.coachrun.dto.strength.CycleStructure;
import com.coachrun.entity.StrengthCycle;
import com.coachrun.entity.enums.FieldsPreset;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.StrengthCycleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cycles de préparation physique (cf. DARI Lab §7.6) : CRUD et assignation au calendrier d'un
 * athlète (planification des séances semaine par semaine).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthCycleService {

    private final StrengthCycleRepository cycleRepository;
    private final ClubRepository clubRepository;
    private final StrengthScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    public List<StrengthCycleResponse> list(UUID clubId) {
        return cycleRepository.findByClubIdOrderByName(clubId).stream()
                .map(c -> StrengthCycleResponse.of(c, read(c.getStructureJson())))
                .toList();
    }

    public StrengthCycleResponse get(UUID clubId, UUID id) {
        StrengthCycle c = require(clubId, id);
        return StrengthCycleResponse.of(c, read(c.getStructureJson()));
    }

    @Transactional
    public StrengthCycleResponse create(UUID clubId, StrengthCycleRequest req) {
        StrengthCycle c = new StrengthCycle();
        c.setClub(clubRepository.getReferenceById(clubId));
        apply(c, req);
        return StrengthCycleResponse.of(cycleRepository.save(c), structureOf(req));
    }

    @Transactional
    public StrengthCycleResponse update(UUID clubId, UUID id, StrengthCycleRequest req) {
        StrengthCycle c = require(clubId, id);
        apply(c, req);
        return StrengthCycleResponse.of(c, structureOf(req));
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        cycleRepository.delete(require(clubId, id));
    }

    /** Planifie toutes les séances du cycle pour un athlète à partir d'une date de départ. */
    @Transactional
    public int assign(UUID clubId, UUID athleteId, UUID cycleId, LocalDate startDate) {
        StrengthCycle c = require(clubId, cycleId);
        CycleStructure structure = read(c.getStructureJson());
        int scheduled = 0;
        for (CycleStructure.CycleWeek week : structure.weeks()) {
            int dayOffset = 0;
            for (UUID sessionId : week.sessionIds()) {
                LocalDate date = startDate.plusDays((long) (week.week() - 1) * 7 + dayOffset);
                scheduleService.schedule(clubId, athleteId, sessionId, date, FieldsPreset.DEBUTANT);
                scheduled++;
                dayOffset += 2;
            }
        }
        log.info("Cycle {} assigné à l'athlète {} ({} séances planifiées)", cycleId, athleteId, scheduled);
        return scheduled;
    }

    // --- Helpers --------------------------------------------------------------

    private void apply(StrengthCycle c, StrengthCycleRequest req) {
        c.setName(req.name().trim());
        c.setWeeks(req.weeks());
        c.setObjective(req.objective());
        c.setDescription(req.description());
        c.setStructureJson(write(structureOf(req)));
    }

    private CycleStructure structureOf(StrengthCycleRequest req) {
        return req.structure() == null ? new CycleStructure(List.of()) : req.structure();
    }

    private StrengthCycle require(UUID clubId, UUID id) {
        return cycleRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Cycle introuvable."));
    }

    private String write(CycleStructure s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation du cycle impossible.", e);
        }
    }

    private CycleStructure read(String json) {
        if (!StringUtils.hasText(json)) {
            return new CycleStructure(List.of());
        }
        try {
            return objectMapper.readValue(json, CycleStructure.class);
        } catch (Exception e) {
            return new CycleStructure(List.of());
        }
    }
}
