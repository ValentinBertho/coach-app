package com.coachrun.service;

import com.coachrun.engine.SessionCalculatorEngine;
import com.coachrun.engine.SessionCalculatorEngine.AthletePaceContext;
import com.coachrun.engine.PaceUtil;
import com.coachrun.entity.AthleteZoneValue;
import com.coachrun.entity.MetricType;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.PrescriptionRef;
import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneValueSource;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Pré-remplit les valeurs de zones d'un athlète (source AUTO) à partir de la <b>règle</b> portée par
 * chaque {@code ZoneMetric} (ancre + fourchette %) et des valeurs de référence de l'athlète.
 * Cf. PROPOSITION-ZONES-ET-EDITEUR-V2 §3.3 (chantier zones v2).
 *
 * <p>La règle est désormais une <b>donnée</b> (éditable, traçable), plus une table Java figée. Les
 * ancres d'allure (LT1/LT2/VC/allures VDOT) se résolvent via le moteur ; les ancres de FC
 * (FCMAX/LTHR/HRR) se calculent directement. Les valeurs MANUAL ou verrouillées ne sont jamais
 * écrasées.</p>
 */
@Service
@RequiredArgsConstructor
public class ZoneValueSyncService {

    private final SessionCalculatorService calculatorService;
    private final SessionCalculatorEngine engine;
    private final AthleteZoneValueRepository valueRepository;
    private final TrainingZoneRepository zoneRepository;
    private final TrainingZoneSeedService seedService;
    private final ZoneSetService zoneSetService;
    private final AthleteRepository athleteRepository;
    private final ClubRepository clubRepository;

    /** (Re)génère les valeurs AUTO non verrouillées de l'athlète depuis les règles. Idempotent. */
    @Transactional
    public int resync(UUID clubId, UUID athleteId) {
        AthletePaceContext ctx = calculatorService.contextFor(clubId, athleteId);

        if (!zoneRepository.existsByClubId(clubId)) {
            seedService.seedDefaultsIfEmpty(clubRepository.getReferenceById(clubId));
        }
        // L'échelle est celle du modèle de zones de l'athlète (à défaut, le jeu par défaut du club) :
        // deux athlètes du même club peuvent travailler sur des zones différentes.
        List<TrainingZone> zones = zoneSetService.zonesForAthlete(clubId, athleteId);
        var athlete = athleteRepository.getReferenceById(athleteId);

        int written = 0;
        for (TrainingZone zone : zones) {
            for (ZoneMetric zm : zone.getMetrics()) {
                double[] range = computeRange(zm, ctx);
                if (range == null) {
                    continue;
                }
                if (upsertAuto(athlete, zone, zm.getMetricType(), athleteId, range)) {
                    written++;
                }
            }
        }
        return written;
    }

    /** Calcule la fourchette [min, max] d'un couple (zone, métrique) depuis sa règle, ou null. */
    double[] computeRange(ZoneMetric zm, AthletePaceContext ctx) {
        String code = zm.getMetricType().getCode();
        Double low = zm.getLowPct();
        Double high = zm.getHighPct();
        // RPE : cible fixe (bornes absolues 1–10), identique pour tous les athlètes, sans ancre
        // physiologique. La règle stocke les bornes RPE directement dans low/high.
        if ("RPE".equals(code)) {
            if (low == null || high == null || low <= 0 || high <= 0) {
                return null;
            }
            return new double[]{Math.round(low), Math.round(high)};
        }
        ZoneAnchor anchor = zm.getAnchor();
        ZoneAnchor highAnchor = zm.effectiveHighAnchor();
        if (anchor == null || low == null || high == null || low <= 0 || high <= 0) {
            return null;
        }
        return switch (code) {
            case "PACE", "SPEED" -> {
                // Chaque borne se calcule depuis SON ancre : c'est ce qui permet à une zone
                // d'enjamber la frontière LT1 → LT2 en collant exactement à ses deux voisines.
                Integer slowBase = basePaceOf(anchor, ctx);
                Integer fastBase = basePaceOf(highAnchor, ctx);
                if (slowBase == null || fastBase == null) yield null;
                int fast = (int) Math.round(fastBase / (high / 100.0)); // % haut ⇒ plus rapide
                int slow = (int) Math.round(slowBase / (low / 100.0));
                if (fast >= slow) yield null; // règle incohérente : bornes croisées
                if ("PACE".equals(code)) yield new double[]{fast, slow};
                // SPEED : vitesse mini (allure lente) → maxi (allure rapide)
                yield new double[]{round1(PaceUtil.secPerKmToKmh(slow)), round1(PaceUtil.secPerKmToKmh(fast))};
            }
            case "HR", "PCT_HRMAX" -> {
                double[] hr = hrRange(anchor, low, high, ctx);
                if (hr == null) yield null;
                if ("HR".equals(code)) yield hr;
                // PCT_HRMAX : convertit en % de FCmax si dispo, sinon les % de la règle.
                if (anchor == ZoneAnchor.FCMAX) yield new double[]{low, high};
                if (ctx.fcMax() == null || ctx.fcMax() <= 0) yield null;
                yield new double[]{Math.round(100.0 * hr[0] / ctx.fcMax()), Math.round(100.0 * hr[1] / ctx.fcMax())};
            }
            default -> null; // POWER, RPE : pas de règle → saisie manuelle.
        };
    }

