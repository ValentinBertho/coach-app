package com.coachrun.service;

import com.coachrun.dto.request.GenerateMesocycleRequest;
import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.GroupApplyResponse;
import com.coachrun.dto.response.WorkoutPrescriptionResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.session.PrescribedWorkout;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.MesocycleTemplate;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.MesocycleTemplateRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.security.AthleteAccessValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des séances prescrites : calendrier, CRUD scopé par club (anti-IDOR),
 * étapes structurées et transitions d'état validées.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutService {

    private final WorkoutRepository workoutRepository;
    private final AthleteRepository athleteRepository;
    private final NotificationService notificationService;
    private final com.coachrun.engine.PlannedLoadEngine plannedLoadEngine;
    private final MesocycleTemplateRepository mesocycleTemplateRepository;
    private final TrainingGroupRepository groupRepository;
    private final AthleteAccessValidator accessValidator;
    private final ObjectMapper objectMapper;
    private final com.coachrun.repository.RunDrillRepository runDrillRepository;
    private final SessionCalculatorService sessionCalculatorService;
    private final com.coachrun.security.HealthDataConsentValidator consentValidator;
    private final ClockService clock;

    public List<WorkoutResponse> calendar(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        return workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAscOrderIndexAsc(clubId, athleteId, from, to)
                .stream().map(WorkoutResponse::from).toList();
    }

    /**
     * Réordonne les séances d'un même jour (glisser-déposer intra-jour) : {@code orderIndex} suit
     * la position dans {@code orderedIds}. Les séances du jour non listées sont poussées à la fin.
     */
    @Transactional
    public void reorder(UUID clubId, UUID athleteId, LocalDate date, List<UUID> orderedIds) {
        List<Workout> dayWorkouts = workoutRepository.findByClubIdAndAthleteIdAndScheduledDate(clubId, athleteId, date);
        for (Workout w : dayWorkouts) {
            int idx = orderedIds.indexOf(w.getId());
            w.setOrderIndex(idx >= 0 ? idx : orderedIds.size());
        }
    }

    public WorkoutResponse get(UUID clubId, UUID workoutId) {
        return WorkoutResponse.from(require(clubId, workoutId));
    }

    @Transactional
    public WorkoutResponse create(UUID clubId, UUID athleteId, WorkoutRequest request) {
        return create(clubId, athleteId, request, null);
    }

    /** Création avec rattachement optionnel à un plan ({@code planId}) pour le suivi d'avancement. */
    @Transactional
    public WorkoutResponse create(UUID clubId, UUID athleteId, WorkoutRequest request, UUID planId) {
        return create(clubId, athleteId, request, planId, true);
    }

    /**
     * Création, en disant explicitement si l'athlète doit en être averti.
     *
     * <p>{@code notifyAthlete} vaut {@code false} sur les chemins de <b>génération en lot</b> :
     * l'attribution d'un plan descend jusqu'ici une fois par séance, et notifier à ce niveau
     * produisait une cinquantaine de notifications pour un seul geste du coach. Le lot émet à sa
     * place une notification unique (cf. {@code NotificationService#notifyPlanAssigned}). Le
     * drapeau est porté par un paramètre plutôt que déduit de {@code planId} : poser une séance
     * de plan à l'unité reste une création ordinaire, qui doit se voir.</p>
     */
    @Transactional
    public WorkoutResponse create(UUID clubId, UUID athleteId, WorkoutRequest request, UUID planId,
                                  boolean notifyAthlete) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        Workout workout = new Workout();
        workout.setClub(athlete.getClub());
        workout.setAthlete(athlete);
        workout.setStatus(WorkoutStatus.PLANNED);
        workout.setPlanId(planId);
        apply(workout, request);

        workout = workoutRepository.save(workout);
        log.info("Séance créée {} (athlète={}, plan={})", workout.getId(), athleteId, planId);
        if (notifyAthlete) {
            notificationService.notifyWorkoutPlanned(workout);
        }
        return WorkoutResponse.from(workout);
    }

    /**
     * Modification d'une séance par le coach → l'athlète en est averti dès que le changement le
     * concerne.
     *
     * <p>Rien ne partait : un coach qui déplaçait la sortie longue du dimanche au samedi, ou qui
     * doublait le volume d'une séance de seuil, ne prévenait personne. L'athlète le découvrait en
     * ouvrant l'application, ou pas du tout.</p>
     *
     * <p>Deux garde-fous pour que ça ne devienne pas du bruit. La <b>signature</b> compare l'avant
     * et l'après : réenregistrer une séance sans rien changer — ce que fait un formulaire rouvert
     * puis validé — ne notifie pas. Et seules les séances <b>encore à faire</b> déclenchent :
     * corriger le libellé d'une séance déjà réalisée n'a aucun intérêt pour l'athlète. L'anti-rafale
     * qui regroupe le remaniement d'une semaine entière est porté par le service de notification.</p>
     */
    @Transactional
    public WorkoutResponse update(UUID clubId, UUID workoutId, WorkoutRequest request) {
        Workout workout = require(clubId, workoutId);
        boolean wasPlanned = workout.getStatus() == WorkoutStatus.PLANNED;
        String before = signature(workout);
        LocalDate previousDate = workout.getScheduledDate();

        apply(workout, request);

        if (wasPlanned && !before.equals(signature(workout))) {
            notificationService.notifyWorkoutChanged(
                    workout, !previousDate.equals(workout.getScheduledDate()));
        }
        return WorkoutResponse.from(workout);
    }

    /**
     * Empreinte de ce qu'une séance annonce à l'athlète : sa date, sa nature, son intitulé et son
     * contenu. Sert à ne notifier qu'un changement réel.
     *
     * <p>Les étapes en font partie : passer 6 × 400 m à 8 × 400 m ne touche ni la date ni le
     * titre, et c'est pourtant le changement que l'athlète a le plus besoin de connaître.</p>
     */
    private static String signature(Workout w) {
        StringBuilder sb = new StringBuilder()
                .append(w.getScheduledDate()).append('|').append(w.getType()).append('|')
                .append(w.getTitle()).append('|').append(w.getNotes()).append('|')
                .append(w.getTargetDistanceM()).append('|').append(w.getTargetDurationS());
        for (WorkoutStep s : w.getSteps()) {
            sb.append('|').append(s.getStepType()).append(':').append(s.getRepetitions())
                    .append(':').append(s.getZone()).append(':').append(s.getDistanceM())
                    .append(':').append(s.getDurationS()).append(':').append(s.getNotes());
        }
        return sb.toString();
    }

    /**
     * Transition d'état par le coach. Volontairement muette : c'est une écriture de suivi
     * (« je note que cette séance a été manquée »), pas une information nouvelle pour l'athlète,
     * qui est la source du fait constaté.
     */
    @Transactional
    public WorkoutResponse updateStatus(UUID clubId, UUID workoutId, WorkoutStatus target) {
        Workout workout = require(clubId, workoutId);
        if (!workout.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Transition de statut interdite : " + workout.getStatus() + " → " + target);
        }
        workout.setStatus(target);
        return WorkoutResponse.from(workout);
    }

    /**
     * Replanification (glisser-déposer) : change uniquement la date, et prévient l'athlète.
     *
     * <p>C'est le geste par lequel une séance se déplace le plus souvent, et il était muet.</p>
     */
    @Transactional
    public WorkoutResponse reschedule(UUID clubId, UUID workoutId, java.time.LocalDate date) {
        Workout workout = require(clubId, workoutId);
        boolean moved = workout.getStatus() == WorkoutStatus.PLANNED
                && !date.equals(workout.getScheduledDate());
        workout.setScheduledDate(date);
        if (moved) {
            notificationService.notifyWorkoutChanged(workout, true);
        }
        return WorkoutResponse.from(workout);
    }

    /**
     * Commentaire du coach sur une séance réalisée : le feedback in situ, sans passer par la
     * messagerie. Visible par l'athlète et notifié. Un texte vide efface le commentaire.
     */
    @Transactional
    public WorkoutResponse setCoachComment(UUID clubId, UUID workoutId, String comment) {
        Workout workout = require(clubId, workoutId);
        String text = comment == null || comment.isBlank() ? null : comment.trim();
        boolean isNew = text != null && !text.equals(workout.getCoachComment());
        workout.setCoachComment(text);
        workout.setCoachCommentAt(text == null ? null : java.time.Instant.now());
        if (isNew) {
            notificationService.notifyCoachComment(workout);
        }
        return WorkoutResponse.from(workout);
    }

    /**
     * Marque le retour de l'athlète comme traité (file « retours à traiter »). N'altère ni le
     * contenu de la séance ni le retour lui-même : c'est un accusé de lecture côté coach.
     */
    @Transactional
    public WorkoutResponse markFeedbackReviewed(UUID clubId, UUID workoutId, boolean reviewed) {
        Workout workout = require(clubId, workoutId);
        workout.setCoachReviewedAt(reviewed ? java.time.Instant.now() : null);
        return WorkoutResponse.from(workout);
    }

    /**
     * Duplique une semaine de séances course vers une autre semaine (planification en cycles) :
     * recopie chaque séance en conservant son décalage de jour, le contenu et la prescription figée,
     * en statut {@code PLANNED} et sans retour. Ne notifie pas (édition en cours côté coach).
     */
    @Transactional
    public int duplicateWeek(UUID clubId, UUID athleteId, LocalDate sourceWeekStart, LocalDate targetWeekStart) {
        athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        if (sourceWeekStart.equals(targetWeekStart)) {
            throw new ConflictException("La semaine cible doit être différente de la semaine source.");
        }
        int created = copyWeek(clubId, athleteId, sourceWeekStart, targetWeekStart, 1.0);
        log.info("Semaine dupliquée athlète={} : {} séance(s) ({} → {})",
                athleteId, created, sourceWeekStart, targetWeekStart);
        return created;
    }

    /**
     * Périodisation assistée : génère un mésocycle progressif à partir d'une semaine type. Chaque
     * semaine recopie la semaine source en mettant à l'échelle distance/durée par un facteur de
     * progression ({@code +increasePct} par semaine d'accumulation, semaine de décharge toutes les
     * {@code deloadEvery} semaines à {@code deloadPct}). Statut PLANNED, sans retour, sans notif.
     */
    @Transactional
    public int generateMesocycle(UUID clubId, UUID athleteId, LocalDate sourceWeekStart,
                                 LocalDate firstWeekStart, int weeks, double increasePct,
                                 int deloadEvery, double deloadPct) {
        athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        int n = Math.max(1, Math.min(weeks, 16));
        int blockLen = Math.max(2, deloadEvery);
        int created = 0;
        int buildIndex = 0;
        for (int i = 0; i < n; i++) {
            boolean deload = (i % blockLen) == blockLen - 1;
            double multiplier;
            if (deload) {
                multiplier = deloadPct / 100.0;
            } else {
                multiplier = 1.0 + (increasePct / 100.0) * buildIndex;
                buildIndex++;
            }
            LocalDate target = firstWeekStart.plusWeeks(i);
            if (target.equals(sourceWeekStart)) {
                continue; // ne pas réécrire la semaine source
            }
            created += copyWeek(clubId, athleteId, sourceWeekStart, target, multiplier);
        }
        log.info("Mésocycle généré athlète={} : {} séance(s) sur {} semaines (à partir de {})",
                athleteId, created, n, firstWeekStart);
        return created;
    }

    /** Paramètres de périodisation résolus (depuis un « méso type » ou la requête directe). */
    private record MesoParams(int weeks, double increasePct, int deloadEvery, double deloadPct) { }

    /**
     * Résout les paramètres de périodisation : depuis le modèle de mésocycle s'il est fourni,
     * sinon depuis la requête (avec valeurs par défaut). {@code weeks} est alors obligatoire.
     */
    private MesoParams resolveParams(UUID clubId, GenerateMesocycleRequest req) {
        if (req.mesocycleTemplateId() != null) {
            MesocycleTemplate t = mesocycleTemplateRepository
                    .findByIdAndClubId(req.mesocycleTemplateId(), clubId)
                    .orElseThrow(() -> new NotFoundException("Modèle de mésocycle introuvable."));
            return new MesoParams(t.getWeeks(), t.getIncreasePct(), t.getDeloadEvery(), t.getDeloadPct());
        }
        if (req.weeks() == null) {
            throw new ConflictException("Indiquez un nombre de semaines ou choisissez un modèle de mésocycle.");
        }
        return new MesoParams(req.weeks(), req.increasePctOrDefault(),
                req.deloadEveryOrDefault(), req.deloadPctOrDefault());
    }

    /** Génération de mésocycle pour un athlète à partir d'une requête (modèle ou paramètres directs). */
    @Transactional
    public int generateMesocycle(UUID clubId, UUID athleteId, GenerateMesocycleRequest req) {
        MesoParams p = resolveParams(clubId, req);
        return generateMesocycle(clubId, athleteId, req.sourceWeekStart(), req.firstWeekStart(),
                p.weeks(), p.increasePct(), p.deloadEvery(), p.deloadPct());
    }

    /**
     * Génère le mésocycle pour <strong>tous les athlètes actifs d'un groupe</strong> accessibles en
     * écriture (les autres sont ignorés). Chaque athlète est projeté à partir de sa propre semaine
     * source ({@code sourceWeekStart}) ; gros gain de temps pour piloter un groupe homogène.
     */
    @Transactional
    public GroupApplyResponse generateMesocycleForGroup(UUID clubId, UUID groupId,
                                                        GenerateMesocycleRequest req, UUID coachId) {
        groupRepository.findByIdAndClubId(groupId, clubId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        MesoParams p = resolveParams(clubId, req);
        List<Athlete> athletes = athleteRepository.findActiveByGroup(groupId, clubId, AthleteStatus.ACTIVE);
        int created = 0;
        int skipped = 0;
        int applied = 0;
        for (Athlete a : athletes) {
            boolean canWrite = accessValidator.effectiveLevel(coachId, a.getId())
                    .map(l -> l.atLeast(PermissionLevel.WRITE)).orElse(false);
            if (!canWrite) {
                skipped++;
                continue;
            }
            created += generateMesocycle(clubId, a.getId(), req.sourceWeekStart(), req.firstWeekStart(),
                    p.weeks(), p.increasePct(), p.deloadEvery(), p.deloadPct());
            applied++;
        }
        log.info("Mésocycle de groupe généré (groupe={}) : {} athlète(s), {} ignoré(s), {} séance(s)",
                groupId, applied, skipped, created);
        return new GroupApplyResponse(applied, skipped, created);
    }

    /** Recopie une semaine de séances en mettant la charge à l'échelle (facteur multiplicatif). */
    private int copyWeek(UUID clubId, UUID athleteId, LocalDate sourceWeekStart,
                         LocalDate targetWeekStart, double multiplier) {
        List<Workout> source = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, sourceWeekStart, sourceWeekStart.plusDays(6));
        int created = 0;
        for (Workout w : source) {
            long offset = ChronoUnit.DAYS.between(sourceWeekStart, w.getScheduledDate());
            Workout copy = new Workout();
            copy.setClub(w.getClub());
            copy.setAthlete(w.getAthlete());
            copy.setStatus(WorkoutStatus.PLANNED);
            copy.setScheduledDate(targetWeekStart.plusDays(offset));
            copy.setType(w.getType());
            copy.setTitle(w.getTitle());
            copy.setNotes(w.getNotes());
            copy.setTargetDistanceM(scale(w.getTargetDistanceM(), multiplier));
            copy.setTargetDurationS(scale(w.getTargetDurationS(), multiplier));
            copy.setSourceTemplateId(w.getSourceTemplateId());
            // Le snapshot/cibles ne sont recopiés qu'à l'identique (multiplier 1.0) : une mise à
            // l'échelle invaliderait les cibles figées calculées.
            if (multiplier == 1.0) {
                copy.setSessionSnapshot(w.getSessionSnapshot());
                copy.setCalculatedPaces(w.getCalculatedPaces());
            }
            for (WorkoutStep s : w.getSteps()) {
                WorkoutStep ns = new WorkoutStep();
                ns.setWorkout(copy);
                ns.setOrderIndex(s.getOrderIndex());
                ns.setStepType(s.getStepType());
                ns.setRepetitions(s.getRepetitions());
                ns.setZone(s.getZone());
                ns.setDistanceM(scale(s.getDistanceM(), multiplier));
                ns.setDurationS(scale(s.getDurationS(), multiplier));
                ns.setNotes(s.getNotes());
                copy.getSteps().add(ns);
            }
            workoutRepository.save(copy);
            created++;
        }
        return created;
    }

    private Integer scale(Integer value, double multiplier) {
        if (value == null || multiplier == 1.0) {
            return value;
        }
        return (int) Math.round(value * multiplier);
    }

    /**
     * Crée une séance prescrite depuis la bibliothèque avec snapshot figé + cibles calculées
     * (cf. DARI Lab — copie figée au moment de l'assignation).
     */
    @Transactional
    public WorkoutResponse createPrescribed(UUID clubId, UUID athleteId, PrescribedWorkout data) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        Workout workout = new Workout();
        workout.setClub(athlete.getClub());
        workout.setAthlete(athlete);
        workout.setStatus(WorkoutStatus.PLANNED);
        workout.setScheduledDate(data.date());
        workout.setType(data.type());
        workout.setTitle(data.title());
        workout.setNotes(data.notes());
        workout.setTargetDistanceM(data.targetDistanceM());
        workout.setTargetDurationS(data.targetDurationS());
        workout.setSourceTemplateId(data.sourceTemplateId());
        workout.setSessionSnapshot(data.snapshotJson());
        workout.setCalculatedPaces(data.calculatedJson());
        workout.setPlannedLoadUa(data.plannedLoadUa());

        workout = workoutRepository.save(workout);
        log.info("Séance prescrite {} depuis modèle {} (athlète={})",
                workout.getId(), data.sourceTemplateId(), athleteId);
        notificationService.notifyWorkoutPlanned(workout);
        return WorkoutResponse.from(workout);
    }

    /** Prescription figée d'une séance (snapshot + cibles calculées) — vue coach. */
    public WorkoutPrescriptionResponse prescription(UUID clubId, UUID workoutId) {
        return toPrescription(require(clubId, workoutId));
    }

    /**
     * Suppression d'une séance. L'athlète est averti si elle était <b>encore à faire et pas
     * passée</b> : c'est alors une annulation, une information qu'il attend. Supprimer une séance
     * d'une semaine écoulée est du ménage de calendrier, et ne regarde que le coach.
     */
    @Transactional
    public void delete(UUID clubId, UUID workoutId) {
        Workout workout = require(clubId, workoutId);
        // Avant la suppression : la notification a besoin du titre et de la date, et sa trace
        // in-app participe de la même transaction — si l'effacement échoue, elle disparaît avec.
        if (workout.getStatus() == WorkoutStatus.PLANNED
                && !workout.getScheduledDate().isBefore(clock.today())) {
            notificationService.notifyWorkoutCancelled(workout);
        }
        workoutRepository.delete(workout);
    }

    /**
     * Duplique une séance course vers une date (glisser + Alt, ou menu contextuel). Recopie le
     * contenu et la prescription figée à l'identique, en statut {@code PLANNED} et sans retour.
     */
    @Transactional
    public WorkoutResponse copyToDate(UUID clubId, UUID workoutId, LocalDate date) {
        Workout w = require(clubId, workoutId);
        Workout copy = new Workout();
        copy.setClub(w.getClub());
        copy.setAthlete(w.getAthlete());
        copy.setStatus(WorkoutStatus.PLANNED);
        copy.setScheduledDate(date);
        copy.setType(w.getType());
        copy.setTitle(w.getTitle());
        copy.setNotes(w.getNotes());
        copy.setTargetDistanceM(w.getTargetDistanceM());
        copy.setTargetDurationS(w.getTargetDurationS());
        copy.setSourceTemplateId(w.getSourceTemplateId());
        copy.setSessionSnapshot(w.getSessionSnapshot());
        copy.setCalculatedPaces(w.getCalculatedPaces());
        for (WorkoutStep s : w.getSteps()) {
            WorkoutStep ns = new WorkoutStep();
            ns.setWorkout(copy);
            ns.setOrderIndex(s.getOrderIndex());
            ns.setStepType(s.getStepType());
            ns.setRepetitions(s.getRepetitions());
            ns.setZone(s.getZone());
            ns.setDistanceM(s.getDistanceM());
            ns.setDurationS(s.getDurationS());
            ns.setNotes(s.getNotes());
            copy.getSteps().add(ns);
        }
        return WorkoutResponse.from(workoutRepository.save(copy));
    }

    // ----- Portail athlète (scoping par athleteId du principal) -----

    public List<WorkoutResponse> todayForAthlete(UUID athleteId, LocalDate date) {
        return workoutRepository.findByAthleteIdAndScheduledDateOrderByCreatedAtAsc(athleteId, date)
                .stream().map(WorkoutResponse::from).toList();
    }

    public List<WorkoutResponse> athleteCalendar(UUID athleteId, LocalDate from, LocalDate to) {
        return workoutRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(athleteId, from, to)
                .stream().map(WorkoutResponse::from).toList();
    }

    @Transactional
    public WorkoutResponse submitFeedback(UUID athleteId, UUID workoutId,
                                          com.coachrun.dto.request.WorkoutFeedbackRequest request) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        WorkoutStatus status = request.status();
        if (status != null) {
            if (!workout.getStatus().canTransitionTo(status)) {
                throw new ConflictException(
                        "Transition de statut interdite : " + workout.getStatus() + " → " + status);
            }
            workout.setStatus(status);
        }

        if (status == WorkoutStatus.MISSED) {
            // Une séance non faite n'a produit aucune charge : lui laisser un RPE la ferait peser
            // dans l'ACWR pour une durée prescrite qui n'a jamais été courue.
            applyMissed(workout, request);
        } else {
            workout.setMissedReason(null);
            workout.setRpe(request.rpe());
            // La sensation dit comment la séance a été vécue ; le RPE, sa difficulté. Elle relève
            // de l'exécution du contrat comme le RPE — c'est un ressenti d'entraînement, pas un
            // état de santé — et n'entre donc jamais dans le calcul de forme.
            workout.setFeel(request.feel());
            // Fatigue, douleur et blessures : données de l'article 9. Sans consentement actif
            // elles ne sont pas enregistrées, mais la séance se clôture — RPE, sensation, statut
            // et commentaire passent.
            workout.setFatigue(consentValidator.keepIfAllowed(workout.getAthlete(), request.fatigue()));
            workout.setPain(consentValidator.keepIfAllowed(workout.getAthlete(), request.pain()));
            // La durée réalisée n'a de sens que sur une séance écourtée : sur une séance menée à
            // son terme, la prescription fait foi et le champ reste vide.
            workout.setActualDurationS(
                    status == WorkoutStatus.PARTIAL ? request.actualDurationS() : null);
        }

        // Les blessures survivent aux trois statuts, y compris « pas faite » : c'est précisément
        // là qu'elles expliquent l'absence. Les effacer avec le reste de l'effort perdrait le
        // seul motif actionnable d'une séance manquée pour raison physique.
        applyInjuries(workout, request);
        workout.setAthleteComment(request.comment());
        notificationService.notifyAthleteFeedback(workout);
        // Une douleur élevée ne peut pas attendre le digest du lendemain matin : c'est le seul
        // signal du produit dont le délai se paie en blessure.
        notificationService.notifyPainAlert(workout.getAthlete(), workout.getPain());
        // Une blessure nommée ne franchit pas forcément le seuil de douleur (une entorse déclarée
        // avec 4/10 le manquait) : la déclaration elle-même vaut alerte.
        notificationService.notifyInjuryAlert(workout.getAthlete(),
                com.coachrun.util.InjuryCodec.read(workout.getInjuriesJson()));
        return WorkoutResponse.from(workout);
    }

    /**
     * Séance déclarée non faite : on efface tout ce qui décrirait un effort, et on garde le motif.
     *
     * <p>L'athlète n'avait jusqu'ici que « réalisée » et « partiellement ». Pour une séance qu'il
     * n'avait pas faite — déplacement professionnel, maladie, imprévu — le seul geste possible
     * était de ne rien faire, et son silence ressortait quelques jours plus tard en alerte
     * « séances manquées » côté coach, sans motif.</p>
     */
    private void applyMissed(Workout workout, com.coachrun.dto.request.WorkoutFeedbackRequest request) {
        workout.setRpe(null);
        workout.setFeel(null);
        workout.setFatigue(null);
        workout.setPain(null);
        workout.setActualDurationS(null);

        var reason = request.missedReason() == null
                ? com.coachrun.entity.enums.MissedReason.OTHER : request.missedReason();
        // « Santé » révèle un état de santé (art. 9) : même traitement que le motif d'une
        // indisponibilité, il est ramené à « autre » sans consentement actif.
        if (reason == com.coachrun.entity.enums.MissedReason.HEALTH
                && !consentValidator.isAllowed(workout.getAthlete())) {
            reason = com.coachrun.entity.enums.MissedReason.OTHER;
        }
        workout.setMissedReason(reason);
    }

    /**
     * Blessures déclarées au débrief : données de l'article 9, écartées sans consentement actif.
     *
     * <p>{@code null} veut dire « inchangé » — un écran qui ne pose pas la question ne doit pas
     * effacer ce qui a déjà été déclaré — tandis qu'une liste vide efface bel et bien : c'est le
     * geste de l'athlète qui retire sa déclaration.</p>
     */
    private void applyInjuries(Workout workout, com.coachrun.dto.request.WorkoutFeedbackRequest request) {
        if (request.injuries() == null) {
            return;
        }
        workout.setInjuriesJson(consentValidator.isAllowed(workout.getAthlete())
                ? com.coachrun.util.InjuryCodec.write(request.injuries()) : null);
    }

    /**
     * Déplacement d'une séance par l'athlète : change la date et marque {@code movedByAthlete}.
     * L'athlète peut déplacer mais jamais modifier le contenu (cf. DARI Lab).
     */
    @Transactional
    public WorkoutResponse moveByAthlete(UUID athleteId, UUID workoutId, LocalDate date) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        if (workout.getOriginalDate() == null) {
            workout.setOriginalDate(workout.getScheduledDate());
        }
        workout.setScheduledDate(date);
        workout.setMovedByAthlete(true);
        return WorkoutResponse.from(workout);
    }

    /** Une de mes séances — vue athlète (scopée par athleteId). */
    public WorkoutResponse getForAthlete(UUID athleteId, UUID workoutId) {
        return workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .map(WorkoutResponse::from)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
    }

    /** Prescription figée d'une séance — vue athlète (scopée par athleteId). */
    public WorkoutPrescriptionResponse prescriptionForAthlete(UUID athleteId, UUID workoutId) {
        Workout workout = workoutRepository.findByIdAndAthleteId(workoutId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        return toPrescription(workout);
    }

    /**
     * Édite la structure d'une séance planifiée pour son athlète : recalcule les cibles en
     * fourchettes et met à jour le snapshot figé + les totaux. Permet d'adapter une séance à un
     * athlète sans toucher au modèle de bibliothèque.
     */
    @Transactional
    public WorkoutPrescriptionResponse updateStructure(UUID clubId, UUID workoutId, SessionStructure structure) {
        Workout w = require(clubId, workoutId);
        SessionStructure safe = structure == null ? SessionStructure.empty() : structure;
        CalculatedSessionResponse calc =
                sessionCalculatorService.calculateSession(clubId, w.getAthlete().getId(), safe);
        String before = w.getSessionSnapshot();
        w.setSessionSnapshot(writeJson(safe));
        w.setCalculatedPaces(writeJson(calc));
        // La charge prévue suit l'adaptation de structure : sinon elle resterait sur l'ancienne.
        w.setPlannedLoadUa(plannedLoadEngine.compute(calc));
        if (calc.totalDistanceM() != null) {
            w.setTargetDistanceM(calc.totalDistanceM());
        }
        if (calc.totalDurationS() != null) {
            w.setTargetDurationS(calc.totalDurationS());
        }
        log.info("Structure de séance {} mise à jour (athlète={})", workoutId, w.getAthlete().getId());
        notifyStructureChanged(w, before);
        return toPrescription(w);
    }

    /**
     * Réécriture de la structure par le coach → l'athlète en est averti.
     *
     * <p><b>Le dernier chemin muet.</b> Créer, déplacer, modifier et supprimer une séance
     * prévenaient déjà ; « Adapter » — l'écran par lequel le coach réécrit réellement le contenu,
     * passe un 6 × 400 en 8 × 400, change les allures ou ajoute une série — ne prévenait personne.
     * C'est pourtant la modification la plus lourde de conséquences : l'athlète part courir la
     * séance qu'il avait lue la veille, pas celle qui est désormais prescrite.</p>
     *
     * <p>Trois conditions, les mêmes que partout ailleurs : la séance doit être encore à faire,
     * ne pas être passée — réécrire une séance de la semaine dernière est du ménage de calendrier
     * — et la structure doit avoir <b>réellement</b> changé. Ouvrir l'éditeur puis enregistrer
     * sans rien toucher ne notifie pas, sans quoi le canal se dévalue tout seul.</p>
     */
    private void notifyStructureChanged(Workout w, String previousSnapshot) {
        if (w.getStatus() != WorkoutStatus.PLANNED
                || w.getScheduledDate().isBefore(clock.today())
                || java.util.Objects.equals(previousSnapshot, w.getSessionSnapshot())) {
            return;
        }
        notificationService.notifyWorkoutChanged(w, false);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation de la structure impossible.", e);
        }
    }

    private WorkoutPrescriptionResponse toPrescription(Workout w) {
        SessionStructure snapshot = readJson(w.getSessionSnapshot(), SessionStructure.class);
        CalculatedSessionResponse calculated = readJson(w.getCalculatedPaces(), CalculatedSessionResponse.class);
        SessionStructure safe = snapshot == null ? SessionStructure.empty() : snapshot;
        return new WorkoutPrescriptionResponse(safe, calculated, resolveDrills(safe, w.getClub().getId()));
    }

    /** Résout les éducatifs (gammes) référencés par les blocs du snapshot, scopés au club. */
    private java.util.List<com.coachrun.dto.response.RunDrillResponse> resolveDrills(
            SessionStructure s, UUID clubId) {
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        java.util.stream.Stream.of(s.warmup(), s.main(), s.cooldown())
                .filter(java.util.Objects::nonNull)
                .flatMap(java.util.List::stream)
                .filter(b -> b.drillIds() != null)
                .forEach(b -> ids.addAll(b.drillIds()));
        if (ids.isEmpty()) {
            return java.util.List.of();
        }
        return ids.stream()
                .map(id -> runDrillRepository.findByIdAndClubId(id, clubId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(com.coachrun.dto.response.RunDrillResponse::of)
                .toList();
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

    private Workout require(UUID clubId, UUID workoutId) {
        return workoutRepository.findByIdAndClubId(workoutId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
    }

    private void apply(Workout workout, WorkoutRequest request) {
        workout.setScheduledDate(request.scheduledDate());
        workout.setType(request.type());
        workout.setTitle(request.title());
        workout.setNotes(request.notes());
        workout.setTargetDistanceM(request.targetDistanceM());
        workout.setTargetDurationS(request.targetDurationS());
        workout.replaceSteps(request.steps().stream().map(this::toStep).toList());
    }

    private WorkoutStep toStep(WorkoutStepRequest req) {
        WorkoutStep step = new WorkoutStep();
        step.setStepType(req.stepType());
        step.setRepetitions(Math.max(1, req.repetitions()));
        step.setZone(req.zone());
        step.setDistanceM(req.distanceM());
        step.setDurationS(req.durationS());
        step.setNotes(req.notes());
        return step;
    }
}
