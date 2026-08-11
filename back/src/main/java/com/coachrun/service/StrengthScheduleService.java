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
    private final com.coachrun.security.HealthDataConsentValidator consentValidator;
    private final StrengthSessionService strengthSessionService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final ClockService clock;

    @Transactional
    public ScheduledStrengthResponse schedule(UUID clubId, UUID athleteId, UUID sessionId,
                                              LocalDate date, FieldsPreset preset) {
        return schedule(clubId, athleteId, sessionId, date, preset, null);
    }

    /** Planifie une séance de force en la rattachant à un plan ({@code planId}) pour le suivi. */
    @Transactional
    public ScheduledStrengthResponse schedule(UUID clubId, UUID athleteId, UUID sessionId,
                                              LocalDate date, FieldsPreset preset, UUID planId) {
        return schedule(clubId, athleteId, sessionId, date, preset, planId, 0d);
    }

    /**
     * Planifie une séance de force en appliquant un ajustement de charge en pourcentage
     * (progression hebdomadaire d'un cycle : +2,5 % en semaine 2, −40 % en semaine de décharge…).
     *
     * <p>L'ajustement s'applique aux <strong>deux bornes</strong> de chaque fourchette : une
     * prescription reste une fourchette min–max, jamais une valeur sèche.</p>
     */
    @Transactional
    public ScheduledStrengthResponse schedule(UUID clubId, UUID athleteId, UUID sessionId,
                                              LocalDate date, FieldsPreset preset, UUID planId,
                                              Double chargePctAdjustment) {
        return schedule(clubId, athleteId, sessionId, date, preset, planId, chargePctAdjustment, true);
    }

    /**
     * Idem, en disant si l'athlète doit être averti — {@code false} sur la génération en lot
     * d'une attribution de plan, qui émet une notification unique pour tout le programme.
     */
    @Transactional
    public ScheduledStrengthResponse schedule(UUID clubId, UUID athleteId, UUID sessionId,
                                              LocalDate date, FieldsPreset preset, UUID planId,
                                              Double chargePctAdjustment, boolean notifyAthlete) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        StrengthSessionResponse session = strengthSessionService.get(clubId, sessionId);

        double adjustment = chargePctAdjustment == null ? 0d : chargePctAdjustment;
        StrengthStructure structure = session.structure();
        CalculatedStrengthResponse calc;
        if (adjustment != 0d) {
            structure = scaleCharges(structure, 1 + adjustment / 100d);
            calc = strengthSessionService.previewForAthlete(clubId, athleteId, structure);
        } else {
            calc = strengthSessionService.calculateForAthlete(clubId, athleteId, sessionId);
        }

        ScheduledStrengthSession ss = new ScheduledStrengthSession();
        ss.setClub(athlete.getClub());
        ss.setAthlete(athlete);
        ss.setSourceSessionId(sessionId);
        ss.setPlanId(planId);
        ss.setTitle(session.name());
        ss.setSessionSnapshot(writeJson(structure));
        ss.setCalculatedCharges(writeJson(calc));
        ss.setRequiredFields((preset != null ? preset : FieldsPreset.DEBUTANT).json());
        ss.setScheduledDate(date);
        ScheduledStrengthSession saved = scheduledRepository.save(ss);
        if (notifyAthlete) {
            notificationService.notifyStrengthPlanned(athlete, saved.getTitle(), date);
        }
        return ScheduledStrengthResponse.from(saved, summarize(calc));
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

    /**
     * Déplacement par le coach (glisser-déposer du calendrier) : contrairement au déplacement
     * athlète, il ne marque pas {@code movedByAthlete} et ne mémorise pas de date d'origine —
     * c'est la prescription elle-même qui change de jour.
     */
    @Transactional
    public ScheduledStrengthResponse moveByCoach(UUID clubId, UUID athleteId, UUID scheduledId, LocalDate date) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        LocalDate before = ss.getScheduledDate();
        ss.setScheduledDate(date);
        if (!date.equals(before)) {
            notifyCalendarChange(ss, date, false);
        }
        return ScheduledStrengthResponse.from(ss);
    }

    /**
     * Marque le retour de l'athlète comme traité (file « retours à traiter »). N'altère ni la
     * séance ni le retour : c'est un accusé de lecture côté coach.
     */
    @Transactional
    public ScheduledStrengthResponse markFeedbackReviewed(UUID clubId, UUID athleteId,
                                                          UUID scheduledId, boolean reviewed) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        ss.setCoachReviewedAt(reviewed ? Instant.now() : null);
        return ScheduledStrengthResponse.from(ss);
    }

    /** Déprogramme une séance de force du calendrier de l'athlète. */
    @Transactional
    public void delete(UUID clubId, UUID athleteId, UUID scheduledId) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        notifyCalendarChange(ss, ss.getScheduledDate(), true);
        scheduledRepository.delete(ss);
    }

    /**
     * Prévient l'athlète qu'une de ses séances de force a bougé — ou disparu.
     *
     * <p>Planifier prévenait déjà ; déplacer et déprogrammer, non. L'athlète se présentait en
     * salle pour une séance retirée depuis, ou manquait celle qu'on avait avancée. Deux gardes,
     * les mêmes que pour la course : on n'annonce ni une séance <b>déjà faite</b> — le retour est
     * saisi, la modification est du ménage de calendrier — ni une séance <b>passée</b>.</p>
     *
     * <p>La date jugée est celle sur laquelle la séance <b>atterrit</b> (sa date d'origine pour
     * une déprogrammation) : c'est la seule qui dit si l'athlète a encore quelque chose à faire.
     * Reculer une séance dans le passé ne l'intéresse pas ; en ramener une dans sa semaine, si.</p>
     */
    private void notifyCalendarChange(ScheduledStrengthSession ss, LocalDate date, boolean cancelled) {
        if (ss.isCompleted() || date.isBefore(clock.today())) {
            return;
        }
        notificationService.notifyStrengthChanged(ss.getAthlete(), ss.getTitle(), date, cancelled);
    }

    private ScheduledStrengthSession require(UUID clubId, UUID athleteId, UUID scheduledId) {
        return scheduledRepository.findByIdAndClubIdAndAthleteId(scheduledId, clubId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable."));
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
        // RPE et commentaire relèvent de l'exécution du contrat ; fatigue et douleur sont des
        // données de l'article 9 et tombent si le consentement n'est pas actif.
        ss.setSessionFatigue(consentValidator.keepIfAllowed(ss.getAthlete(), req.fatigue()));
        ss.setSessionPain(consentValidator.keepIfAllowed(ss.getAthlete(), req.pain()));
        ss.setSessionComment(req.comment());
        notificationService.notifyStrengthFeedback(ss);
        notificationService.notifyPainAlert(ss.getAthlete(), ss.getSessionPain());
        return ScheduledStrengthResponse.from(ss);
    }

    // --- Helpers --------------------------------------------------------------

    /**
     * Résumé lisible des charges obtenues pour cet athlète : nombre d'exercices et fourchette du
     * premier exercice dont la charge est calculable. Sert le retour immédiat au coach après une
     * planification (CdC §8) — « séance planifiée » seul ne dit pas ce que l'athlète va soulever.
     */
    private String summarize(CalculatedStrengthResponse calc) {
        if (calc == null || calc.blocks().isEmpty()) {
            return null;
        }
        int count = 0;
        String first = null;
        for (var block : calc.blocks()) {
            for (var ex : block.exercises()) {
                count++;
                if (first == null && ex.charge() != null && ex.charge().computable()
                        && ex.charge().kgMin() != null && ex.charge().kgMax() != null) {
                    first = ex.item().exerciseName() + " " + trim(ex.charge().kgMin())
                            + "–" + trim(ex.charge().kgMax()) + " kg";
                }
            }
        }
        if (count == 0) {
            return null;
        }
        String exercises = count + " exercice" + (count > 1 ? "s" : "");
        return first == null ? exercises : exercises + " · " + first;
    }

    /** 72.0 → « 72 » ; 72.5 → « 72,5 » (une charge au centième n'a aucun sens en salle). */
    private String trim(double kg) {
        double rounded = Math.round(kg * 10d) / 10d;
        return rounded == Math.floor(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded).replace('.', ',');
    }

    /**
     * Applique un facteur multiplicatif aux charges prescrites (kg fixes et % du 1RM), en
     * conservant l'intégralité du reste de la prescription (reps, effort, tempo, repos).
     * Les deux bornes sont mises à l'échelle : la fourchette reste une fourchette.
     */
    private StrengthStructure scaleCharges(StrengthStructure structure, double factor) {
        if (structure == null || structure.blocks().isEmpty()) {
            return structure == null ? StrengthStructure.empty() : structure;
        }
        return new StrengthStructure(structure.blocks().stream()
                .map(b -> new com.coachrun.dto.strength.StrengthBlock(
                        b.id(), b.blockType(), b.format(), b.durationSec(), b.rounds(),
                        b.workSec(), b.restSec(),
                        b.exercises().stream().map(ex -> scaleItem(ex, factor)).toList()))
                .toList());
    }

    private com.coachrun.dto.strength.StrengthExerciseItem scaleItem(
            com.coachrun.dto.strength.StrengthExerciseItem ex, double factor) {
        var p = ex.prescription();
        if (p == null) {
            return ex;
        }
        var scaled = new com.coachrun.dto.strength.StrengthPrescription(
                p.chargeRefType(),
                scale(p.chargeKgMin(), factor), scale(p.chargeKgMax(), factor),
                scale(p.chargePctRmMin(), factor), scale(p.chargePctRmMax(), factor),
                p.effortRefType(), p.rpeMin(), p.rpeMax(), p.rirMin(), p.rirMax(),
                p.sets(), p.repsFixed(), p.repsMin(), p.repsMax(), p.durationSec(),
                p.plyoContacts(), p.tempo(), p.restSecMin(), p.restSecMax(), p.maxPainAllowed());
        return new com.coachrun.dto.strength.StrengthExerciseItem(
                ex.exerciseId(), ex.exerciseName(), ex.setType(), scaled, ex.setConfig(), ex.coachNotes());
    }

    /** Arrondi au dixième : un %RM ou une charge au centième n'a aucun sens en salle. */
    private Double scale(Double value, double factor) {
        return value == null ? null : Math.round(value * factor * 10d) / 10d;
    }

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