    /** Allure de référence d'une ancre (s/km), ou null si l'ancre n'est pas une ancre d'allure. */
    private Integer basePaceOf(ZoneAnchor anchor, AthletePaceContext ctx) {
        PrescriptionRef ref = paceRefOf(anchor);
        if (ref == null) {
            return null;
        }
        Integer base = engine.basePaceFor(ref, ctx);
        return (base == null || base <= 0) ? null : base;
    }

    private double[] hrRange(ZoneAnchor anchor, double low, double high, AthletePaceContext ctx) {
        return switch (anchor) {
            case FCMAX -> ctx.fcMax() == null ? null
                    : new double[]{Math.round(ctx.fcMax() * low / 100.0), Math.round(ctx.fcMax() * high / 100.0)};
            case LTHR -> ctx.fcLt2() == null ? null
                    : new double[]{Math.round(ctx.fcLt2() * low / 100.0), Math.round(ctx.fcLt2() * high / 100.0)};
            case HRR -> (ctx.fcMax() == null || ctx.hrRest() == null) ? null
                    : new double[]{
                        Math.round(ctx.hrRest() + (ctx.fcMax() - ctx.hrRest()) * low / 100.0),
                        Math.round(ctx.hrRest() + (ctx.fcMax() - ctx.hrRest()) * high / 100.0)};
            default -> null; // ancre d'allure avec métrique FC → incohérent.
        };
    }

    /** Mappe une ancre d'allure vers le référentiel du moteur ; null pour les ancres de FC. */
    private PrescriptionRef paceRefOf(ZoneAnchor a) {
        return switch (a) {
            case LT1 -> PrescriptionRef.PCT_LT1;
            case LT2 -> PrescriptionRef.PCT_LT2;
            case VC -> PrescriptionRef.PCT_VC;
            case VMA -> PrescriptionRef.PCT_VMA;
            case PACE_800M -> PrescriptionRef.PCT_PACE_800M;
            case PACE_1500M -> PrescriptionRef.PCT_PACE_1500M;
            case PACE_3000M -> PrescriptionRef.PCT_PACE_3000M;
            case PACE_5KM -> PrescriptionRef.PCT_PACE_5KM;
            case PACE_10KM -> PrescriptionRef.PCT_PACE_10KM;
            case PACE_15KM -> PrescriptionRef.PCT_PACE_15KM;
            case PACE_SEMI -> PrescriptionRef.PCT_PACE_SEMI;
            case PACE_MARATHON -> PrescriptionRef.PCT_PACE_MARATHON;
            case FCMAX, LTHR, HRR -> null;
        };
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /** Écrit une valeur AUTO ; ne touche jamais une valeur MANUAL ou verrouillée. */
    private boolean upsertAuto(com.coachrun.entity.Athlete athlete, TrainingZone zone,
                               MetricType metric, UUID athleteId, double[] range) {
        AthleteZoneValue value = valueRepository
                .findByAthleteIdAndZoneIdAndMetricTypeId(athleteId, zone.getId(), metric.getId())
                .orElse(null);
        if (value != null && (value.getSource() == ZoneValueSource.MANUAL || value.isLocked())) {
            return false;
        }
        if (value == null) {
            value = new AthleteZoneValue();
            value.setAthlete(athlete);
            value.setZone(zone);
            value.setMetricType(metric);
        }
        value.setValueMin(range[0]);
        value.setValueMax(range[1]);
        value.setSource(ZoneValueSource.AUTO);
        valueRepository.save(value);
        return true;
    }
}
