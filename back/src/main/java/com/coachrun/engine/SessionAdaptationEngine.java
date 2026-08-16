package com.coachrun.engine;

import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.enums.FormStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Le feu vert du matin : que faire de la séance du jour, compte tenu de l'état déclaré et de la
 * charge&nbsp;?
 *
 * <h2>Ce que ce moteur fait, et surtout ce qu'il ne fait pas</h2>
 *
 * <p>Il <b>recommande</b> et sait <b>fabriquer</b> la version allégée d'une séance. Il n'écrit
 * rien : la structure adaptée qu'il rend est un objet en mémoire, que seul un humain acceptant une
 * proposition fera persister. C'est l'invariant du produit — un plan est l'engagement d'un coach,
 * pas une variable que l'application ajuste pendant la nuit.</p>
 *
 * <h2>Les règles</h2>
 *
 * <p>Elles suivent la hiérarchie que tout coach applique de tête, et dans cet ordre :</p>
 * <ol>
 *   <li><b>La douleur commande.</b> Une douleur ne se traite pas en enlevant des kilomètres : elle
 *       se traite en enlevant les allures rapides. Douleur ≥ 5 ⇒ on retire l'intensité ; ≥ 7, ou
 *       douleur ≥ 5 sur une séance déjà dure, ⇒ on ne court pas.</li>
 *   <li><b>La fatigue réduit le volume, pas les allures.</b> Courir un 1000 m trop lentement
 *       n'entraîne pas la qualité visée : mieux vaut en faire quatre justes que six approximatifs.</li>
 *   <li><b>La charge est un signal de second rang.</b> Un ACWR élevé ne justifie d'alléger que si
 *       la séance du jour est elle-même dure — accumuler du footing facile ne blesse personne.</li>
 * </ol>
 *
 * <p>Le RPE n'entre jamais dans la décision, comme il n'entre jamais dans l'état de forme
 * ({@link FormStatusEngine}) : c'est la mesure d'un effort passé, pas d'un état présent.</p>
 *
 * <p>Logique pure, testable sans contexte Spring.</p>
 */
@Component
public class SessionAdaptationEngine {

    /** Ce que le moteur conseille de faire de la séance. */
    public enum Advice {
        /** Rien à signaler : la séance tient debout. */
        KEEP,
        /** Même séance, moins de volume, mêmes allures. */
        LIGHTEN,
        /** Même durée, plus d'intensité : la séance devient un footing. */
        EASY,
        /** Ne pas courir aujourd'hui — décaler. */
        MOVE
    }

    /**
     * @param advice    la conduite conseillée
     * @param title     une phrase affichable telle quelle, à l'athlète comme au coach
     * @param reason    les chiffres qui l'ont déclenchée, en clair — sans eux, accepter revient à
     *                  faire confiance à une boîte noire
     * @param severity  {@code GREEN} / {@code ORANGE} / {@code RED}, pour la pastille
     */
    public record Recommendation(Advice advice, String title, String reason, FormStatus severity) {
    }

    /** Fatigue à partir de laquelle le volume se discute. */
    private static final int FATIGUE_LIGHTEN = 7;
    /** Douleur à partir de laquelle l'intensité se retire. */
    private static final int PAIN_EASY = 5;
    /** Douleur à partir de laquelle on ne court pas. */
    private static final int PAIN_STOP = 7;
    /** ACWR au-delà duquel une séance dure devient discutable. */
    private static final double ACWR_WATCH = 1.4;
    /** RPE prescrit à partir duquel une séance est « dure ». */
    private static final int HARD_RPE = 7;

    /**
     * @param fatigue     fatigue déclarée le matin (0–10), ou {@code null}
     * @param pain        douleur déclarée le matin (0–10), ou {@code null}
     * @param acwr        ratio de charge, ou {@code null} tant qu'il n'est pas publiable
     * @param sessionRpe  RPE le plus élevé prescrit dans la séance, ou {@code null}
     * @param hasSession  faux s'il n'y a rien de prévu aujourd'hui — on ne conseille alors rien
     */
    public Recommendation recommend(Integer fatigue, Integer pain, Double acwr,
                                    Integer sessionRpe, boolean hasSession) {
        if (!hasSession) {
            return new Recommendation(Advice.KEEP, "Repos aujourd'hui.",
                    null, FormStatus.GREEN);
        }
        int f = fatigue == null ? 0 : fatigue;
        int p = pain == null ? 0 : pain;
        boolean hard = sessionRpe != null && sessionRpe >= HARD_RPE;

        // 1. La douleur, d'abord — et elle ne se compense pas par du volume en moins.
        if (p >= PAIN_STOP || (p >= PAIN_EASY && hard)) {
            return new Recommendation(Advice.MOVE,
                    "Ne cours pas aujourd'hui.",
                    "Douleur " + p + "/10" + (hard ? " sur une séance intense." : "."),
                    FormStatus.RED);
        }
        if (p >= PAIN_EASY) {
            return new Recommendation(Advice.EASY,
                    "Séance à faire en footing.",
                    "Douleur " + p + "/10 : on garde la durée, on retire les allures rapides.",
                    FormStatus.RED);
        }

        // 2. La fatigue réduit le volume, jamais les allures.
        if (f >= 9 && hard) {
            return new Recommendation(Advice.MOVE,
                    "Séance à décaler.",
                    "Fatigue " + f + "/10 sur une séance intense : elle ne donnera pas ce qu'elle doit donner.",
                    FormStatus.RED);
        }
        if (f >= FATIGUE_LIGHTEN) {
            return new Recommendation(Advice.LIGHTEN,
                    "Séance à alléger.",
                    "Fatigue " + f + "/10 : moins de volume, mêmes allures.",
                    FormStatus.ORANGE);
        }

        // 3. La charge, seulement si la séance du jour est elle-même dure.
        if (acwr != null && acwr > ACWR_WATCH && hard) {
            return new Recommendation(Advice.LIGHTEN,
                    "Séance à alléger.",
                    "Charge " + pct(acwr) + " au-dessus de ton habituel, sur une séance intense.",
                    FormStatus.ORANGE);
        }
        if (p >= 3) {
            // Une gêne ne justifie pas de toucher à la séance, mais elle mérite d'être dite :
            // l'athlète qui la déclare attend au moins un accusé de réception.
            return new Recommendation(Advice.KEEP, "Séance maintenue.",
                    "Gêne " + p + "/10 signalée : préviens ton coach si elle augmente pendant la séance.",
                    FormStatus.ORANGE);
        }
        return new Recommendation(Advice.KEEP, "Feu vert.", null, FormStatus.GREEN);
    }

