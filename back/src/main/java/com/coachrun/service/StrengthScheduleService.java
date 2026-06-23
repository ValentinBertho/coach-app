package com.coachrun.service;

import com.coachrun.dto.request.StrengthFeedbackRequest;
import com.coachrun.dto.response.CalculatedStrengthResponse;
import com.coachrun.dto.response.ScheduledStrengthResponse;
import com.coachrun.dto.response.StrengthPrescriptionResponse;
import com.coachrun.dto.response.StrengthSessionResponse;
import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.enums.FieldsPreset;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calendrier de force : assignation d'une séance de bibliothèque à un athlète (snapshot figé +
 * charges calculées + champs adaptatifs), vues coach/athlète, déplacement et retour de séance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthScheduleService {

    private final ScheduledStrengthSessionRepository scheduledRepository;
    private final AthleteRepository athleteRepository;
    private final StrengthSessionService strengthSessionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScheduledStrengthResponse schedule(UUID clubId, UUID athleteId, UUID sessionId,
                                              LocalDate date, FieldsPreset preset) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        StrengthSessionResponse session = strengthSessionService.get(clubId, sessionId);
        CalculatedStrengthResponse calc = strengthSessionService.calculateForAthlete(clubId, athleteId, sessionId);

        ScheduledStrengthSession ss = new ScheduledStrengthSession();
        ss.setClub(athlete.getClub());
        ss.setAthlete(athlete);
        ss.setSourceSessionId(sessionId);
        ss.setTitle(session.name());
        ss.setSessionSnapshot(writeJson(session.structure()));
        ss.setCalculatedCharges(writeJson(calc));
        ss.setRequiredFields((preset != null ? preset : FieldsPreset.DEBUTANT).json());
        ss.setScheduledDate(date);
        return ScheduledStrengthResponse.from(scheduledRepository.save(ss));
    }

    public List<ScheduledStrengthResponse> coachCalendar(UUID clubId, UUID athleteId,
                                                         LocalDate from, LocalDate to) {
        return scheduledRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(clubId, athleteId, from, to)
                .stream().map(ScheduledStrengthResponse::from).toList();
    }

    public StrengthPrescriptionResponse prescription(UUID clubId, UUID scheduledId) {
        return toPrescription(scheduledRepository.findByIdAndClubId(scheduledId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable.")));
    }

    // --- Portail athlète ------------------------------------------------------

    public List<ScheduledStrengthResponse> athleteCalendar(UUID athleteId, LocalDate from, LocalDate to) {
        return scheduledRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(athleteId, from, to)
                .stream().map(ScheduledStrengthResponse::from).toList();
    }

    public StrengthPrescriptionResponse prescriptionForAthlete(UUID athleteId, UUID scheduledId) {
        return toPrescription(scheduledRepository.findByIdAndAthleteId(scheduledId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable.")));
    }

    @Transactional
    public ScheduledStrengthResponse moveByAthlete(UUID athleteId, UUID scheduledId, LocalDate date) {
        ScheduledStrengthSession ss = scheduledRepository.findByIdAndAthleteId(scheduledId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable."));
        if (ss.getOriginalDate() == null) {
            ss.setOriginalDate(ss.getScheduledDate());
        }
        ss.setScheduledDate(date);
        ss.setMovedByAthlete(true);
        return ScheduledStrengthResponse.from(ss);
    }

    @Transactional
    public ScheduledStrengthResponse submitFeedback(UUID athleteId, UUID scheduledId,
                                                    StrengthFeedbackRequest req) {
        ScheduledStrengthSession ss = scheduledRepository.findByIdAndAthleteId(scheduledId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable."));
        if (Boolean.TRUE.equals(req.completed())) {
            ss.setCompleted(true);
            ss.setCompletedAt(Instant.now());
        }
        ss.setSessionRpe(req.sessionRpe());
        ss.setSessionFatigue(req.fatigue());
        ss.setSessionPain(req.pain());
        ss.setSessionComment(req.comment());
        return ScheduledStrengthResponse.from(ss);
    }

    // --- Helpers --------------------------------------------------------------

    private StrengthPrescriptionResponse toPrescription(ScheduledStrengthSession ss) {
        StrengthStructure snapshot = readJson(ss.getSessionSnapshot(), StrengthStructure.class);
        CalculatedStrengthResponse calc = readJson(ss.getCalculatedCharges(), CalculatedStrengthResponse.class);
        JsonNode required = readTree(ss.getRequiredFields());
        return new StrengthPrescriptionResponse(
                snapshot == null ? StrengthStructure.empty() : snapshot, calc, required);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation impossible.", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
