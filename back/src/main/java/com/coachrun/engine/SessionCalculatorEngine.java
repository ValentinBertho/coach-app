package com.coachrun.engine;

import com.coachrun.entity.enums.IntensityDomain;
import com.coachrun.entity.enums.PrescriptionRef;
import org.springframework.stereotype.Component;

/**
 * Calculateur de séance (cf. DARI Lab — « calculateur automatique »). À partir d'une prescription
 * en fourchette (référentiel + % min/max) et du profil de l'athlète (seuils physio + allures VDOT),
 * produit les cibles d'entraînement : allure, vitesse, FC, RPE estimé, durée et distance.
 *
 * <p>Convention DARI Lab : un % plus élevé = effort plus rapide (moins de secondes/km).
 * La FC cible est estimée par interpolation linéaire FC↔vitesse ancrée sur LT1/LT2 (modèle
 * sous-maximal classique) ; elle reste nulle si les ancrages FC manquent.</p>
 */
@Component
public class SessionCalculatorEngine {

    private final IntensityDomainEngine domainEngine;

    public SessionCalculatorEngine(IntensityDomainEngine domainEngine) {
        this.domainEngine = domainEngine;
    }

    /** Profil de l'athlète nécessaire au calcul (seuils + allures d'équivalence VDOT). */
    public record AthletePaceContext(
            Double lt1Ms, Double lt2Ms, Double vcMs,
            Integer fcLt1, Integer fcLt2, Integer fcMax,
            double fcDomain1Pct, double fcDomain2Pct,
            Integer pace800S, Integer pace1500S, Integer pace3000S, Integer pace5kmS,
            Integer pace10kmS, Integer pace15kmS, Integer paceSemiS, Integer paceMarathonS
    ) {
    }

    /** Prescription d'un bloc : référentiel, fourchette en %, et volume (reps/distance/durée). */
    public record PrescriptionInput(
            PrescriptionRef ref, double minPct, double maxPct,
            Integer reps, Integer distanceM, Integer durationS
    ) {
    }

    /** Cibles calculées pour l'athlète. {@code computable=false} si le référentiel est absent du profil. */
    public record Result(
            boolean computable,
            PrescriptionRef ref,
            Integer basePaceSecPerKm,
            Integer paceMinSecPerKm, Integer paceMaxSecPerKm,
            String paceMinLabel, String paceMaxLabel,
            Double speedMinKmh, Double speedMaxKmh,
            Integer hrMin, Integer hrMax,
            Integer rpeMin, Integer rpeMax,
            Integer estimatedDurationS, Integer estimatedDistanceM,
            /** Allure dérivée du VDOT faute de seuil mesuré (test lactate) — à afficher comme « estimée ». */
            boolean paceEstimated
    ) {
        static Result notComputable(PrescriptionRef ref) {
            return new Result(false, ref, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, false);
        }
    }

    public Result calculate(PrescriptionInput in, AthletePaceContext ctx) {
        Integer basePace = resolveBasePace(in.ref(), ctx);
        if (basePace == null || basePace <= 0 || in.minPct() <= 0 || in.maxPct() <= 0) {
            return Result.notComputable(in.ref());
        }

        // % plus élevé ⇒ plus rapide ⇒ moins de secondes/km.
        int paceFast = (int) Math.round(basePace / (in.maxPct() / 100.0));   // allure mini (rapide)
        int paceSlow = (int) Math.round(basePace / (in.minPct() / 100.0));   // allure maxi (lente)

        double speedFastKmh = round1(PaceUtil.secPerKmToKmh(paceFast));
        double speedSlowKmh = round1(PaceUtil.secPerKmToKmh(paceSlow));

        // FC : effort rapide ⇒ FC haute, effort lent ⇒ FC basse.
        Integer hrHigh = hrForSpeed(PaceUtil.secPerKmToMs(paceFast), ctx);
        Integer hrLow = hrForSpeed(PaceUtil.secPerKmToMs(paceSlow), ctx);

        // RPE estimé d'après le domaine de l'allure moyenne.
        int meanPace = (paceFast + paceSlow) / 2;
        double meanSpeedMs = PaceUtil.secPerKmToMs(meanPace);
        IntensityDomain domain = domainEngine.classify(meanSpeedMs, null,
                ctx.lt1Ms(), ctx.lt2Ms(), ctx.fcMax(), ctx.fcDomain1Pct(), ctx.fcDomain2Pct());
        int[] rpe = rpeBand(domain);

        // Durée / distance estimées du bloc.
        Integer estimatedDistanceM = null;
        Integer estimatedDurationS = null;
        int reps = in.reps() == null || in.reps() <= 0 ? 1 : in.reps();
        if (in.distanceM() != null && in.distanceM() > 0) {
            estimatedDistanceM = in.distanceM() * reps;
            estimatedDurationS = (int) Math.round(estimatedDistanceM / 1000.0 * meanPace);
        } else if (in.durationS() != null && in.durationS() > 0) {
            estimatedDurationS = in.durationS() * reps;
            estimatedDistanceM = (int) Math.round(estimatedDurationS / (double) meanPace * 1000.0);
        }

        return new Result(true, in.ref(), basePace,
                paceFast, paceSlow,
                PaceUtil.formatPace(paceFast), PaceUtil.formatPace(paceSlow),
                speedSlowKmh, speedFastKmh,
                hrLow, hrHigh,
                rpe[0], rpe[1],
                estimatedDurationS, estimatedDistanceM,
                isEstimatedThreshold(in.ref(), ctx));
    }