    /** « 40 % » depuis un ACWR de 1,4 — la formulation que l'athlète comprend sans glossaire. */
    private String pct(double acwr) {
        return Math.round((acwr - 1) * 100) + " %";
    }

    // --- Fabrication de la séance adaptée -------------------------------------

    /**
     * Version allégée d'une séance : le corps de séance perd environ un tiers de son volume, les
     * <b>allures prescrites ne bougent pas</b>. Échauffement et retour au calme sont laissés tels
     * quels — les raccourcir n'allège rien et dégrade la séance.
     *
     * <p>La réduction porte d'abord sur les répétitions (un 6 × 1000 devient un 4 × 1000, ce qu'un
     * coach écrirait), et seulement à défaut sur la distance ou la durée du bloc — réduire un
     * footing de 60 à 40 minutes est correct, réduire chaque répétition de 1000 à 660 m ne l'est
     * pas.</p>
     */
    public SessionStructure lighten(SessionStructure source, double keepFactor) {
        if (source == null) {
            return SessionStructure.empty();
        }
        List<CourseBlock> main = new ArrayList<>();
        for (CourseBlock b : source.main()) {
            main.add(shrink(b, keepFactor));
        }
        return new SessionStructure(source.warmup(), main, source.cooldown());
    }

    /** Réduction par défaut : on garde deux tiers du volume. */
    public SessionStructure lighten(SessionStructure source) {
        return lighten(source, 2.0 / 3.0);
    }

    private CourseBlock shrink(CourseBlock b, double factor) {
        Integer reps = b.reps();
        if (reps != null && reps > 1) {
            int reduced = Math.max(1, (int) Math.round(reps * factor));
            // Réduire de zéro répétition n'allège rien : on en retire au moins une.
            if (reduced == reps) {
                reduced = reps - 1;
            }
            return withVolume(b, reduced, b.distanceM(), b.durationS());
        }
        Integer distance = b.distanceM() == null ? null : Math.max(200, (int) Math.round(b.distanceM() * factor));
        Integer duration = b.durationS() == null ? null : Math.max(300, (int) Math.round(b.durationS() * factor));
        return withVolume(b, b.reps(), distance, duration);
    }

    /**
     * Séance transformée en footing : l'échauffement et le retour au calme survivent, le corps de
     * séance devient <b>un seul bloc continu</b> de la même durée totale, à l'allure de
     * l'échauffement.
     *
     * <p>Reprendre la prescription de l'échauffement plutôt que de n'en mettre aucune est ce qui
     * distingue un footing prescrit d'un bloc sans cible : l'échauffement d'une séance est par
     * construction l'allure facile de cet athlète-là, déjà calculée pour lui.</p>
     *
     * @param mainDurationS durée estimée du corps de séance, telle que le calculateur l'a établie
     */
    public SessionStructure easy(SessionStructure source, Integer mainDurationS) {
        if (source == null) {
            return SessionStructure.empty();
        }
        var warmupRx = source.warmup().stream()
                .map(CourseBlock::prescription)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);

        int duration = mainDurationS != null && mainDurationS > 0 ? mainDurationS : estimateMainDuration(source);
        CourseBlock easy = new CourseBlock(
                java.util.UUID.randomUUID().toString(), "easy", 1, null, duration,
                warmupRx, null, 3,
                "Intensité retirée : séance transformée en footing.",
                List.of(), 1, null);

        return new SessionStructure(source.warmup(), List.of(easy), source.cooldown());
    }

    /** Durée du corps de séance quand le calculateur n'a rien à dire (prescription en distance seule). */
    private int estimateMainDuration(SessionStructure source) {
        int total = 0;
        for (CourseBlock b : source.main()) {
            if (b.durationS() != null) {
                total += b.durationS() * b.repCount() * b.setCount();
            }
        }
        return total > 0 ? total : 2400; // 40 min : la durée d'un footing par défaut
    }

    private CourseBlock withVolume(CourseBlock b, Integer reps, Integer distanceM, Integer durationS) {
        return new CourseBlock(b.id(), b.type(), reps, distanceM, durationS,
                b.prescription(), b.recovery(), b.rpe(), b.note(), b.drillIds(),
                b.sets(), b.setRecovery());
    }
}
