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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthScheduleService {

    /**
     * La progression de force, injectée paresseusement : elle relit la séance close par ce service
     * même. Sans elle, la suggestion « +2,5 kg » restait un affichage sur une séance passée.
     */
    private final org.springframework.beans.factory.ObjectProvider<StrengthProgressionService> progression;

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

    /**
     * Le « vu 👏 » du coach sur un débrief de renforcement — même geste que sur une séance de
     * course, et pour la même raison : la file du coach est unifiée, la reconnaissance doit
     * l'être aussi.
     *
     * <p>Idempotent quant à la notification : seule la première fois se dit à l'athlète.</p>
     */
    @Transactional
    public ScheduledStrengthResponse acknowledge(UUID clubId, UUID athleteId, UUID scheduledId) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        boolean first = ss.getCoachAcknowledgedAt() == null;
        Instant now = Instant.now();
        ss.setCoachAcknowledgedAt(now);
        if (ss.getCoachReviewedAt() == null) {
            ss.setCoachReviewedAt(now);
        }
        if (first) {
            notificationService.notifyCoachAcknowledgement(ss.getAthlete(), ss.getTitle(),
                    "/athlete/strength");
        }
        return ScheduledStrengthResponse.from(ss);
    }

    /**
     * Décale la charge d'<b>un</b> exercice d'une séance déjà planifiée, en kilos.
     *
     * <h2>Pourquoi cette méthode existe</h2>
     *
     * <p>{@link com.coachrun.engine.ProgressionEngine} sait depuis toujours dire « toutes les
     * séries réussies, RIR au-dessus de la cible, aucune douleur : +2,5 kg ». Cette suggestion
     * s'affichait dans l'écran d'une séance <b>passée</b> et n'avait aucun effet : pour l'appliquer,
     * le coach rouvrait la bibliothèque et retapait la charge. Elle était donc calculée, puis
     * perdue.</p>
     *
     * <p>Le décalage porte sur la séance planifiée, pas sur la séance de bibliothèque : c'est
     * l'athlète qui progresse, pas le modèle. Et il ne s'applique qu'aux prescriptions exprimées en
     * kilos — pour une prescription en %RM, la charge suit déjà le 1RM, et la bonne correction est
     * un test, pas un décalage.</p>
     *
     * @param deltaKg décalage signé, en kilos (peut être négatif : une régression est une décision
     *                de progression comme une autre)
     */
    @Transactional
    public ScheduledStrengthResponse shiftExerciseCharge(UUID clubId, UUID athleteId, UUID scheduledId,
                                                         UUID exerciseId, double deltaKg) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        if (ss.isCompleted()) {
            throw new com.coachrun.exception.ConflictException(
                    "Cette séance est déjà faite : sa charge ne se modifie plus.");
        }
        StrengthStructure snapshot = readJson(ss.getSessionSnapshot(), StrengthStructure.class);
        if (snapshot == null) {
            throw new NotFoundException("Cette séance n'a pas de prescription.");
        }
        StrengthStructure shifted = shiftCharge(snapshot, exerciseId, deltaKg);
        CalculatedStrengthResponse calc = strengthSessionService.previewForAthlete(clubId, athleteId, shifted);

        ss.setSessionSnapshot(writeJson(shifted));
        ss.setCalculatedCharges(writeJson(calc));
        // Une charge qui bouge est un changement de contenu, pas un déplacement de calendrier :
        // l'athlète recevait « Séance de renforcement déplacée » pour une séance restée sur son jour.
        notificationService.notifyStrengthUpdated(ss.getAthlete(), ss.getTitle(), ss.getScheduledDate());
        return ScheduledStrengthResponse.from(ss, summarize(calc));
    }

    /** Applique le décalage aux deux bornes de la fourchette de l'exercice visé, et à lui seul. */
    private StrengthStructure shiftCharge(StrengthStructure structure, UUID exerciseId, double deltaKg) {
        return new StrengthStructure(structure.blocks().stream()
                .map(b -> b.withExercises(b.exercises().stream()
                        .map(ex -> exerciseId.equals(ex.exerciseId()) ? shiftItem(ex, deltaKg) : ex)
                        .toList()))
                .toList());
    }

    private com.coachrun.dto.strength.StrengthExerciseItem shiftItem(
            com.coachrun.dto.strength.StrengthExerciseItem ex, double deltaKg) {
        var p = ex.prescription();
        if (p == null || p.chargeKgMin() == null) {
            // Prescription en %RM ou sans charge : rien à décaler ici, et inventer une charge en
            // kilos par-dessus un %RM ferait diverger la séance de son référentiel.
            return ex;
        }
        var shifted = p.withCharges(
                floorAt(p.chargeKgMin() + deltaKg), floorAt(p.chargeKgMax() == null ? null : p.chargeKgMax() + deltaKg),
                p.chargePctRmMin(), p.chargePctRmMax());
        return ex.withPrescription(shifted);
    }

    /** Une charge ne descend pas sous zéro — une régression trop forte devient « à vide ». */
    private Double floorAt(Double kg) {
        return kg == null ? null : Math.max(0d, Math.round(kg * 10d) / 10d);
    }

    /**
     * Réécrit le contenu d'une séance de renforcement <b>déjà posée</b> au calendrier, sans
     * repasser par la bibliothèque.
     *
     * <h2>Pourquoi</h2>
     *
     * <p>Une séance de force planifiée était figée : le snapshot ne s'écrivait qu'à
     * l'assignation. Changer une série, retirer un exercice ou corriger une charge supposait donc
     * de déprogrammer la séance, retoucher le modèle de bibliothèque — qui sert d'autres athlètes
     * — puis replanifier ; ou, plus souvent, de créer <b>une séance de plus</b> pour la même
     * journée. Le coach accumulait « Full body 3 », « Full body 3 bis », « Full body 3 (Marc) ».
     * C'est l'exact pendant de « Adapter » côté course, qui existait depuis toujours.</p>
     *
     * <p>L'édition porte sur la séance de l'athlète, jamais sur le modèle : adapter pour
     * quelqu'un ne doit rien changer chez les autres. Les charges sont recalculées avec
     * <b>son</b> profil 1RM — une prescription en %RM ne veut rien dire hors de la personne qui
     * la soulève.</p>
     */
    @Transactional
    public StrengthPrescriptionResponse updateStructure(UUID clubId, UUID athleteId, UUID scheduledId,
                                                        StrengthStructure structure) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        // Même garde que sur le décalage de charge : réécrire une séance déjà faite fausserait la
        // comparaison prévu / réalisé, l'athlète ayant soulevé ce qui était prescrit ce jour-là.
        if (ss.isCompleted()) {
            throw new com.coachrun.exception.ConflictException(
                    "Cette séance est déjà faite : son contenu ne se modifie plus.");
        }
        StrengthStructure safe = structure == null ? StrengthStructure.empty() : structure;
        CalculatedStrengthResponse calc = strengthSessionService.previewForAthlete(clubId, athleteId, safe);

        boolean wasEmpty = isEmpty(readJson(ss.getSessionSnapshot(), StrengthStructure.class));
        String before = ss.getSessionSnapshot();
        ss.setSessionSnapshot(writeJson(safe));
        ss.setCalculatedCharges(writeJson(calc));
        notifyContentChanged(ss, before, wasEmpty);
        return toPrescription(ss);
    }

    /**
     * Prévient l'athlète que le contenu de sa séance a changé.
     *
     * <p>Trois gardes, les mêmes que côté course : la séance doit être encore à faire, ne pas
     * être passée, et la structure doit avoir <b>réellement</b> changé — ouvrir l'éditeur puis
     * enregistrer sans rien toucher ne notifie personne, sans quoi le canal se dévalue seul.</p>
     *
     * <p>Une séance jusque-là vide qui reçoit son contenu est une séance <b>nouvelle</b> pour
     * l'athlète : c'est le cas de la séance posée à blanc sur le calendrier puis construite. On
     * l'annonce comme telle plutôt que comme la modification d'une séance qu'il n'a jamais vue.</p>
     */
    private void notifyContentChanged(ScheduledStrengthSession ss, String previousSnapshot, boolean wasEmpty) {
        if (ss.isCompleted() || ss.getScheduledDate().isBefore(clock.today())
                || java.util.Objects.equals(previousSnapshot, ss.getSessionSnapshot())) {
            return;
        }
        if (wasEmpty) {
            notificationService.notifyStrengthPlanned(ss.getAthlete(), ss.getTitle(), ss.getScheduledDate());
        } else {
            notificationService.notifyStrengthUpdated(ss.getAthlete(), ss.getTitle(), ss.getScheduledDate());
        }
    }

    private boolean isEmpty(StrengthStructure structure) {
        return structure == null || structure.blocks().isEmpty()
                || structure.blocks().stream().allMatch(b -> b.exercises().isEmpty());
    }

    /**
     * Renomme une séance de force planifiée.
     *
     * <p>Volontairement distinct de l'édition de structure : « Full body » devient « Full body —
     * bas du corps » sans que la prescription figée soit relue ni réécrite.</p>
     */
    @Transactional
    public ScheduledStrengthResponse rename(UUID clubId, UUID athleteId, UUID scheduledId, String title) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        ss.setTitle(title.trim());
        return ScheduledStrengthResponse.from(ss);
    }

    /**
     * Recopie une séance de force planifiée sur une autre date, chez le <b>même</b> athlète.
     *
     * <p>C'est le snapshot qui est recopié, pas le modèle de bibliothèque. La nuance fait tout :
     * le copier-coller du calendrier repassait par {@code sourceSessionId}, si bien qu'une séance
     * adaptée pour l'athlète — ou construite directement sur son calendrier — revenait dans sa
     * version d'origine, ou ne se copiait pas du tout faute de modèle. On duplique ici ce qui est
     * affiché, charges comprises : elles ont été calculées pour lui.</p>
     */
    @Transactional
    public ScheduledStrengthResponse copyToDate(UUID clubId, UUID athleteId, UUID scheduledId, LocalDate date) {
        ScheduledStrengthSession source = require(clubId, athleteId, scheduledId);
        return ScheduledStrengthResponse.from(saveCopy(source, source.getAthlete(), date,
                source.getSessionSnapshot(), source.getCalculatedCharges()));
    }

    /**
     * Recopie chez un <b>autre</b> athlète une séance de force planifiée (copier-coller de la vue
     * groupe).
     *
     * <p>La structure du coach est reprise telle quelle, mais les charges sont <b>recalculées</b>
     * avec le profil 1RM de celui qui les recevra : donner à Julie les kilos de Marc serait faux,
     * et faux silencieusement.</p>
     */
    @Transactional
    public ScheduledStrengthResponse copyToAthlete(UUID clubId, UUID targetAthleteId, UUID sourceScheduledId,
                                                   LocalDate date) {
        ScheduledStrengthSession source = scheduledRepository.findByIdAndClubId(sourceScheduledId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance de force à copier introuvable."));
        if (source.getAthlete().getId().equals(targetAthleteId)) {
            return copyToDate(clubId, targetAthleteId, sourceScheduledId, date);
        }
        Athlete target = athleteRepository.findByIdAndClubMembership(targetAthleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        StrengthStructure snapshot = readJson(source.getSessionSnapshot(), StrengthStructure.class);
        StrengthStructure safe = snapshot == null ? StrengthStructure.empty() : snapshot;
        CalculatedStrengthResponse calc =
                strengthSessionService.previewForAthlete(clubId, targetAthleteId, safe);
        return ScheduledStrengthResponse.from(
                saveCopy(source, target, date, writeJson(safe), writeJson(calc)), summarize(calc));
    }

    /** Écrit la copie et prévient l'athlète qui la reçoit : pour lui, c'est une séance de plus. */
    private ScheduledStrengthSession saveCopy(ScheduledStrengthSession source, Athlete target,
                                              LocalDate date, String snapshot, String charges) {
        ScheduledStrengthSession copy = new ScheduledStrengthSession();
        copy.setClub(target.getClub());
        copy.setAthlete(target);
        // La provenance suit la copie quand elle existe, mais ne la conditionne plus.
        copy.setSourceSessionId(source.getSourceSessionId());
        copy.setTitle(source.getTitle());
        copy.setSessionSnapshot(snapshot);
        copy.setCalculatedCharges(charges);
        copy.setRequiredFields(source.getRequiredFields());
        copy.setScheduledDate(date);
        ScheduledStrengthSession saved = scheduledRepository.save(copy);
        if (!date.isBefore(clock.today())) {
            notificationService.notifyStrengthPlanned(target, saved.getTitle(), date);
        }
        return saved;
    }

    /**
     * Pose une séance de renforcement <b>vierge</b> sur un jour, sans modèle de bibliothèque.
     *
     * <p>Le chemin court de la prépa physique, celui que la course avait déjà : on pose la séance
     * là où elle doit avoir lieu, puis on la remplit. Rien n'est annoncé à l'athlète ici — une
     * séance encore vide n'est pas une nouvelle ; c'est le premier enregistrement de son contenu
     * qui l'annonce (cf. {@link #notifyContentChanged}).</p>
     */
    @Transactional
    public ScheduledStrengthResponse createAdHoc(UUID clubId, UUID athleteId, LocalDate date,
                                                 String title, FieldsPreset preset) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        ScheduledStrengthSession ss = new ScheduledStrengthSession();
        ss.setClub(athlete.getClub());
        ss.setAthlete(athlete);
        ss.setTitle(StringUtils.hasText(title) ? title.trim() : "Séance de renforcement");
        ss.setSessionSnapshot(writeJson(StrengthStructure.empty()));
        ss.setCalculatedCharges(writeJson(new CalculatedStrengthResponse(List.of())));
        ss.setRequiredFields((preset != null ? preset : FieldsPreset.DEBUTANT).json());
        ss.setScheduledDate(date);
        return ScheduledStrengthResponse.from(scheduledRepository.save(ss));
    }

    /**
     * Verse dans la bibliothèque une séance de renforcement construite au calendrier.
     *
     * <p>Sans ce geste, une séance improvisée pour un athlète puis affinée n'avait pas d'issue :
     * la garder supposait de la reconstruire bloc par bloc. On recopie sa structure dans un
     * nouveau modèle du club — sans les commentaires d'exercice, qui ont été écrits <b>pour cette
     * personne</b> et n'ont rien à faire dans une consigne de club (même parti pris que côté
     * course).</p>
     */
    @Transactional
    public StrengthSessionResponse saveAsLibrarySession(UUID clubId, UUID athleteId, UUID scheduledId,
                                                        String name, String notes, UUID categoryId) {
        ScheduledStrengthSession ss = require(clubId, athleteId, scheduledId);
        StrengthStructure snapshot = readJson(ss.getSessionSnapshot(), StrengthStructure.class);
        return strengthSessionService.createFromStructure(
                clubId, name, notes, categoryId, withoutCoachNotes(snapshot));
    }

    /** La même séance, débarrassée des mots adressés à un athlète en particulier. */
    private StrengthStructure withoutCoachNotes(StrengthStructure structure) {
        if (structure == null) {
            return StrengthStructure.empty();
        }
        return new StrengthStructure(structure.blocks().stream()
                .map(b -> b.withExercises(b.exercises().stream()
                        .map(ex -> new com.coachrun.dto.strength.StrengthExerciseItem(
                                ex.exerciseId(), ex.exerciseName(), ex.setType(), ex.prescription(),
                                ex.setConfig(), null))
                        .toList()))
                .toList());
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
        proposeProgression(ss);
        return ScheduledStrengthResponse.from(ss);
    }

    /**
     * Transforme les suggestions de progression de cette séance en propositions sur la suivante.
     *
     * <p>Aucune charge n'est modifiée ici : la proposition attend la validation du coach. Et un
     * échec de calcul ne doit jamais faire échouer le débrief de l'athlète — il a fait sa séance,
     * son retour doit être enregistré quoi qu'il arrive.</p>
     */
    private void proposeProgression(ScheduledStrengthSession ss) {
        if (!ss.isCompleted()) {
            return;
        }
        try {
            StrengthProgressionService service = progression.getIfAvailable();
            if (service != null) {
                service.proposeAfter(ss);
            }
        } catch (RuntimeException e) {
            log.warn("Progression de force impossible après la séance {} : {}", ss.getId(), e.getMessage());
        }
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
                .map(b -> b.withExercises(b.exercises().stream().map(ex -> scaleItem(ex, factor)).toList()))
                .toList());
    }

    private com.coachrun.dto.strength.StrengthExerciseItem scaleItem(
            com.coachrun.dto.strength.StrengthExerciseItem ex, double factor) {
        var p = ex.prescription();
        if (p == null) {
            return ex;
        }
        var scaled = p.withCharges(
                scale(p.chargeKgMin(), factor), scale(p.chargeKgMax(), factor),
                scale(p.chargePctRmMin(), factor), scale(p.chargePctRmMax(), factor));
        return ex.withPrescription(scaled);
    }

    /** Arrondi au dixième : un %RM ou une charge au centième n'a aucun sens en salle. */
    private Double scale(Double value, double factor) {
        return value == null ? null : Math.round(value * factor * 10d) / 10d;
    }

    private StrengthPrescriptionResponse toPrescription(ScheduledStrengthSession ss) {
        StrengthStructure snapshot = readJson(ss.getSessionSnapshot(), StrengthStructure.class);
        CalculatedStrengthResponse calc = readJson(ss.getCalculatedCharges(), CalculatedStrengthResponse.class);
        JsonNode required = readTree(ss.getRequiredFields());
        return new StrengthPrescriptionResponse(ss.getTitle(),
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
