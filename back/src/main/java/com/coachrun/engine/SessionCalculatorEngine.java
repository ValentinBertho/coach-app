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
 *
 * <p>Le RPE, lui, ne dépend d'aucune mesure : il se dérive du référentiel et du pourcentage
 * <b>prescrits</b> (cf. {@link #domainForPrescription}). Ce moteur ne dépend donc plus de
 * {@link IntensityDomainEngine}, qui reclassifie une vitesse contre des seuils mesurés — utile pour
 * juger un effort réalisé, inapplicable à une prescription destinée à un athlète sans test.</p>
 */
@Component
public class SessionCalculatorEngine {

    /** Profil de l'athlète nécessaire au calcul (seuils + allures d'équivalence VDOT). */
    public record AthletePaceContext(
            Double lt1Ms, Double lt2Ms, Double vcMs,
            Integer fcLt1, Integer fcLt2, Integer fcMax,
            double fcDomain1Pct, double fcDomain2Pct,
            Integer pace800S, Integer pace1500S, Integer pace3000S, Integer pace5kmS,
            Integer pace10kmS, Integer pace15kmS, Integer paceSemiS, Integer paceMarathonS,
            Integer hrRest,
            /** VMA en km/h (ancre de zone {@code VMA}). */
            Double vmaKmh
    ) {
    }

    /** Allure de base (s/km) associée à un référentiel pour cet athlète — réutilisable (règles de zones). */
    public Integer basePaceFor(PrescriptionRef ref, AthletePaceContext ctx) {
        return resolveBasePace(ref, ctx);
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

        int meanPace = (paceFast + paceSlow) / 2;

        // RPE estimé d'après le domaine de la prescription (référentiel + %), et non d'après une
        // reclassification de l'allure obtenue. Voir domainForPrescription : reclassifier exigeait
        // LT1 et LT2 mesurés, que la grande majorité des athlètes n'a pas.
        int[] rpe = rpeBand(domainForPrescription(in.ref(), in.minPct(), in.maxPct()));

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

    /**
     * Chemin Z3 : cible <b>lue</b> directement depuis les valeurs de zone de l'athlète
     * ({@code AthleteZoneValue}) — plus de base × %. La fourchette d'allure pilote vitesse, durée et
     * distance estimées ; la FC et le RPE sont repris tels quels s'ils sont fournis.
     *
     * @param paceFastS allure la plus rapide (min s/km) ; {@code null} si la zone n'a pas d'allure.
     * @param paceSlowS allure la plus lente (max s/km).
     */
    public Result calculateFromZone(Integer paceFastS, Integer paceSlowS,
                                    Integer hrLow, Integer hrHigh, Integer rpeMin, Integer rpeMax,
                                    Integer reps, Integer distanceM, Integer durationS) {
        if (paceFastS == null || paceSlowS == null || paceFastS <= 0 || paceSlowS <= 0) {
            // Sans allure cible, le bloc n'est pas chiffrable (la zone reste « à renseigner »).
            return new Result(false, null, null, null, null, null, null, null, null,
                    hrLow, hrHigh, rpeMin, rpeMax, null, null, false);
        }
        int paceFast = Math.min(paceFastS, paceSlowS);
        int paceSlow = Math.max(paceFastS, paceSlowS);

        double speedFastKmh = round1(PaceUtil.secPerKmToKmh(paceFast));
        double speedSlowKmh = round1(PaceUtil.secPerKmToKmh(paceSlow));

        int meanPace = (paceFast + paceSlow) / 2;
        Integer estimatedDistanceM = null;
        Integer estimatedDurationS = null;
        int r = reps == null || reps <= 0 ? 1 : reps;
        if (distanceM != null && distanceM > 0) {
            estimatedDistanceM = distanceM * r;
            estimatedDurationS = (int) Math.round(estimatedDistanceM / 1000.0 * meanPace);
        } else if (durationS != null && durationS > 0) {
            estimatedDurationS = durationS * r;
            estimatedDistanceM = (int) Math.round(estimatedDurationS / (double) meanPace * 1000.0);
        }

        return new Result(true, null, null,
                paceFast, paceSlow,
                PaceUtil.formatPace(paceFast), PaceUtil.formatPace(paceSlow),
                speedSlowKmh, speedFastKmh,
                hrLow, hrHigh, rpeMin, rpeMax,
                estimatedDurationS, estimatedDistanceM, false);
    }

    /** Vrai si la prescription vise un seuil (LT1/LT2/VC) non mesuré → allure dérivée du VDOT. */
    private boolean isEstimatedThreshold(PrescriptionRef ref, AthletePaceContext c) {
        return switch (ref) {
            case PCT_LT1 -> c.lt1Ms() == null;
            case PCT_LT2 -> c.lt2Ms() == null;
            case PCT_VC -> c.vcMs() == null;
            case PCT_VMA -> c.vmaKmh() == null;
            default -> false;
        };
    }

    // --- Helpers --------------------------------------------------------------

    /** Allure (s/km) à 100 % d'une VMA exprimée en km/h ; null si absente. */
    private Integer vmaPace(Double vmaKmh) {
        return (vmaKmh == null || vmaKmh <= 0) ? null : (int) Math.round(3600.0 / vmaKmh);
    }

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
            // VMA : allure à 100 % de la VMA (km/h → s/km) ; à défaut ≈ allure 3000 m (Daniels).
            case PCT_VMA -> firstNonNull(vmaPace(c.vmaKmh()), c.pace3000S(), c.pace5kmS());
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

    /**
     * Vitesse de chaque référentiel exprimée en fraction de la vitesse au <b>LT2</b>.
     *
     * <p>Ces rapports sont les mêmes équivalences physiologiques que celles déjà utilisées par
     * {@link #resolveBasePace} pour le repli VDOT (Daniels) : LT1 ≈ allure marathon, LT2 ≈ allure
     * d'environ une heure de course (10–15 km), VC et VMA au-dessus. Ils ne dépendent d'aucune
     * mesure propre à l'athlète — c'est tout l'intérêt : la <b>prescription</b> porte son domaine,
     * quel que soit l'état du profil.</p>
     */
    private static final java.util.Map<PrescriptionRef, Double> SPEED_RATIO_TO_LT2 =
            java.util.Map.ofEntries(
                    java.util.Map.entry(PrescriptionRef.PCT_LT1, 0.94),
                    java.util.Map.entry(PrescriptionRef.PCT_LT2, 1.00),
                    java.util.Map.entry(PrescriptionRef.PCT_VC, 1.05),
                    java.util.Map.entry(PrescriptionRef.PCT_VMA, 1.09),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_MARATHON, 0.94),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_SEMI, 0.98),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_15KM, 1.00),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_10KM, 1.02),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_5KM, 1.06),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_3000M, 1.09),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_1500M, 1.15),
                    java.util.Map.entry(PrescriptionRef.PCT_PACE_800M, 1.22));

    /** Rapport LT1/LT2 en vitesse : le LT1 se situe autour de l'allure marathon. */
    private static final double LT1_OVER_LT2 = 0.94;

    /**
     * Domaine d'intensité <b>de la prescription</b>, dérivé du référentiel et du pourcentage visés.
     *
     * <p><b>Pourquoi ce chemin existe.</b> Le RPE affiché passait auparavant par
     * {@link IntensityDomainEngine#classify}, qui reclassifie une <em>vitesse</em> contre les seuils
     * <em>mesurés</em> de l'athlète. Sa branche physiologique exige {@code lt1Ms} et {@code lt2Ms}
     * non nuls, sa branche de repli exige une fréquence cardiaque que ce chemin n'a pas — l'appel
     * passait {@code null}. Tout athlète sans test lactate tombait donc sur le défaut conservatif
     * {@code DOMAIN_1}, et lisait « RPE 2–4 » sur la totalité de ses séances, y compris un 10×400 m
     * prescrit par son coach à RPE 8. L'allure, elle, était juste : seul le RPE mentait, et rien ne
     * le signalait.</p>
     *
     * <p><b>Le principe retenu.</b> Un bloc à 105 % de VMA est en domaine 3 par construction, quel
     * que soit l'état du profil de l'athlète : c'est l'intention du coach qui porte l'intensité, pas
     * la mesure. La vitesse visée est ramenée à une fraction du LT2 via
     * {@link #SPEED_RATIO_TO_LT2}, puis comparée aux deux frontières (LT1 puis LT2). Effet de bord
     * bienvenu : deux athlètes recevant la même prescription lisent le même RPE, ce qu'un coach
     * attend d'une consigne d'effort perçu.</p>
     *
     * @param minPct borne basse de la fourchette prescrite (% du référentiel)
     * @param maxPct borne haute de la fourchette prescrite
     */
    public IntensityDomain domainForPrescription(PrescriptionRef ref, double minPct, double maxPct) {
        Double ratio = ref == null ? null : SPEED_RATIO_TO_LT2.get(ref);
        if (ratio == null) {
            return IntensityDomain.DOMAIN_1;
        }
        // La fourchette est jugée sur son milieu : c'est aussi ce qui sert au volume estimé.
        double meanPct = (minPct + maxPct) / 2.0;
        double speedOverLt2 = ratio * meanPct / 100.0;

        if (speedOverLt2 < LT1_OVER_LT2) {
            return IntensityDomain.DOMAIN_1;
        }
        return speedOverLt2 <= 1.0 ? IntensityDomain.DOMAIN_2 : IntensityDomain.DOMAIN_3;
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