    /** Vrai si la prescription vise un seuil (LT1/LT2/VC) non mesuré → allure dérivée du VDOT. */
    private boolean isEstimatedThreshold(PrescriptionRef ref, AthletePaceContext c) {
        return switch (ref) {
            case PCT_LT1 -> c.lt1Ms() == null;
            case PCT_LT2 -> c.lt2Ms() == null;
            case PCT_VC -> c.vcMs() == null;
            default -> false;
        };
    }

    // --- Helpers --------------------------------------------------------------

    private Integer resolveBasePace(PrescriptionRef ref, AthletePaceContext c) {
        return switch (ref) {
            // Seuils : valeur mesurée (test lactate) prioritaire ; à défaut, repli sur des
            // équivalents dérivés du VDOT (allures de course) pour que l'athlète ait malgré tout
            // des allures de travail. Correspondances physiologiques (Daniels) :
            //  - LT1 (seuil aérobie)  ≈ allure marathon ;
            //  - LT2 (seuil lactique) ≈ allure ~1 h de course (10–15 km / semi) ;
            //  - VC  (vitesse critique) ≈ allure 3–5 km.
            case PCT_LT1 -> firstNonNull(toPace(c.lt1Ms()),
                    c.paceMarathonS(), c.paceSemiS(), c.pace10kmS());
            case PCT_LT2 -> firstNonNull(toPace(c.lt2Ms()),
                    c.pace10kmS(), c.pace15kmS(), c.paceSemiS(), c.pace5kmS());
            case PCT_VC -> firstNonNull(toPace(c.vcMs()),
                    c.pace3000S(), c.pace5kmS(), c.pace10kmS());
            case PCT_PACE_800M -> c.pace800S();
            case PCT_PACE_1500M -> c.pace1500S();
            case PCT_PACE_3000M -> c.pace3000S();
            case PCT_PACE_5KM -> c.pace5kmS();
            case PCT_PACE_10KM -> c.pace10kmS();
            case PCT_PACE_15KM -> c.pace15kmS();
            case PCT_PACE_SEMI -> c.paceSemiS();
            case PCT_PACE_MARATHON -> c.paceMarathonS();
        };
    }

    /** Première valeur non nulle et positive (priorité mesurée → repli VDOT). */
    private Integer firstNonNull(Integer... candidates) {
        for (Integer v : candidates) {
            if (v != null && v > 0) {
                return v;
            }
        }
        return null;
    }

    private Integer toPace(Double ms) {
        return (ms == null || ms <= 0) ? null : (int) Math.round(PaceUtil.msToSecPerKm(ms));
    }

    /** FC estimée pour une vitesse, par interpolation linéaire ancrée sur LT1/LT2 (sinon null). */
    private Integer hrForSpeed(double speedMs, AthletePaceContext c) {
        if (c.lt1Ms() == null || c.lt2Ms() == null || c.fcLt1() == null || c.fcLt2() == null
                || c.lt2Ms().equals(c.lt1Ms())) {
            return null;
        }
        double slope = (c.fcLt2() - c.fcLt1()) / (c.lt2Ms() - c.lt1Ms());
        double hr = c.fcLt1() + slope * (speedMs - c.lt1Ms());
        if (c.fcMax() != null) {
            hr = Math.min(hr, c.fcMax());
        }
        hr = Math.max(hr, 60);
        return (int) Math.round(hr);
    }

    private int[] rpeBand(IntensityDomain domain) {
        return switch (domain) {
            case DOMAIN_1 -> new int[]{2, 4};
            case DOMAIN_2 -> new int[]{5, 7};
            case DOMAIN_3 -> new int[]{7, 9};
        };
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
