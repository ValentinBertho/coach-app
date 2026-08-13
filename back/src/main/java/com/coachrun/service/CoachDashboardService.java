package com.coachrun.service;

import com.coachrun.dto.response.AthleteFormResponse;
import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.dto.response.CoachDashboardResponse;
import com.coachrun.dto.response.CoachDaySessionResponse;
import com.coachrun.dto.response.CoachFormDashboardResponse;
import com.coachrun.dto.response.FeedbackQueueItemResponse;
import com.coachrun.dto.response.RaceObjectiveResponse;
import com.coachrun.engine.FormStatusEngine;
import com.coachrun.entity.enums.FormStatus;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Agrégation des indicateurs du tableau de bord coach. */
@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachDashboardService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final RaceObjectiveRepository raceRepository;
    private final FormStatusEngine formStatusEngine;
    private final CoachAthleteRelationRepository relationRepository;
    private final AthleteLoadService loadService;
    private final AthleteFeedbackService feedbackService;
    private final com.coachrun.security.AthleteAccessValidator accessValidator;
    private final ScheduledStrengthSessionRepository strengthRepository;
    private final ClockService clock;
    private final com.coachrun.repository.AthleteUnavailabilityRepository unavailabilityRepository;
    private final ProgressionService progressionService;

    /**
     * KPI du cockpit, restreints au périmètre choisi (all / mine / private / club) — comme la
     * jauge de forme et les alertes. Sans cela, changer de périmètre ne rechargeait que la
     * forme, et les KPI continuaient de décrire tout le club.
     */
    public CoachDashboardResponse compute(UUID clubId, String scope, UUID coachId) {
        LocalDate today = clock.today();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate nextMonday = monday.plusWeeks(1);

        List<Athlete> athletes = athletesInScope(clubId, scope, coachId);
        List<UUID> ids = athletes.stream().map(Athlete::getId).toList();

        long activeAthletes = athletes.stream().filter(a -> a.getStatus() == AthleteStatus.ACTIVE).count();
        long pending = athletes.stream().filter(a -> a.getInviteToken() != null).count();
        // « Retours à traiter » : retours d'athlètes (RPE / douleur / commentaire) non encore vus.
        // Le KPI compte exactement les lignes de la file — il pointe dessus.
        long toReview = feedbackQueue(clubId, scope, coachId, DEFAULT_FEEDBACK_WINDOW_DAYS).size();

        if (ids.isEmpty()) {
            return new CoachDashboardResponse(0, 0, 0, 0, List.of());
        }

        long completedThisWeek = workoutRepository.countByAthleteIdInAndStatusAndScheduledDateBetween(
                ids, WorkoutStatus.COMPLETED, monday, nextMonday);
        // Le nom de l'athlète accompagne chaque course : sur une liste multi-athlètes,
        // « Marathon de Paris » sans savoir de qui il s'agit ne dit rien au coach.
        var races = raceRepository
                .findTop5ByAthleteIdInAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
                        ids, RaceObjectiveStatus.UPCOMING, today)
                .stream().map(r -> RaceObjectiveResponse.from(r, displayName(r.getAthlete()), today)).toList();

        return new CoachDashboardResponse(activeAthletes, pending, toReview, completedThisWeek, races);
    }

    /**
     * File « retours à traiter » : tous athlètes du périmètre confondus, les séances réalisées
     * dont l'athlète a laissé un retour (RPE, douleur, commentaire) et que le coach n'a pas
     * encore marquées comme traitées. Course et force unifiées, triées du plus récent au plus
     * ancien.
     *
     * @param days profondeur de la fenêtre en jours (bornée entre 1 et 90, 14 par défaut).
     *             Sans borne, la file cumulait tout l'historique jamais marqué comme traité et
     *             cessait d'être lisible au bout de quelques semaines.
     */
    public List<FeedbackQueueItemResponse> feedbackQueue(UUID clubId, String scope, UUID coachId,
                                                        int days) {
        List<Athlete> athletes = athletesInScope(clubId, scope, coachId);
        if (athletes.isEmpty()) {
            return List.of();
        }
        java.time.LocalDate since = clock.today().minusDays(windowDays(days));
        Map<UUID, String> names = athletes.stream()
                .collect(java.util.stream.Collectors.toMap(Athlete::getId, CoachDashboardService::displayName));
        List<UUID> ids = List.copyOf(names.keySet());

        List<FeedbackQueueItemResponse> items = new ArrayList<>();
        for (Workout w : workoutRepository.findPendingFeedback(ids, since)) {
            items.add(new FeedbackQueueItemResponse(
                    "COURSE", w.getId(), w.getAthlete().getId(), names.get(w.getAthlete().getId()),
                    w.getTitle(), w.getScheduledDate(),
                    w.getRpe() == null ? null : w.getRpe().doubleValue(),
                    w.getFatigue(), w.getPain(), w.getFeel(),
                    com.coachrun.util.InjuryCodec.read(w.getInjuriesJson()),
                    w.getAthleteComment()));
        }
        for (ScheduledStrengthSession s : strengthRepository.findPendingFeedback(ids, since)) {
            items.add(new FeedbackQueueItemResponse(
                    "STRENGTH", s.getId(), s.getAthlete().getId(), names.get(s.getAthlete().getId()),
                    s.getTitle(), s.getScheduledDate(),
                    s.getSessionRpe() == null ? null : s.getSessionRpe().doubleValue(),
                    // Le débrief de force ne collecte encore ni sensation ni blessure : la file
                    // affiche donc ce qu'elle a, sans prétendre à une absence de déclaration.
                    s.getSessionFatigue(), s.getSessionPain(), null, java.util.List.of(),
                    s.getSessionComment()));
        }
        items.sort(java.util.Comparator
                .comparing(FeedbackQueueItemResponse::sessionDate).reversed()
                .thenComparing(FeedbackQueueItemResponse::athleteName));
        return items;
    }

    /**
     * La journée d'un coach : les séances de course prévues un jour donné, tous athlètes de son
     * périmètre confondus.
     *
     * <p>C'est l'information que le calendrier ne sait pas donner. Il se lit athlète par athlète ;
     * la question « qu'est-ce qui est prévu aujourd'hui, et pour qui » demandait donc d'ouvrir
     * autant de fiches que d'athlètes suivis — impraticable sur un téléphone, et déjà pénible
     * ailleurs.</p>
     *
     * <p>Course seulement : le renforcement a son propre écran, ses propres formats et son propre
     * débrief. L'unifier ici mêlerait deux gestes de coaching différents dans une liste dont la
     * seule promesse est de tenir sur un écran de téléphone.</p>
     */
    public List<CoachDaySessionResponse> day(UUID clubId, String scope, UUID coachId, LocalDate date) {
        List<Athlete> athletes = athletesInScope(clubId, scope, coachId);
        if (athletes.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = athletes.stream()
                .collect(java.util.stream.Collectors.toMap(Athlete::getId, CoachDashboardService::displayName));
        LocalDate day = date == null ? clock.today() : date;

        return workoutRepository.findByAthletesAndDate(List.copyOf(names.keySet()), day).stream()
                .map(w -> CoachDaySessionResponse.from(w, names.get(w.getAthlete().getId())))
                .sorted(java.util.Comparator.comparing(CoachDaySessionResponse::athleteName,
                        java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** Fenêtre par défaut de la file de retours, bornée pour rester une file de travail. */
    public static final int DEFAULT_FEEDBACK_WINDOW_DAYS = 14;

    private static int windowDays(int days) {
        return Math.max(1, Math.min(days <= 0 ? DEFAULT_FEEDBACK_WINDOW_DAYS : days, 90));
    }

    /**
     * Marque d'un coup tous les retours en attente du périmètre et de la fenêtre courants.
     * Sans ça, vider la file demandait un clic par ligne — et personne ne le faisait.
     *
     * @return nombre de retours marqués (course + force)
     */
    @org.springframework.transaction.annotation.Transactional
    public int markAllFeedbackReviewed(UUID clubId, String scope, UUID coachId, int days) {
        List<Athlete> athletes = athletesInScope(clubId, scope, coachId);
        if (athletes.isEmpty()) {
            return 0;
        }
        List<UUID> ids = athletes.stream().map(Athlete::getId).toList();
        java.time.LocalDate since = clock.today().minusDays(windowDays(days));
        java.time.Instant now = java.time.Instant.now();

        int marked = 0;
        for (Workout w : workoutRepository.findPendingFeedback(ids, since)) {
            w.setCoachReviewedAt(now);
            marked++;
        }
        for (ScheduledStrengthSession s : strengthRepository.findPendingFeedback(ids, since)) {
            s.setCoachReviewedAt(now);
            marked++;
        }
        log.info("Retours marqués comme traités en masse : {} (club={}, scope={}, {} j)",
                marked, clubId, scope, windowDays(days));
        return marked;
    }

    private static String displayName(Athlete a) {
        return (a.getFirstName() + " " + a.getLastName()).trim();
    }

    /**
     * État de forme d'un athlète : la classification fatigue + douleur, <b>si le signal est encore
     * frais</b>.
     *
     * <p>Le moteur reste intact — il traduit deux nombres en couleur, c'est tout son rôle, et
     * l'invariant « jamais le RPE » lui appartient. La péremption est une décision de service :
     * elle dépend de la date du retour, que le moteur n'a pas à connaître.</p>
     *
     * <p>Sans elle, une valeur absente était lue comme un zéro et un athlète sans aucun retour
     * s'affichait vert ; et un rouge déclaré après une compétition restait rouge des semaines
     * durant, en tête de la file « à surveiller », devant les athlètes qui, eux, avaient signalé
     * quelque chose le matin même.</p>
     */
    private FormStatus formStatusOf(AthleteFeedbackService.LastFeedback last) {
        return last.isFresh(clock.today())
                ? formStatusEngine.classify(last.fatigue(), last.pain())
                : FormStatus.STALE;
    }

    /**
     * Tableau de bord « état de forme » : athlètes actifs répartis Route/Trail, chacun avec sa
     * pastille de forme (fatigue + douleur du dernier retour).
     */
    public CoachFormDashboardResponse formDashboard(UUID clubId, String scope, UUID coachId) {
        List<AthleteFormResponse> route = new ArrayList<>();
        List<AthleteFormResponse> trail = new ArrayList<>();

        for (Athlete a : athletesInScope(clubId, scope, coachId)) {
            if (a.getStatus() != AthleteStatus.ACTIVE) {
                continue;
            }
            // Course + force confondues : un retour de renforcement compte autant qu'une course.
            AthleteFeedbackService.LastFeedback last = feedbackService.lastFeedback(a.getId());
            AthleteFormResponse row = AthleteFormResponse.of(
                    a, formStatusOf(last),
                    last.fatigue(), last.pain(), last.date());

            if (a.getDiscipline() == Discipline.TRAIL) {
                trail.add(row);
            } else {
                route.add(row);
            }
        }

        return new CoachFormDashboardResponse(
                route.size() + trail.size(), route.size(), trail.size(), route, trail);
    }

    /**
     * Athlètes d'un périmètre : all = tout le club ; mine/private/club = via les relations du coach.
     *
     * <p><b>« Tout le club » n'est pas « tous les athlètes du club ».</b> Ce chemin renvoyait la
     * table entière sans passer par
     * {@link com.coachrun.security.AthleteAccessValidator} — alors que le calendrier de groupe,
     * lui, filtre athlète par athlète. Comme le périmètre par défaut du cockpit est {@code all},
     * un coach assistant y lisait la fatigue et la douleur des athlètes <b>privés</b> de ses
     * collègues, sur l'écran le plus consulté du produit. Un athlète privé n'est jamais partagé :
     * c'est la règle du modèle multi-coach, et elle vaut ici comme ailleurs.</p>
     *
     * <p>Le filtrage utilise la variante par identifiant de coach, qui ne connaît pas le rôle
     * plateforme : un {@code PLATFORM_ADMIN} qui ouvrirait un cockpit de club n'y verrait donc pas
     * les athlètes privés. C'est volontaire — la supervision passe par le back-office
     * d'administration, pas par une vue de coach.</p>
     */
    private List<Athlete> athletesInScope(UUID clubId, String scope, UUID coachId) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            List<Athlete> all = athleteRepository.findByClubIdOrderByLastNameAsc(clubId);
            if (coachId == null) {
                // Balayage serveur (digest quotidien) : pas de coach demandeur, donc rien à
                // filtrer. Le digest réachemine ensuite chaque alerte vers le coach référent.
                return all;
            }
            return all.stream()
                    .filter(a -> accessValidator.effectiveLevel(coachId, a.getId()).isPresent())
                    .toList();
        }
        return relationRepository.findByCoachIdAndActiveTrue(coachId).stream()
                .filter(rel -> switch (scope.toLowerCase()) {
                    case "private" -> rel.getClub() == null;
                    case "club" -> rel.getClub() != null;
                    default -> true;            // "mine" = privés + club
                })
                .map(CoachAthleteRelation::getAthlete)
                .filter(a -> a.getClub() != null && clubId.equals(a.getClub().getId()))
                .distinct()
                .sorted(java.util.Comparator.comparing(Athlete::getLastName,
                        java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /**
     * File d'alertes actionnables : pour chaque athlète actif du périmètre, détecte douleur,
     * charge à risque (ACWR/monotonie), séances manquées et silence. Triées par gravité (rouge
     * d'abord). Transforme le tableau de bord « descriptif » en outil de pilotage par exception.
     */
    public List<CoachAlertResponse> alerts(UUID clubId, String scope, UUID coachId) {
        LocalDate today = clock.today();
        List<CoachAlertResponse> alerts = new ArrayList<>();

        for (Athlete a : athletesInScope(clubId, scope, coachId)) {
            if (a.getStatus() != AthleteStatus.ACTIVE) {
                continue;
            }
            String name = (a.getFirstName() + " " + a.getLastName()).trim();
            String discipline = a.getDiscipline() == Discipline.TRAIL ? "TRAIL" : "ROUTE";

            // Ce que le coach a déjà saisi ne doit pas lui revenir en alerte : une indisponibilité
            // déclarée explique à elle seule les séances non faites, le silence et la charge qui
            // retombe. Sans ce filtre, l'athlète blessé devenait le plus alarmant du club, tous les
            // matins, pendant toute la durée de sa blessure.
            List<com.coachrun.entity.AthleteUnavailability> unavailable = unavailabilityRepository
                    .findByAthleteIdAndEndDateGreaterThanEqualAndStartDateLessThanEqual(
                            a.getId(), today.minusDays(MISSED_WINDOW_DAYS), today);

            // --- Douleur (dernier retour, course ou force) ---
            AthleteFeedbackService.LastFeedback lastFeedback = feedbackService.lastFeedback(a.getId());
            // Une douleur périmée n'est pas une douleur d'aujourd'hui : elle ne doit plus lever
            // d'alerte, sinon un athlète qui a cessé de saisir reste en tête de file pour toujours.
            // Son silence, lui, est déjà couvert par l'alerte « athlète silencieux » plus bas.
            Integer pain = lastFeedback.isFresh(today) ? lastFeedback.pain() : null;
            if (pain != null && pain >= 5) {
                alerts.add(alert(a, name, discipline, "RED", "PAIN",
                        "Douleur élevée", "Douleur " + pain + "/10 au dernier retour."));
            } else if (pain != null && pain >= 3) {
                alerts.add(alert(a, name, discipline, "ORANGE", "PAIN",
                        "Douleur signalée", "Douleur " + pain + "/10 au dernier retour."));
            }

            // --- Charge (ACWR / monotonie) ---
            try {
                var load = loadService.load(clubId, a.getId());
                Double ratio = load.ratio();
                if (ratio != null && ratio > 1.5) {
                    alerts.add(alert(a, name, discipline, "RED", "ACWR_HIGH",
                            "Charge en forte hausse", "ACWR " + ratio + " (> 1,5 : risque de blessure)."));
                } else if (ratio != null && ratio >= 1.3) {
                    alerts.add(alert(a, name, discipline, "ORANGE", "ACWR_HIGH",
                            "Charge en hausse", "ACWR " + ratio + " (zone de vigilance)."));
                } else if (ratio != null && ratio < 0.8 && load.sessions28d() > 0
                        && unavailable.isEmpty()) {
                    // Une charge qui retombe pendant une coupure déclarée n'est pas un
                    // désentraînement subi : c'est le plan.
                    alerts.add(alert(a, name, discipline, "ORANGE", "ACWR_LOW",
                            "Charge en baisse", "ACWR " + ratio + " (désentraînement possible)."));
                }
                if (load.monotony() != null && load.monotony() >= 2.0) {
                    alerts.add(alert(a, name, discipline, "ORANGE", "MONOTONY",
                            "Monotonie élevée", "Monotonie " + load.monotony() + " (manque de variété)."));
                }
            } catch (RuntimeException ignored) {
                // un calcul de charge en échec ne doit pas masquer les autres alertes
            }

            // --- Force : douleur, RPE proche de l'échec, RIR nul, charge sous la prescription ---
            // Ces alertes existaient déjà mais restaient enfermées dans l'écran d'une séance :
            // il fallait les ouvrir une par une pour les voir. Elles remontent ici, dédoublonnées
            // par type — trois séries douloureuses dans la semaine, c'est une alerte, pas trois.
            try {
                java.util.Set<String> seenStrength = new java.util.HashSet<>();
                for (var sa : progressionService.recentAlerts(a.getId(), today.minusDays(MISSED_WINDOW_DAYS))) {
                    if (!seenStrength.add(sa.code())) {
                        continue;
                    }
                    alerts.add(alert(a, name, discipline,
                            "HIGH".equals(sa.level()) ? "RED" : "ORANGE",
                            "STRENGTH_" + sa.code(), "Renforcement — " + sa.exerciseName(), sa.message()));
                }
            } catch (RuntimeException ignored) {
                // un snapshot de séance illisible ne doit pas masquer les autres alertes
            }

            // --- Séances manquées (14 derniers jours) ---
            List<Workout> recent = workoutRepository
                    .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                            clubId, a.getId(), today.minusDays(MISSED_WINDOW_DAYS), today);
            long missed = recent.stream()
                    .filter(w -> w.getScheduledDate().isBefore(today)
                            && w.getStatus() == WorkoutStatus.PLANNED
                            && !fallsWithin(w.getScheduledDate(), unavailable))
                    .count();
            if (missed >= 3) {
                alerts.add(alert(a, name, discipline, "RED", "MISSED",
                        missed + " séances manquées", "Sur les 14 derniers jours."));
            } else if (missed == 2) {
                alerts.add(alert(a, name, discipline, "ORANGE", "MISSED",
                        "2 séances manquées", "Sur les 14 derniers jours."));
            }

            // --- Silence (aucun retour depuis longtemps alors qu'un programme tourne) ---
            boolean hasRecentProgram = !recent.isEmpty();
            LocalDate lastFb = lastFeedback.date();
            long silence = lastFb == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(lastFb, today);
            // Un athlète en coupure déclarée n'a rien à déclarer : son silence est attendu.
            if (hasRecentProgram && silence > 10 && unavailable.isEmpty()) {
                String detail = lastFb == null ? "Aucun retour de séance enregistré."
                        : "Dernier retour il y a " + silence + " jours.";
                alerts.add(alert(a, name, discipline, "ORANGE", "SILENCE", "Athlète silencieux", detail));
            }
        }

        // Tri : rouge avant orange, puis par nom pour la stabilité.
        alerts.sort(java.util.Comparator
                .comparingInt((CoachAlertResponse al) -> "RED".equals(al.severity()) ? 0 : 1)
                .thenComparing(CoachAlertResponse::athleteName));
        return alerts;
    }

    /** Profondeur de la fenêtre « séances manquées », et donc de la recherche d'indisponibilités. */
    private static final int MISSED_WINDOW_DAYS = 14;

    /** La date tombe-t-elle dans l'une des périodes d'indisponibilité déclarées ? */
    private static boolean fallsWithin(LocalDate date,
                                       List<com.coachrun.entity.AthleteUnavailability> windows) {
        return windows.stream().anyMatch(u ->
                !date.isBefore(u.getStartDate()) && !date.isAfter(u.getEndDate()));
    }

    private CoachAlertResponse alert(Athlete a, String name, String discipline,
                                     String severity, String type, String title, String detail) {
        return new CoachAlertResponse(a.getId(), name, discipline, severity, type, title, detail);
    }
}
