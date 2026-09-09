package com.coachrun.service;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.util.StravaAutoName;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Rapprochement prévu/réalisé (algorithme isolé et testable, cf. Cahier-des-charges §8).
 * Score = combinaison du sport, de la proximité de date, du volume et du titre ; rapprochement
 * automatique au-dessus du seuil {@link #MATCH_THRESHOLD}.
 */
@Service
public class MatchingService {

    /** Seuil de confiance minimal pour un rapprochement automatique. */
    public static final double MATCH_THRESHOLD = 0.6;
    /** Tolérance de distance pour considérer la séance COMPLETED (sinon PARTIAL). */
    private static final double COMPLETED_DISTANCE_TOLERANCE = 0.15;

    /**
     * Poids du rapprochement des titres. Il s'<b>ajoute</b> au score, jamais ne s'en retranche :
     * deux titres qui ne se ressemblent pas ne prouvent rien (Strava nomme lui-même la plupart des
     * sorties), alors que deux titres qui se ressemblent prouvent beaucoup.
     */
    private static final double TITLE_BONUS = 0.25;

    /**
     * Meilleure séance correspondant à l'activité, si le score dépasse le seuil.
     *
     * <h2>Pourquoi une séance déjà notée reste rapprochable</h2>
     *
     * <p>Le tri ne retenait que les séances {@code PLANNED}. Or l'ordre naturel des choses est
     * l'inverse : on court, on note sa séance dans l'application en rentrant — elle passe donc
     * {@code COMPLETED} — <b>et la montre se synchronise après</b>. À l'arrivée, la sortie ne
     * trouvait plus aucune séance à qui se rattacher : elle restait « à rattacher », la séance
     * restait sans réalisé, et la comparaison prévu/réalisé n'existait pour ni l'un ni l'autre.
     * C'est le « je ne retrouve plus les séances une fois qu'elles ont été faites » remonté en
     * bêta, et le webhook — qui accélère la synchro sans changer son rang dans la séquence — ne
     * l'aurait pas corrigé.</p>
     *
     * <p>Une séance {@code MISSED} reste écartée, elle : l'athlète a déclaré ne pas l'avoir faite,
     * et une sortie du même jour ne doit pas contredire cette déclaration. C'est ce qui distingue
     * les deux cas — l'un complète une déclaration, l'autre la nierait.</p>
     *
     * <h2>Pourquoi une séance de demain n'est jamais candidate</h2>
     *
     * <p>La proximité de date était symétrique : une séance de la veille et une séance du
     * lendemain valaient le même demi-point. Or les deux situations n'ont rien à voir. Rattacher
     * la sortie d'aujourd'hui à la séance d'hier décrit un fait ordinaire — on a couru en retard,
     * ou la montre a basculé après minuit. La rattacher à la séance de <b>demain</b> affirme qu'on
     * a déjà fait ce qui n'a pas encore eu lieu.</p>
     *
     * <p>Et l'écart de date se rattrapait trop facilement : un jour d'écart ne coûte que deux
     * dixièmes du score final, qu'un volume presque identique récupère largement. Une sortie de
     * 11,35 km en 57:45 se rapprochait donc de la séance du lendemain prévue à 11,32 km en 57:30
     * (score 0,80) plutôt que de celle du jour même dont les cibles étaient mal renseignées
     * (0,50) : la séance du jour restait sans réalisé, et celle du lendemain était déclarée faite
     * avant d'avoir été courue — avec, dans la foulée, un « Repos demain » le soir venu.</p>
     *
     * <p>Le rapprochement <b>manuel</b> reste possible dans les deux sens : l'athlète qui avance
     * réellement sa séance du samedi au vendredi la rattache lui-même. C'est un geste délibéré, ce
     * que l'automatisme ne doit pas faire à sa place.</p>
     *
     * @param candidates séances de la fenêtre, <b>déjà débarrassées</b> de celles qui portent une
     *                   activité mieux placée : le tri ici ne sait pas ce qui est déjà rapproché
     */
    public Optional<Workout> findBestMatch(Activity activity, List<Workout> candidates) {
        return candidates.stream()
                .filter(w -> eligible(activity, w))
                .map(w -> new Scored(w, score(activity, w)))
                .filter(s -> s.score >= MATCH_THRESHOLD)
                .max((a, b) -> Double.compare(a.score, b.score))
                .map(s -> s.workout);
    }

    /**
     * Cette séance peut-elle, dans l'absolu, accueillir cette sortie ? Question de nature, posée
     * avant toute question de degré : ce que le score n'a pas à trancher parce que c'est déjà
     * tranché.
     */
    private boolean eligible(Activity activity, Workout workout) {
        return workout.getStatus() != WorkoutStatus.MISSED
                && workout.getType() != WorkoutType.REST
                && sportAllows(activity, workout)
                && !travelledNothing(activity, workout)
                && !isAfterActivity(workout, activity);
    }

    /**
     * La sortie n'a pas parcouru un mètre, alors que la séance en prescrit des milliers.
     *
     * <p>C'est une contradiction, pas une donnée manquante — et c'est ainsi qu'elle était lue :
     * {@code closeness} rendait {@code null} pour toute valeur nulle, si bien que le score se
     * rabattait sur la seule durée, où 29 minutes contre 59 prévues valent encore un demi-point.
     * Une séance de renforcement passait ainsi le seuil face à un fractionné de treize
     * kilomètres.</p>
     *
     * <p>Une distance simplement <b>absente</b> ({@code null}) reste, elle, une donnée manquante :
     * une saisie manuelle qui n'indique qu'une durée n'affirme rien sur la distance parcourue.</p>
     */
    private boolean travelledNothing(Activity activity, Workout workout) {
        return activity.getDistanceM() != null && activity.getDistanceM() == 0
                && com.coachrun.util.PlannedVolume.usableDistanceM(workout.getTargetDistanceM()) != null;
    }

    /**
     * Le sport déclaré par la source permet-il ce rapprochement ?
     *
     * <h2>Le défaut que cette garde corrige</h2>
     *
     * <p>Une sortie n'était, pour l'algorithme, qu'une date, une distance et une durée. Sur une
     * journée à cinq sorties — un footing, un fractionné, une sortie gravel et une séance de
     * renforcement — c'est la <b>musculation</b> qui a emporté le fractionné prescrit : 29 minutes
     * contre 59 prévues faisaient une proximité de 0,49, la distance nulle était lue comme
     * <i>absente</i> plutôt que comme incompatible, et le score montait à 0,75. Le fractionné
     * réellement couru — dont la montre n'avait enregistré que la partie rapide, 6,2 km sur les
     * 13,3 prévus — plafonnait à 0,72 et restait « non rattachée ».</p>
     *
     * <p>Le sport arrivait pourtant déjà : Strava l'envoie, le FIT le déclare, le TCX le porte en
     * attribut. Il était lu puis jeté.</p>
     *
     * <h2>Ce que la garde n'affirme pas</h2>
     *
     * <p>Un sport {@code null} — saisie manuelle, sortie importée avant l'arrivée de la colonne —
     * ne bloque rien. L'absence de déclaration n'est pas une déclaration d'incompatibilité, et
     * refuser tout ce qui n'est pas explicitement de la course reviendrait à casser le
     * rapprochement pour tout l'historique.</p>
     */
    private boolean sportAllows(Activity activity, Workout workout) {
        ActivitySport sport = activity.getSport();
        WorkoutType type = workout.getType();
        // Le type d'une séance en base ne peut pas être nul ; celui d'une séance qu'on vient de
        // construire, si — et un `switch` sur un nul lève, là où l'on veut seulement ne rien
        // affirmer.
        if (sport == null || type == null) {
            return true;
        }
        return switch (type) {
            // Une séance de renforcement se fait en salle : c'est la seule où la musculation est
            // le réalisé attendu, et la seule où une distance nulle est normale.
            case STRENGTH -> sport == ActivitySport.STRENGTH;
            // Le cross-training est précisément l'endroit où le sport n'est pas prescrit.
            case CROSS_TRAINING -> true;
            case REST -> false;
            // Tout le reste est de la course à pied. La marche y est admise : une sortie longue
            // en côte alterne les deux, et une montre en range parfois la totalité en « walk ».
            default -> sport.isFootborne();
        };
    }

    /**
     * Repli de dernier recours : cette séance peut-elle accueillir la sortie faute de mieux ?
     *
     * <p><b>Pourquoi ce repli existe.</b> Une séance dont le volume prévu n'est pas exploitable —
     * une prescription écrite en durée dont aucun bloc n'est chiffrable, qui ne totalise que ses
     * éducatifs — n'a rien à opposer à l'activité : le score tombe à zéro et la sortie du jour
     * restait orpheline, à côté d'une séance qui restait sans réalisé. Deux écrans vides pour un
     * entraînement qui a bel et bien eu lieu.</p>
     *
     * <p><b>Pourquoi il est aussi étroit.</b> La date seule ne prouve rien : c'est précisément ce
     * qui avait fait retirer le rapprochement sur date. Quatre gardes le rendent acceptable — la
     * séance doit être celle du <b>jour même</b> (pas de la veille), ne pas avoir été déclarée
     * non faite, accepter le <b>sport</b> de la sortie, et n'avoir <b>rien de comparable</b> à
     * opposer : deux volumes qui se contredisent restent un refus, ils portent une information.
     * L'appelant y ajoute la garde décisive — il faut que ce soit la <b>seule séance de la
     * journée</b>, prises comprises, sans quoi le choix redevient arbitraire.</p>
     *
     * <p>Le statut retenu reste {@code PARTIAL} : la sortie a eu lieu, rien ne prouve qu'elle
     * correspond à la séance prescrite, et c'est à l'athlète ou au coach de trancher.</p>
     */
    public boolean canFallBackOn(Activity activity, Workout workout) {
        return workout.getStatus() != WorkoutStatus.MISSED
                && workout.getType() != WorkoutType.REST
                && sportAllows(activity, workout)
                && workout.getScheduledDate() != null
                && workout.getScheduledDate().equals(activity.getActivityDate())
                && hasNothingComparable(activity, workout);
    }

    /** Ni distance ni durée exploitables du côté de la séance : il n'y a rien à confronter. */
    private boolean hasNothingComparable(Activity activity, Workout workout) {
        return distanceCloseness(activity, workout) == null
                && durationCloseness(activity, workout) == null;
    }

    /** La séance est-elle prévue après la sortie ? On ne peut pas avoir déjà fait ce qui vient. */
    private boolean isAfterActivity(Workout workout, Activity activity) {
        return workout.getScheduledDate() != null && activity.getActivityDate() != null
                && workout.getScheduledDate().isAfter(activity.getActivityDate());
    }

    /**
     * Statut résultant d'un rapprochement, selon l'écart au prévu.
     *
     * <p>Sans cible mesurable, le statut retenu était {@code COMPLETED} : n'importe quelle activité
     * du jour validait donc une séance dont on ne pouvait rien vérifier. On retient désormais
     * {@code PARTIAL} — la sortie a bien eu lieu, mais rien ne prouve qu'elle correspond à la
     * séance prescrite, et c'est à l'athlète ou au coach de trancher.</p>
     *
     * <p><b>Ce que ce calcul ne dit pas.</b> Il compare des volumes, pas des structures : un
     * 10 × 400 m remplacé par un footing de même distance ressort « réalisé ». Comparer la
     * structure demanderait le découpage réel de l'activité, que l'import ne fournit pas
     * aujourd'hui ; en attendant, le ressenti de l'athlète reste la seule source qui distingue
     * les deux.</p>
     */
    public WorkoutStatus resolvedStatus(Activity activity, Workout workout) {
        // Même lecture que le rapprochement : un total sous le plancher n'est pas une cible, et
        // ne peut donc pas servir à déclarer une séance réalisée.
        Integer target = com.coachrun.util.PlannedVolume.usableDistanceM(workout.getTargetDistanceM());
        Integer actual = activity.getDistanceM();
        if (target == null || target == 0 || actual == null) {
            return WorkoutStatus.PARTIAL;
        }
        double ratio = Math.abs(actual - target) / (double) target;
        return ratio <= COMPLETED_DISTANCE_TOLERANCE ? WorkoutStatus.COMPLETED : WorkoutStatus.PARTIAL;
    }

    /**
     * Confiance du rapprochement de cette sortie avec cette séance, {@code 0} si elle n'y a pas
     * droit du tout. Publique pour que l'appelant puisse <b>comparer deux prétendantes</b> à la
     * même séance : sans cela, la première sortie importée gardait la séance quelle que soit celle
     * qui arrivait ensuite, et l'ordre de synchronisation décidait à la place des chiffres.
     */
    public double confidence(Activity activity, Workout workout) {
        return eligible(activity, workout) ? score(activity, workout) : 0.0;
    }

    /**
     * Score de rapprochement : proximité de date, de volume (distance <strong>et</strong> durée),
     * plus une prime de ressemblance des titres.
     *
     * <p>Sans la durée, une sortie de 10 km en 40 min et une séance prévue de 10 km en 60 min
     * obtenaient un score parfait — alors que ce sont deux séances différentes. Le poids se
     * répartit sur les critères effectivement comparables : une séance sans durée cible reste
     * rapprochable sur la date et la distance, comme avant.</p>
     *
     * <p>La prime de titre peut porter le total au-delà de 1 : c'est un <b>rang</b>, pas une
     * probabilité, et seul l'ordre entre candidates compte une fois le seuil franchi.</p>
     */
    private double score(Activity activity, Workout workout) {
        // Écart en jours, toujours positif : les séances postérieures à la sortie sont écartées
        // en amont par findBestMatch, ce score ne juge donc que le passé et le jour même.
        long dayGap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                workout.getScheduledDate(), activity.getActivityDate()));
        double dateScore = switch ((int) Math.min(dayGap, 2)) {
            case 0 -> 1.0;
            case 1 -> 0.6;
            default -> 0.0;
        };

        double titleScore = titleCloseness(activity, workout);
        Double distScore = distanceCloseness(activity, workout);
        Double durationScore = durationCloseness(activity, workout);
        if (distScore == null && durationScore == null) {
            // Rien de comparable : la date seule ne prouve rien. Se fier à elle rapprochait
            // automatiquement n'importe quelle sortie du jour de n'importe quelle séance sans
            // cible — et la validait au passage. On laisse le rapprochement à la main.
            return 0.0;
        }

        // La date pèse la moitié ; le reste se partage entre les mesures disponibles.
        double effortScore = distScore == null ? durationScore
                : durationScore == null ? distScore
                : (distScore + durationScore) / 2.0;
        return 0.5 * dateScore + 0.5 * effortScore + TITLE_BONUS * titleScore;
    }

    /**
     * Proximité des distances, ou {@code null} si la séance n'a pas de distance exploitable.
     *
     * <p>Un total de séance sous le plancher de {@link com.coachrun.util.PlannedVolume} n'est pas
     * une cible : c'est ce qui reste quand le calcul n'a su convertir que les éducatifs. Le
     * comparer à une sortie de onze kilomètres produisait une proximité de 0,009 — un chiffre qui
     * n'infirme rien mais qui écrase le score, au point de faire perdre la séance du jour contre
     * celle du lendemain.</p>
     *
     * <p>Une sortie qui n'a pas parcouru un mètre face à une séance chiffrée en kilomètres n'est
     * pas traitée ici mais en amont, par {@link #travelledNothing} : c'est une contradiction, pas
     * une proximité faible, et elle doit écarter la séance plutôt que d'entrer dans une moyenne
     * où la durée la rattraperait.</p>
     */
    private Double distanceCloseness(Activity activity, Workout workout) {
        return closeness(com.coachrun.util.PlannedVolume.usableDistanceM(workout.getTargetDistanceM()),
                activity.getDistanceM());
    }

    private Double durationCloseness(Activity activity, Workout workout) {
        return closeness(com.coachrun.util.PlannedVolume.usableDurationS(workout.getTargetDurationS()),
                activity.getDurationS());
    }

    /** Ratio de proximité min/max ∈ [0,1], ou null si non comparable. */
    private Double closeness(Integer a, Integer b) {
        if (a == null || b == null || a == 0 || b == 0) {
            return null;
        }
        return (double) Math.min(a, b) / Math.max(a, b);
    }

    // --- Ressemblance des titres ---------------------------------------------

    /**
     * Ce que les deux titres ont en commun, ∈ [0,1].
     *
     * <h2>Pourquoi le titre est un si bon indice</h2>
     *
     * <p>Un athlète qui nomme sa sortie recopie le nom de sa séance : « 8*(200/400) » en face de
     * « Endurance · Séance 8x(200/400) ». Aucun volume ne dit cela aussi clairement — surtout
     * quand la montre n'a enregistré que la partie rapide du fractionné, et que les chiffres,
     * eux, désignent le footing d'échauffement enregistré à part.</p>
     *
     * <h2>Pourquoi il ne peut que rapporter des points</h2>
     *
     * <p>La plupart des sorties portent un nom que Strava a composé — « Afternoon Gravel Ride » —
     * et qui ne dit rien de la séance. Un titre qui ne ressemble à rien ne prouve donc rien, et ne
     * doit rien retirer ; c'est pour cela que la prime s'ajoute au lieu d'entrer dans la moyenne.
     * Les noms générés par Strava sont écartés d'emblée (cf. {@link StravaAutoName}) : « Afternoon
     * Run » ressemblerait à toutes les séances de course de la semaine.</p>
     */
    private double titleCloseness(Activity activity, Workout workout) {
        String activityTitle = activity.getTitle();
        if (activityTitle == null || StravaAutoName.isAutoGenerated(activityTitle)) {
            return 0.0;
        }
        Set<String> left = signature(activityTitle);
        Set<String> right = signature(workout.getTitle());
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new LinkedHashSet<>(left);
        shared.retainAll(right);
        if (shared.isEmpty()) {
            return 0.0;
        }
        // Un seul mot commun ne suffit que s'il est distinctif : « 400 » désigne une séance,
        // « long » désigne la moitié du calendrier.
        if (shared.size() == 1 && !distinctive(shared.iterator().next())) {
            return 0.0;
        }
        // Recouvrement plutôt que Jaccard : « 8*(200/400) » est contenu dans « Séance
        // 8x(200/400) », et le titre le plus court ne doit pas être puni d'être court.
        return (double) shared.size() / Math.min(left.size(), right.size());
    }

    /** Un mot qui identifie une séance : il porte un chiffre, et pas seulement un ou deux signes. */
    private boolean distinctive(String token) {
        return token.length() >= 2 && token.chars().anyMatch(Character::isDigit);
    }

    /**
     * Mots significatifs d'un titre, normalisés. Les accents, la casse et la ponctuation
     * disparaissent ; l'astérisque devient un « x » pour que « 8*(200/400) » et « 8x(200/400) »
     * s'écrivent pareil.
     */
    private Set<String> signature(String title) {
        if (title == null || title.isBlank()) {
            return Set.of();
        }
        String flat = Normalizer.normalize(title.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('*', 'x')
                .replace('×', 'x')
                .replaceAll("[^a-z0-9]+", " ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : flat.trim().split(" +")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * Mots qui ne distinguent aucune séance d'une autre. Volontairement court : un mot rare mais
     * partagé (« côtes », « fartlek ») est exactement ce qu'on cherche, et retirer trop de mots
     * ferait ressembler entre elles des séances qui n'ont rien à voir.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "seance", "sortie", "entrainement", "de", "du", "des", "la", "le", "les", "l", "d",
            "et", "en", "au", "aux", "a", "un", "une", "avec", "sur", "pour", "x", "m", "km",
            "run", "course", "pied", "running");

    private record Scored(Workout workout, double score) {
    }
}
