package com.coachrun.service;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.response.ActivityExclusionResponse;
import com.coachrun.dto.response.ActivityLapsResponse;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.dto.response.FeedbackPromptResponse;
import com.coachrun.entity.Activity;
import com.coachrun.entity.ActivityExclusion;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityExclusionRepository;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Import des activités réalisées (manuel pour l'instant), déduplication et rapprochement
 * automatique prévu/réalisé. Scopé par club (anti-IDOR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    /**
     * Détection de niveau après import, injectée <b>paresseusement</b> : elle relit les tours par
     * ce service, et une injection directe fermerait le cycle de dépendances au démarrage.
     */
    private final org.springframework.beans.factory.ObjectProvider<PhysioDetectionService> physioDetection;

    private final ActivityRepository activityRepository;
    private final ActivityExclusionRepository exclusionRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final MatchingService matchingService;
    private final com.coachrun.security.HealthDataConsentValidator consentValidator;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public List<ActivityResponse> list(UUID clubId, UUID athleteId) {
        return activityRepository.findByClubIdAndAthleteIdOrderByActivityDateDesc(clubId, athleteId)
                .stream().map(this::toResponse).toList();
    }

    /** Liste — variante athlète-scopée (portail /me) : ne renvoie que mes activités. */
    public List<ActivityResponse> listForAthlete(UUID athleteId) {
        return activityRepository.findByAthleteIdOrderByActivityDateDesc(athleteId)
                .stream().map(this::toResponse).toList();
    }

    /** Tracé GPS — variante athlète-scopée : l'activité doit appartenir à l'athlète. */
    public java.util.List<double[]> routeForAthlete(UUID athleteId, UUID activityId) {
        Activity a = activityRepository.findById(activityId)
                .filter(act -> act.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
        if (a.getRouteJson() == null) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(a.getRouteJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<double[]>>() { });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    @Transactional
    public ActivityResponse importActivity(UUID clubId, UUID athleteId, ActivityImportRequest request) {
        return importActivity(clubId, athleteId, request, null);
    }

    /**
     * Données que la source d'import peut fournir en plus du DTO public : capteurs, tracé et flux.
     * Elles ne passent pas par {@link ActivityImportRequest}, qui est un corps de requête HTTP —
     * un tracé de 400 points n'a rien à y faire.
     *
     * @param route  tracé {@code [[lat,lon],…]}, format commun au GPX et à la polyline Strava
     * @param stream flux {@code [[elapsedS,hr,paceSecPerKm],…]} (-1 = absent), pour le temps-en-zone
     * @param laps   tours relevés par la montre. Vides quand la source n'en donne qu'un : une
     *               sortie continue n'a pas de tours, et la lecture retombera sur des splits
     *               kilométriques calculés — qu'on ne stocke pas, puisqu'ils se recalculent.
     */
    public record ImportExtras(
            Integer maxHr, Integer avgCadence, Integer avgPowerW, Integer calories,
            java.util.List<double[]> route, java.util.List<int[]> stream,
            java.util.List<com.coachrun.dto.response.ActivityLapsResponse.Lap> laps) {

        /** Variante sans tours, pour les sources qui n'en fournissent pas. */
        public ImportExtras(Integer maxHr, Integer avgCadence, Integer avgPowerW, Integer calories,
                            java.util.List<double[]> route, java.util.List<int[]> stream) {
            this(maxHr, avgCadence, avgPowerW, calories, route, stream, java.util.List.of());
        }
    }

    @Transactional
    public ActivityResponse importActivity(UUID clubId, UUID athleteId, ActivityImportRequest request,
                                           ImportExtras extras) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        ActivitySource source = request.sourceOrDefault();
        if (request.externalId() != null
                && activityRepository.existsByAthleteIdAndSourceAndExternalId(athleteId, source, request.externalId())) {
            throw new ConflictException("Cette activité a déjà été importée.");
        }
        // Écartée pour de bon par l'athlète. Le refus vaut pour TOUS les chemins d'import — la
        // synchro planifiée, le webhook Strava, et l'import manuel : c'est le seul endroit où une
        // activité entre, donc le seul où la décision tient sans avoir à être répétée.
        if (request.externalId() != null
                && exclusionRepository.existsByAthleteIdAndSourceAndExternalId(athleteId, source, request.externalId())) {
            throw new ConflictException("Cette sortie a été retirée de Darilab : elle n'est plus importée.");
        }
        rejectIfDuplicate(athleteId, request.activityDate(), request.distanceM(), request.durationS(),
                Boolean.TRUE.equals(request.confirmDuplicate()));

        Activity activity = new Activity();
        activity.setClub(athlete.getClub());
        activity.setAthlete(athlete);
        activity.setSource(source);
        activity.setExternalId(request.externalId());
        activity.setActivityDate(request.activityDate());
        activity.setTitle(request.title());
        activity.setDistanceM(request.distanceM());
        activity.setDurationS(request.durationS());
        activity.setAvgHr(request.avgHr());
        activity.setElevationGainM(request.elevationGainM());
        activity.setStatus(ActivityStatus.IMPORTED);
        applyExtras(activity, extras);

        autoMatch(athleteId, activity);
        activity = activityRepository.save(activity);
        detectPhysio(activity);
        log.info("Activité importée {} (athlète={}, statut={})", activity.getId(), athleteId, activity.getStatus());
        return toResponse(activity);
    }

    /** Écart toléré sur la distance et la durée avant de considérer deux sorties identiques. */
    private static final double DUPLICATE_TOLERANCE = 0.05;

    /**
     * Refuse une sortie qui en double une autre du même jour, quelle que soit sa provenance.
     *
     * <p><b>Pourquoi ce contrôle existe.</b> La déduplication ne portait que sur le triplet
     * (athlète, source, identifiant externe). Or cet identifiant n'existe que pour les activités
     * venues d'une plateforme : un fichier GPX importé et une saisie manuelle n'en ont jamais.
     * Deux gestes très ordinaires produisaient donc des doublons — exporter la trace de sa montre
     * puis connecter Strava, ou ré-importer le même fichier par maladresse sur mobile — et comme
     * les sources diffèrent, même un contrôle d'unicité sur l'identifiant ne les aurait pas
     * rapprochées.</p>
     *
     * <p>L'effet se voyait sur le seul chiffre que l'athlète regarde : le récapitulatif
     * hebdomadaire additionne toutes les activités de la semaine, et affichait « 64/45 km » sur
     * une semaine où il en avait couru 32. Le cahier des charges pose pourtant « zéro doublon »
     * en critère d'acceptation.</p>
     *
     * <p>Ce n'est pas un refus définitif : la réponse nomme la sortie en cause et l'appelant peut
     * confirmer. Deux séances le même jour, ça existe — mais deux séances identiques au kilomètre
     * et à la minute près, beaucoup moins.</p>
     */
    private void rejectIfDuplicate(UUID athleteId, java.time.LocalDate date,
                                   Integer distanceM, Integer durationS, boolean confirmed) {
        if (confirmed || date == null) {
            return;
        }
        for (Activity existing : activityRepository
                .findByAthleteIdAndActivityDateBetween(athleteId, date, date)) {
            if (looksLikeSameOuting(existing, distanceM, durationS)) {
                throw new ConflictException(String.format(
                        "Une sortie très proche est déjà enregistrée ce jour-là (%s%s). "
                                + "Confirmez si vous avez bien couru deux fois.",
                        sourceLabel(existing.getSource()),
                        existing.getDistanceM() == null ? ""
                                : String.format(", %.1f km", existing.getDistanceM() / 1000.0)));
            }
        }
    }

    /** Deux sorties du même jour se ressemblent-elles au point d'être la même ? */
    private boolean looksLikeSameOuting(Activity existing, Integer distanceM, Integer durationS) {
        Boolean sameDistance = closeEnough(existing.getDistanceM(), distanceM);
        Boolean sameDuration = closeEnough(existing.getDurationS(), durationS);
        if (sameDistance == null && sameDuration == null) {
            // Ni distance ni durée comparables : la date seule ne prouve rien, on laisse passer.
            return false;
        }
        return !Boolean.FALSE.equals(sameDistance) && !Boolean.FALSE.equals(sameDuration);
    }

    /** {@code null} si la comparaison n'a pas de sens (valeur absente d'un côté ou de l'autre). */
    private Boolean closeEnough(Integer a, Integer b) {
        if (a == null || b == null || a <= 0 || b <= 0) {
            return null;
        }
        return Math.abs(a - b) <= Math.max(a, b) * DUPLICATE_TOLERANCE;
    }

    private String sourceLabel(ActivitySource source) {
        return switch (source) {
            case STRAVA -> "importée de Strava";
            case FILE -> "importée d'un fichier";
            case MANUAL -> "saisie à la main";
            default -> "déjà importée";
        };
    }

    /**
     * Faut-il passer cette activité externe sans même la télécharger ?
     *
     * <p>Deux raisons de le faire, et une seule conséquence : elle est déjà en base, ou l'athlète
     * l'a écartée pour de bon. Le test se fait <em>avant</em> d'aller chercher les détails, quand
     * la source facture chaque appel (quota Strava) — une sortie masquée serait sinon retéléchargée
     * à chaque synchro pour être refusée à l'arrivée.</p>
     */
    public boolean alreadyKnown(UUID athleteId, ActivitySource source, String externalId) {
        if (externalId == null) {
            return false;
        }
        return activityRepository.existsByAthleteIdAndSourceAndExternalId(athleteId, source, externalId)
                || exclusionRepository.existsByAthleteIdAndSourceAndExternalId(athleteId, source, externalId);
    }

    /**
     * Reporte capteurs, tracé et flux sur l'activité. Le tracé et le flux sont optionnels : une
     * activité sur tapis n'en a pas, et leur absence ne doit jamais faire échouer un import.
     */
    private void applyExtras(Activity activity, ImportExtras extras) {
        if (extras == null) {
            return;
        }
        activity.setMaxHr(extras.maxHr());
        activity.setAvgCadence(extras.avgCadence());
        activity.setAvgPowerW(extras.avgPowerW());
        activity.setCalories(extras.calories());
        if (extras.route() != null && !extras.route().isEmpty()) {
            try {
                activity.setRouteJson(objectMapper.writeValueAsString(extras.route()));
            } catch (Exception ignored) {
                // tracé optionnel
            }
        }
        if (extras.stream() != null && !extras.stream().isEmpty()) {
            try {
                activity.setStreamJson(objectMapper.writeValueAsString(extras.stream()));
            } catch (Exception ignored) {
                // flux optionnel
            }
        }
        applyLaps(activity, extras.laps());
    }

    /**
     * Stocke les tours de la montre. Seuls ceux-là méritent la place : des splits kilométriques
     * se recalculent à la lecture depuis le flux, et les stocker figerait une découpe qu'on peut
     * refaire à tout moment (y compris sur les activités importées avant cette fonctionnalité).
     */
    private void applyLaps(Activity activity, java.util.List<ActivityLapsResponse.Lap> laps) {
        if (laps == null || laps.size() < 2) {
            return;
        }
        try {
            activity.setLapsJson(objectMapper.writeValueAsString(
                    new ActivityLapsResponse(ActivityLapsResponse.Kind.DEVICE, laps)));
        } catch (Exception ignored) {
            // tours optionnels
        }
    }

    // --- Correction d'une sortie par l'athlète -----------------------------

    /**
     * L'athlète corrige une de ses sorties et y pose son ressenti.
     *
     * <p>Le ressenti n'existait que porté par une séance <em>prescrite</em>. Une sortie sans
     * séance — une course, un footing improvisé — n'avait donc aucun moyen d'être commentée,
     * alors que c'est exactement là que le coach n'a rien d'autre à lire : ni prescription à
     * comparer, ni retour de séance.</p>
     *
     * <p><b>Une sortie rapprochée n'a pas de ressenti à elle.</b> Elle et sa séance décrivent le
     * même effort ; deux ressentis pour un seul effort, c'est un de trop, et rien ne disait
     * lequel croyait le coach. Le ressenti est donc écrit sur la <b>séance</b>, et la sortie
     * n'en garde aucune copie — ni ancienne, ni concurrente (cf. {@link #debriefTarget}).</p>
     */
    @Transactional
    public ActivityResponse updateForAthlete(UUID athleteId, UUID activityId,
                                             com.coachrun.dto.request.ActivityUpdateRequest request) {
        Activity a = ownedByAthlete(athleteId, activityId);

        if (request.title() != null) {
            a.setTitle(request.title().isBlank() ? null : request.title().trim());
        }
        applyDebrief(a, request);

        // Les mesures d'une montre ne se réécrivent pas : elles font foi, et la charge du coach
        // est calculée dessus. Une saisie manuelle, elle, n'a jamais été qu'une déclaration.
        if (a.getSource() == ActivitySource.MANUAL) {
            boolean movedOrResized = false;
            if (request.activityDate() != null && !request.activityDate().equals(a.getActivityDate())) {
                a.setActivityDate(request.activityDate());
                movedOrResized = true;
            }
            if (request.distanceM() != null) {
                a.setDistanceM(request.distanceM());
                movedOrResized = true;
            }
            if (request.durationS() != null) {
                a.setDurationS(request.durationS());
                movedOrResized = true;
            }
            if (request.elevationGainM() != null) {
                a.setElevationGainM(request.elevationGainM());
            }
            // Date ou volume corrigés : le rapprochement d'origine ne vaut plus. On détache la
            // séance (qui redevient à faire) et on relance la recherche sur les nouvelles valeurs.
            if (movedOrResized) {
                detachWorkout(a);
                autoMatch(athleteId, a);
            }
        }
        return toResponse(a);
    }

    // --- Le ressenti : un seul, porté par la séance dès qu'il y en a une -----------------------

    /**
     * Qui porte le ressenti de cette sortie : sa séance si elle en a une, elle-même sinon.
     *
     * <p>C'est la règle entière, et elle tient en une ligne parce qu'elle n'a qu'un critère : le
     * rapprochement. Tout le reste — quel écran a été ouvert, lequel des deux a été rempli en
     * premier — n'entre pas en compte, sans quoi on retomberait sur deux vérités.</p>
     */
    private Optional<Workout> debriefTarget(Activity a) {
        return a.getMatchedWorkoutId() == null
                ? Optional.empty()
                : workoutRepository.findById(a.getMatchedWorkoutId());
    }

    /**
     * Pose le ressenti là où il doit vivre, et nulle part ailleurs.
     *
     * <p>Sur une sortie rapprochée, il est écrit sur la séance et la sortie est <b>vidée</b> de
     * ce qu'elle en gardait : une copie qu'on ne lit plus est une copie qui ressortira un jour,
     * au détachement ou dans un export, en contredisant celle qui fait foi.</p>
     */
    private void applyDebrief(Activity a, com.coachrun.dto.request.ActivityUpdateRequest request) {
        Optional<Workout> workout = debriefTarget(a);
        if (workout.isPresent()) {
            writeDebrief(DebriefSink.of(workout.get()), a.getAthlete(), request);
            clearDebrief(a);
            return;
        }
        writeDebrief(DebriefSink.of(a), a.getAthlete(), request);
    }

    /**
     * Où se posent les six champs d'un ressenti, sans que l'écriture ait à savoir sur quoi.
     *
     * <p>La règle champ par champ — {@code null} vaut « inchangé », un drapeau {@code clear}
     * vaut « efface », la santé passe par le consentement — a été écrite deux fois, une pour la
     * séance et une pour la sortie. Deux copies d'une même règle finissent toujours par
     * diverger, et c'est exactement la divergence que tout ce code corrige. Il n'y en a plus
     * qu'une : seule la destination change.</p>
     */
    private record DebriefSink(
            java.util.function.Consumer<Integer> rpe,
            java.util.function.Consumer<Integer> feel,
            java.util.function.Consumer<Integer> fatigue,
            java.util.function.Consumer<Integer> pain,
            java.util.function.Consumer<String> comment,
            java.util.function.Consumer<String> injuriesJson) {

        static DebriefSink of(Workout w) {
            return new DebriefSink(w::setRpe, w::setFeel, w::setFatigue, w::setPain,
                    w::setAthleteComment, w::setInjuriesJson);
        }

        static DebriefSink of(Activity a) {
            return new DebriefSink(a::setRpe, a::setFeel, a::setFatigue, a::setPain,
                    a::setAthleteComment, a::setInjuriesJson);
        }
    }

    private void writeDebrief(DebriefSink sink, com.coachrun.entity.Athlete athlete,
                              com.coachrun.dto.request.ActivityUpdateRequest request) {
        if (Boolean.TRUE.equals(request.clearRpe())) {
            sink.rpe().accept(null);
        } else if (request.rpe() != null) {
            sink.rpe().accept(request.rpe());
        }
        if (Boolean.TRUE.equals(request.clearFeel())) {
            sink.feel().accept(null);
        } else if (request.feel() != null) {
            sink.feel().accept(request.feel());
        }
        if (Boolean.TRUE.equals(request.clearComment())) {
            sink.comment().accept(null);
        } else if (request.comment() != null) {
            sink.comment().accept(request.comment().isBlank() ? null : request.comment().trim());
        }
        // Fatigue, douleur et blessures : données de l'article 9. Sans consentement actif elles ne
        // sont pas enregistrées, mais la correction de la sortie aboutit.
        if (request.fatigue() != null) {
            sink.fatigue().accept(consentValidator.keepIfAllowed(athlete, request.fatigue()));
        }
        if (request.pain() != null) {
            sink.pain().accept(consentValidator.keepIfAllowed(athlete, request.pain()));
        }
        if (request.injuries() != null) {
            sink.injuriesJson().accept(consentValidator.isAllowed(athlete)
                    ? com.coachrun.util.InjuryCodec.write(request.injuries()) : null);
        }
    }

    /**
     * Un ressenti, détaché de ce qui le porte : les six champs, et rien d'autre.
     *
     * <p>Il n'existe que pour être <b>déplacé</b> d'une sortie vers sa séance et retour. Sans
     * lui, chaque geste — lire, vider, transférer dans un sens, puis dans l'autre — réénumérait
     * les six champs, soit autant d'occasions d'en oublier un le jour où un septième apparaît.
     * C'est ici, et ici seulement, que la liste est écrite.</p>
     */
    private record Debrief(Integer rpe, Integer feel, Integer fatigue, Integer pain,
                           String comment, String injuriesJson) {

        static final Debrief EMPTY = new Debrief(null, null, null, null, null, null);

        static Debrief of(Activity a) {
            return new Debrief(a.getRpe(), a.getFeel(), a.getFatigue(), a.getPain(),
                    a.getAthleteComment(), a.getInjuriesJson());
        }

        static Debrief of(Workout w) {
            return new Debrief(w.getRpe(), w.getFeel(), w.getFatigue(), w.getPain(),
                    w.getAthleteComment(), w.getInjuriesJson());
        }

        /** Rien n'a été déclaré. Un commentaire blanc n'est pas un commentaire. */
        boolean isEmpty() {
            return rpe == null && feel == null && fatigue == null && pain == null
                    && injuriesJson == null && (comment == null || comment.isBlank());
        }
    }

    private void write(DebriefSink sink, Debrief debrief) {
        sink.rpe().accept(debrief.rpe());
        sink.feel().accept(debrief.feel());
        sink.fatigue().accept(debrief.fatigue());
        sink.pain().accept(debrief.pain());
        sink.comment().accept(debrief.comment());
        sink.injuriesJson().accept(debrief.injuriesJson());
    }

    private void clearDebrief(Activity a) {
        write(DebriefSink.of(a), Debrief.EMPTY);
    }

    /**
     * Rapprochement : le ressenti déjà donné sur la sortie rejoint la séance.
     *
     * <p>Sans ce transfert, débriefer une sortie libre puis la voir rapprochée le lendemain
     * — c'est l'ordre habituel quand la montre se synchronise après coup — ferait disparaître le
     * ressenti de l'écran, puisque c'est désormais la séance qu'on lit. Il ne remplace jamais
     * celui de la séance : si l'athlète s'est déjà prononcé sur la séance, c'est elle qui a
     * raison, et la copie de la sortie est simplement effacée.</p>
     */
    private void adoptDebrief(Activity a, Workout w) {
        Debrief fromActivity = Debrief.of(a);
        if (!fromActivity.isEmpty() && Debrief.of(w).isEmpty()) {
            write(DebriefSink.of(w), fromActivity);
        }
        clearDebrief(a);
    }

    /**
     * Détachement : la sortie reprend le ressenti de la séance qu'elle quitte.
     *
     * <p>Symétrique du rapprochement, et pour la même raison : l'athlète a décrit un effort, cet
     * effort est celui de la sortie, et le détacher ne doit pas le lui reprendre.</p>
     */
    private void releaseDebrief(Activity a, Workout w) {
        Debrief fromWorkout = Debrief.of(w);
        if (Debrief.of(a).isEmpty() && !fromWorkout.isEmpty()) {
            write(DebriefSink.of(a), fromWorkout);
        }
    }

    /**
     * L'athlète supprime une de ses sorties (doublon, erreur de saisie, sortie de quelqu'un
     * d'autre importée par erreur). La séance rapprochée, s'il y en avait une, redevient à faire :
     * plus rien n'atteste qu'elle a été réalisée.
     *
     * @param neverImportAgain la sortie ne doit plus jamais revenir. Sans cela, supprimer une
     *                         sortie synchronisée ne dure que jusqu'à la synchro suivante, qui la
     *                         rapporte — l'athlète efface, Strava renvoie, il efface encore.
     */
    @Transactional
    public void deleteForAthlete(UUID athleteId, UUID activityId, boolean neverImportAgain) {
        Activity a = ownedByAthlete(athleteId, activityId);
        if (neverImportAgain) {
            excludeFromFutureImports(a);
        }
        detachWorkout(a);
        activityRepository.delete(a);
        log.info("Sortie supprimée {} par l'athlète {}{}", activityId, athleteId,
                neverImportAgain ? " (ne sera plus importée)" : "");
    }

    /**
     * Pose la pierre tombale qui empêchera le retour de cette sortie.
     *
     * <p>Sans identifiant externe, il n'y a rien à empêcher : une saisie manuelle ou un fichier
     * déposé à la main ne reviennent que si quelqu'un les redépose. Demander « ne plus jamais
     * importer » n'a alors pas d'objet, et inventer une clé pour honorer la case cochée créerait
     * une exclusion que rien ne consultera jamais.</p>
     *
     * <p>Le titre et la date sont recopiés : la sortie va disparaître, et l'écran des sorties
     * masquées doit pouvoir la nommer à quelqu'un qui voudrait revenir sur sa décision.</p>
     */
    private void excludeFromFutureImports(Activity a) {
        if (a.getExternalId() == null) {
            return;
        }
        if (exclusionRepository.existsByAthleteIdAndSourceAndExternalId(
                a.getAthlete().getId(), a.getSource(), a.getExternalId())) {
            return;
        }
        ActivityExclusion exclusion = new ActivityExclusion();
        exclusion.setAthlete(a.getAthlete());
        exclusion.setSource(a.getSource());
        exclusion.setExternalId(a.getExternalId());
        exclusion.setTitle(a.getTitle());
        exclusion.setActivityDate(a.getActivityDate());
        exclusionRepository.save(exclusion);
    }

    /**
     * Les sorties que cet athlète a écartées pour de bon.
     *
     * <p>Un masquage définitif sans moyen de le relire serait un piège : coché par erreur, il
     * retirerait une sortie sans qu'aucun écran ne dise pourquoi elle ne revient pas.</p>
     */
    public List<ActivityExclusionResponse> listExclusions(UUID athleteId) {
        return exclusionRepository.findByAthleteIdOrderByCreatedAtDesc(athleteId).stream()
                .map(ActivityExclusionResponse::from)
                .toList();
    }

    /**
     * L'athlète revient sur sa décision : la sortie pourra de nouveau être importée.
     *
     * <p>Elle ne réapparaît pas pour autant sur-le-champ — elle a été supprimée, et c'est la
     * prochaine synchronisation qui la rapportera. Encore faut-il qu'elle entre dans la fenêtre
     * que la synchro relit ; passé ce délai, il reste l'import manuel.</p>
     */
    @Transactional
    public void removeExclusion(UUID athleteId, UUID exclusionId) {
        ActivityExclusion exclusion = exclusionRepository.findByIdAndAthleteId(exclusionId, athleteId)
                .orElseThrow(() -> new NotFoundException("Sortie masquée introuvable."));
        exclusionRepository.delete(exclusion);
        log.info("Masquage levé {} par l'athlète {}", exclusionId, athleteId);
    }

    /** Rend sa séance à l'état « à faire » et détache l'activité. */
    private void detachWorkout(Activity a) {
        UUID workoutId = a.getMatchedWorkoutId();
        if (workoutId == null) {
            return;
        }
        workoutRepository.findById(workoutId).ifPresent(w -> {
            releaseDebrief(a, w);
            w.setStatus(WorkoutStatus.PLANNED);
        });
        a.setMatchedWorkoutId(null);
        a.setStatus(ActivityStatus.UNMATCHED);
    }

    private Activity ownedByAthlete(UUID athleteId, UUID activityId) {
        return activityRepository.findById(activityId)
                .filter(a -> a.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
    }

    // --- Lecture des tours -------------------------------------------------

    /** Tours d'une de mes activités (portail athlète). */
    public ActivityLapsResponse lapsForAthlete(UUID athleteId, UUID activityId,
                                               ActivityLapsResponse.Kind requested) {
        return laps(ownedByAthlete(athleteId, activityId), requested);
    }

    /** Tours d'une activité vue par le coach (scopé club). */
    public ActivityLapsResponse laps(UUID clubId, UUID activityId,
                                     ActivityLapsResponse.Kind requested) {
        return laps(require(clubId, activityId), requested);
    }

    /**
     * Tours de l'activité selon la découpe demandée, avec la liste de celles qu'elle sait produire.
     *
     * <p>Deux lectures d'une même sortie, jamais confondues : les tours de la montre sont les
     * répétitions telles qu'elles ont été courues, les splits kilométriques une découpe régulière.
     * Une sortie qui porte les deux mérite qu'on puisse basculer de l'une à l'autre — sur un
     * fractionné en côte, la comparaison est justement l'information.</p>
     *
     * <p>Sans découpe demandée, les tours de la montre priment ; le repli sur les splits fait
     * tout l'intérêt du dispositif en bêta, où les sorties déjà importées n'ont pas de tours en
     * base mais ont leur flux — elles se décortiquent donc sans réimport.</p>
     */
    private ActivityLapsResponse laps(Activity a, ActivityLapsResponse.Kind requested) {
        ActivityLapsResponse device = deviceLaps(a);
        java.util.List<ActivityLapsResponse.Lap> splits =
                com.coachrun.util.SplitCalculator.perKilometer(stream(a), a.getDistanceM());

        java.util.List<ActivityLapsResponse.Kind> available = new java.util.ArrayList<>();
        if (device != null && !device.laps().isEmpty()) {
            available.add(ActivityLapsResponse.Kind.DEVICE);
        }
        if (!splits.isEmpty()) {
            available.add(ActivityLapsResponse.Kind.SPLIT);
        }

        boolean wantsSplit = requested == ActivityLapsResponse.Kind.SPLIT && !splits.isEmpty();
        if (device == null || device.laps().isEmpty() || wantsSplit) {
            return new ActivityLapsResponse(ActivityLapsResponse.Kind.SPLIT, splits, available);
        }
        return device.withAvailable(available);
    }

    /** Tours relevés par la montre, ou {@code null} s'il n'y en a pas (ou s'ils sont illisibles). */
    private ActivityLapsResponse deviceLaps(Activity a) {
        if (a.getLapsJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(a.getLapsJson(), ActivityLapsResponse.class);
        } catch (Exception e) {
            log.warn("Tours illisibles pour l'activité {} : repli sur les splits", a.getId());
            return null;
        }
    }

    // --- Courbe de la sortie -----------------------------------------------

    /** Courbe d'une de mes activités (portail athlète). */
    public com.coachrun.dto.response.ActivityStreamResponse streamForAthlete(UUID athleteId,
                                                                            UUID activityId) {
        return curve(ownedByAthlete(athleteId, activityId));
    }

    /** Courbe d'une activité vue par le coach (scopé club). */
    public com.coachrun.dto.response.ActivityStreamResponse streamCurve(UUID clubId, UUID activityId) {
        return curve(require(clubId, activityId));
    }

    /**
     * Flux prêt à tracer : FC et allure <b>en fonction de la distance</b>, pas du temps.
     *
     * <p>Le flux stocké ne porte que le temps écoulé ; la distance s'intègre depuis l'allure, puis
     * se recale sur le total connu de la montre — même règle que les splits, pour que la courbe et
     * le tableau des tours placent le kilomètre 5 au même endroit. Lire un fractionné sur un axe
     * temporel écrase les récupérations et étire les répétitions : l'axe de distance est celui sur
     * lequel un coureur reconnaît sa séance.</p>
     */
    private com.coachrun.dto.response.ActivityStreamResponse curve(Activity a) {
        return com.coachrun.util.ActivityCurve.build(stream(a), a.getDistanceM());
    }

    /** Flux stocké de l'activité, ou liste vide (saisie manuelle, montre sans capteur). */
    private java.util.List<int[]> stream(Activity a) {
        if (a.getStreamJson() == null) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(a.getStreamJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<int[]>>() { });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /** Import d'un fichier FIT/GPX/TCX → activité + tracé, puis rapprochement automatique. */
    @Transactional
    public ActivityResponse importFile(UUID clubId, UUID athleteId, String filename, byte[] bytes,
                                       boolean confirmDuplicate) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return createFromFile(athlete, athleteId, filename, bytes, confirmDuplicate);
    }

    // --- Portail athlète : saisie / import par l'athlète sur ses propres données ---

    /** L'athlète enregistre une sortie libre (source toujours MANUAL). Scopé par son propre id. */
    @Transactional
    public ActivityResponse logForAthlete(UUID athleteId, ActivityImportRequest request) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        rejectIfDuplicate(athleteId, request.activityDate(), request.distanceM(), request.durationS(),
                Boolean.TRUE.equals(request.confirmDuplicate()));

        Activity activity = new Activity();
        activity.setClub(athlete.getClub());
        activity.setAthlete(athlete);
        activity.setSource(ActivitySource.MANUAL);
        activity.setActivityDate(request.activityDate());
        activity.setTitle(request.title());
        activity.setDistanceM(request.distanceM());
        activity.setDurationS(request.durationS());
        activity.setAvgHr(request.avgHr());
        activity.setElevationGainM(request.elevationGainM());
        activity.setStatus(ActivityStatus.IMPORTED);
        autoMatch(athleteId, activity);
        activity = activityRepository.save(activity);
        detectPhysio(activity);
        log.info("Sortie libre enregistrée {} (athlète={})", activity.getId(), athleteId);
        return toResponse(activity);
    }

    /** L'athlète importe sa propre trace (FIT/GPX/TCX). Scopé par son propre id. */
    @Transactional
    public ActivityResponse importFileForAthlete(UUID athleteId, String filename, byte[] bytes,
                                                 boolean confirmDuplicate) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return createFromFile(athlete, athleteId, filename, bytes, confirmDuplicate);
    }

    private ActivityResponse createFromFile(com.coachrun.entity.Athlete athlete, UUID athleteId,
                                            String filename, byte[] bytes, boolean confirmDuplicate) {
        com.coachrun.util.ActivityTrack.ParsedActivity parsed;
        try {
            parsed = com.coachrun.util.ActivityFileParser.parse(bytes);
        } catch (IllegalArgumentException ex) {
            throw new com.coachrun.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    ex.getMessage());
        }

        // Le cas le plus courant du doublon : ré-importer le même fichier, ou importer la trace
        // d'une sortie déjà remontée par la montre.
        rejectIfDuplicate(athleteId, parsed.date(), parsed.distanceM(), parsed.durationS(),
                confirmDuplicate);

        Activity activity = new Activity();
        activity.setClub(athlete.getClub());
        activity.setAthlete(athlete);
        activity.setSource(ActivitySource.FILE);
        activity.setActivityDate(parsed.date());
        activity.setTitle(filename != null
                ? filename.replaceAll("(?i)\\.(fit|gpx|tcx)$", "") : "Activité importée");
        activity.setDistanceM(parsed.distanceM());
        activity.setDurationS(parsed.durationS());
        activity.setElevationGainM(parsed.elevationGainM());
        // La FC moyenne : un FIT la porte dans son message de session, un GPX/TCX se déduit de
        // ses points. Sans elle, une sortie importée s'affichait sans cardio là où la même sortie
        // remontée par Strava en avait une.
        activity.setAvgHr(parsed.avgHr());
        activity.setStatus(ActivityStatus.IMPORTED);
        try {
            activity.setRouteJson(objectMapper.writeValueAsString(parsed.route()));
        } catch (Exception ignored) {
            // tracé optionnel
        }
        if (parsed.stream() != null && !parsed.stream().isEmpty()) {
            try {
                activity.setStreamJson(objectMapper.writeValueAsString(parsed.stream()));
            } catch (Exception ignored) {
                // flux optionnel
            }
        }
        // Les tours d'un TCX : c'est là que le fractionné est lisible tel qu'il a été couru.
        applyLaps(activity, parsed.laps());
        autoMatch(athleteId, activity);
        activity = activityRepository.save(activity);
        detectPhysio(activity);
        log.info("Activité importée par fichier {} ({} pts)", activity.getId(), parsed.route().size());
        return toResponse(activity);
    }

    /** Détail incluant le tracé GPS décodé. */
    public java.util.List<double[]> route(UUID clubId, UUID activityId) {
        Activity a = require(clubId, activityId);
        if (a.getRouteJson() == null) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readValue(a.getRouteJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<double[]>>() { });
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /**
     * Séance rapprochée dont le ressenti manque encore, à proposer à l'ouverture du portail —
     * ou {@code null} s'il n'y a rien à confirmer.
     *
     * <p>Le rapprochement passe la séance en COMPLETED/PARTIAL : elle disparaît donc du bandeau
     * « retours en attente », qui ne regarde que les séances non clôturées. Sans ce prompt, une
     * séance importée depuis la montre ne produirait jamais de signal de forme — c'est la fuite
     * exacte que l'auto-détection vient boucher.</p>
     */
    /**
     * Prochaine sortie à débriefer, rattachée à une séance <b>ou non</b>.
     *
     * <p>La proposition ne portait que sur les sorties rapprochées d'une séance prescrite. Une
     * course, un footing improvisé — précisément ce qui n'a pas de prescription à comparer, donc
     * ce sur quoi le coach n'a rien d'autre à lire — n'était jamais proposé : l'athlète devait
     * penser à aller ouvrir sa sortie et à y saisir son ressenti lui-même.</p>
     *
     * <p>Une sortie sans séance revient avec {@code workout} nul : l'écran demande alors le
     * ressenti sur la sortie elle-même (RPE + mot au coach), qu'elle porte désormais.</p>
     */
    public FeedbackPromptResponse pendingFeedbackPrompt(UUID athleteId) {
        for (Activity a : activityRepository
                .findByAthleteIdAndFeedbackPromptedAtIsNullOrderByActivityDateDesc(athleteId)) {
            if (a.getMatchedWorkoutId() == null) {
                // Déjà noté sur la sortie = l'athlète s'est exprimé, on ne le relance pas.
                if (a.getRpe() == null && a.getAthleteComment() == null) {
                    return FeedbackPromptResponse.of(a, null);
                }
                continue;
            }
            Workout w = workoutRepository.findByIdAndAthleteId(a.getMatchedWorkoutId(), athleteId).orElse(null);
            if (w != null && w.getRpe() == null && w.getAthleteComment() == null) {
                return FeedbackPromptResponse.of(a, w);
            }
        }
        return null;
    }

    /**
     * Marque l'invitation comme vue : la feuille pré-remplie ne s'ouvre qu'une fois, qu'elle
     * ait été remplie ou écartée. Rouvrir une feuille refusée à chaque lancement transformerait
     * une bonne idée en pop-up subie.
     */
    @Transactional
    public void ackFeedbackPrompt(UUID athleteId, UUID activityId) {
        activityRepository.findById(activityId)
                .filter(a -> a.getAthlete().getId().equals(athleteId))
                .ifPresent(a -> a.setFeedbackPromptedAt(java.time.Instant.now()));
    }

    @Transactional
    public ActivityResponse matchManually(UUID clubId, UUID activityId, UUID workoutId) {
        Activity activity = require(clubId, activityId);
        return relink(activity, clubId, workoutId);
    }

    /**
     * Rapprochement manuel — variante athlète-scopée. L'algorithme se trompe (deux sorties le même
     * jour, une séance déplacée) et son erreur était définitive : l'athlète est souvent le premier
     * à la voir, et le seul disponible le dimanche soir.
     */
    @Transactional
    public ActivityResponse matchForAthlete(UUID athleteId, UUID activityId, UUID workoutId) {
        Activity activity = activityRepository.findById(activityId)
                .filter(a -> a.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
        UUID clubId = activity.getClub().getId();
        if (workoutId != null) {
            // La séance visée doit être la sienne : un athlète ne rattache pas sa sortie à la
            // séance d'un camarade de club.
            workoutRepository.findById(workoutId)
                    .filter(w -> w.getAthlete().getId().equals(athleteId))
                    .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        }
        return relink(activity, clubId, workoutId);
    }

    @Transactional
    public ActivityResponse unmatch(UUID clubId, UUID activityId) {
        return relink(require(clubId, activityId), clubId, null);
    }

    /**
     * Détache l'activité de sa séance actuelle puis la rattache à {@code workoutId}
     * ({@code null} = simple détachement). Les deux statuts sont recalculés : sans le
     * détachement, l'ancienne séance restait COMPLETED alors que plus rien ne l'atteste.
     */
    private ActivityResponse relink(Activity activity, UUID clubId, UUID workoutId) {
        UUID previous = activity.getMatchedWorkoutId();
        if (previous != null && !previous.equals(workoutId)) {
            workoutRepository.findByIdAndClubId(previous, clubId).ifPresent(w -> {
                // La sortie récupère le ressenti de la séance qu'elle quitte — sans quoi le
                // détachement effacerait, sans le dire, ce que l'athlète avait décrit.
                releaseDebrief(activity, w);
                w.setStatus(WorkoutStatus.PLANNED);
            });
        }
        if (workoutId == null) {
            activity.setMatchedWorkoutId(null);
            activity.setStatus(ActivityStatus.UNMATCHED);
            return toResponse(activity);
        }
        Workout workout = workoutRepository.findByIdAndClubId(workoutId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        link(activity, workout);
        return toResponse(activity);
    }


    /**
     * Regarde si la sortie qui vient d'arriver dit quelque chose du niveau de l'athlète.
     *
     * <p>Elle ne met rien à jour : elle dépose au plus une proposition, que le coach validera ou
     * non. Et un échec de détection ne fait jamais échouer l'import — une sortie qui n'entre pas en
     * base parce qu'un calcul de VDOT a levé une exception serait un très mauvais échange.</p>
     */
    private void detectPhysio(Activity activity) {
        try {
            PhysioDetectionService detector = physioDetection.getIfAvailable();
            if (detector != null) {
                detector.inspect(activity);
            }
        } catch (RuntimeException e) {
            log.warn("Détection de niveau impossible sur l'activité {} : {}",
                    activity.getId(), e.getMessage());
        }
    }

    private void autoMatch(UUID athleteId, Activity activity) {
        List<Workout> window = workoutRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        athleteId, activity.getActivityDate().minusDays(1), activity.getActivityDate().plusDays(1));
        // Une séance qui porte déjà une sortie n'est plus disponible : sans ce tri, la deuxième
        // sortie de la journée viendrait déloger la première.
        Set<UUID> taken = window.isEmpty() ? Set.of()
                : new HashSet<>(activityRepository.findMatchedWorkoutIdsIn(
                        window.stream().map(Workout::getId).toList()));
        List<Workout> candidates = window.stream().filter(w -> !taken.contains(w.getId())).toList();

        Optional<Workout> best = matchingService.findBestMatch(activity, candidates);
        if (best.isEmpty()) {
            best = loneSessionOfTheDay(activity, window, taken);
        }
        if (best.isPresent()) {
            link(activity, best.get());
        } else {
            activity.setStatus(ActivityStatus.UNMATCHED);
        }
    }

    /**
     * Repli : la seule séance de la journée, quand rien ne permet de la comparer à la sortie.
     *
     * <p>La garde décisive se pose <b>ici</b> et pas dans l'algorithme de score, parce qu'elle
     * demande de connaître la journée entière — séances déjà rapprochées comprises. Compter les
     * seules séances encore libres reviendrait à dire « la seule séance <i>restante</i> », ce qui
     * n'est pas la même chose : une journée à deux séances dont la première est déjà prise
     * laisserait la seconde attraper n'importe quelle sortie, alors que le choix y est justement
     * arbitraire.</p>
     */
    private Optional<Workout> loneSessionOfTheDay(Activity activity, List<Workout> window,
                                                  Set<UUID> taken) {
        List<Workout> sameDay = window.stream()
                .filter(w -> activity.getActivityDate().equals(w.getScheduledDate()))
                .toList();
        if (sameDay.size() != 1) {
            return Optional.empty();
        }
        Workout only = sameDay.get(0);
        return !taken.contains(only.getId()) && matchingService.canFallBackOn(activity, only)
                ? Optional.of(only) : Optional.empty();
    }

    private void link(Activity activity, Workout workout) {
        activity.setMatchedWorkoutId(workout.getId());
        activity.setStatus(ActivityStatus.MATCHED);
        // À partir d'ici, le ressenti est celui de la séance : celui de la sortie la rejoint.
        adoptDebrief(activity, workout);
        // Le statut ne se déduit des chiffres que si l'athlète ne s'est pas déjà prononcé : il a
        // couru la séance, pas nous. Sa déclaration — « faite », « écourtée » — prime sur un
        // écart de distance, qui ignore la façon dont la sortie a été enregistrée (montre coupée,
        // échauffement compté à part) et n'a jamais eu vocation à contredire quelqu'un.
        if (workout.getStatus() == WorkoutStatus.PLANNED) {
            workout.setStatus(matchingService.resolvedStatus(activity, workout));
        }
    }

    /** Activité réalisée rapprochée d'une séance (ou {@code null}), avec les écarts prévu/réalisé. */
    public ActivityResponse getForWorkout(UUID clubId, UUID athleteId, UUID workoutId) {
        return activityRepository.findByClubIdAndAthleteIdAndMatchedWorkoutId(clubId, athleteId, workoutId)
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * Ma sortie rapprochée d'une de mes séances (ou {@code null}). Scopée par mon seul athleteId :
     * le portail n'a pas de clubId à porter, et n'en a pas besoin — une activité appartient à un
     * athlète, et c'est cette appartenance qui est vérifiée.
     */
    public ActivityResponse getForWorkoutOfAthlete(UUID athleteId, UUID workoutId) {
        return activityRepository.findByAthleteIdAndMatchedWorkoutId(athleteId, workoutId)
                .map(this::toResponse)
                .orElse(null);
    }

    private Activity require(UUID clubId, UUID activityId) {
        return activityRepository.findByIdAndClubId(activityId, clubId)
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
    }

    /**
     * Passage obligé de toute lecture d'une sortie — et c'est ce qui rend la règle tenable : le
     * ressenti rendu est celui de la séance rapprochée, jamais une seconde version.
     *
     * <p>Repli sur celui de la sortie quand la séance n'en porte aucun : les sorties rapprochées
     * <b>avant</b> cette règle gardent une copie en base, et la faire disparaître de l'écran
     * parce qu'on a changé de porteur serait exactement le défaut qu'on corrige.</p>
     */
    private ActivityResponse toResponse(Activity activity) {
        if (activity.getMatchedWorkoutId() == null) {
            return ActivityResponse.from(activity);
        }
        return workoutRepository.findById(activity.getMatchedWorkoutId())
                .map(w -> ActivityResponse.of(activity, Debrief.of(w).isEmpty() ? null : w,
                        delta(activity.getDistanceM(), w.getTargetDistanceM()),
                        delta(activity.getDurationS(), w.getTargetDurationS())))
                .orElseGet(() -> ActivityResponse.from(activity));
    }

    private Integer delta(Integer actual, Integer target) {
        return (actual == null || target == null) ? null : actual - target;
    }
}
