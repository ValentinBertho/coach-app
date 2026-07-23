package com.coachrun.service;

import com.coachrun.engine.SessionCalculatorEngine;
import com.coachrun.engine.SessionCalculatorEngine.AthletePaceContext;
import com.coachrun.engine.SessionCalculatorEngine.PrescriptionInput;
import com.coachrun.engine.SessionCalculatorEngine.Result;
import com.coachrun.entity.AthleteZoneValue;
import com.coachrun.entity.MetricType;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.PrescriptionRef;
import com.coachrun.entity.enums.ZoneValueSource;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pré-remplit les valeurs de zones d'un athlète (source AUTO) à partir du moteur physio existant
 * (VDOT / seuils LT1-LT2-VC) — le socle de pré-remplissage façon Nolio. Cf. §3.5.
 *
 * <p>Correspondance zone standard → prescription (référentiel + bande %), puis lecture directe des
 * cibles du moteur pour chaque métrique portée. Les valeurs MANUAL ou verrouillées ne sont jamais
 * écrasées. Les zones custom (hors table de correspondance) restent à renseigner manuellement.</p>
 */
@Service
@RequiredArgsConstructor
public class ZoneValueSyncService {

    /** Table de correspondance versionnée : nom de zone standard → (référentiel, %min, %max). */
    private record ZoneRx(PrescriptionRef ref, double minPct, double maxPct) {
    }

    private static final Map<String, ZoneRx> MAPPING = Map.of(
            "Récupération", new ZoneRx(PrescriptionRef.PCT_LT1, 60, 72),
            "Endurance fondamentale", new ZoneRx(PrescriptionRef.PCT_LT1, 80, 92),
            "Marathon", new ZoneRx(PrescriptionRef.PCT_LT1, 95, 102),
            "Seuil", new ZoneRx(PrescriptionRef.PCT_LT2, 96, 103),
            "VO2", new ZoneRx(PrescriptionRef.PCT_VC, 100, 107),
            "Anaérobie / Sprint", new ZoneRx(PrescriptionRef.PCT_PACE_800M, 98, 110));

    private final SessionCalculatorService calculatorService;
    private final SessionCalculatorEngine engine;
    private final AthleteZoneValueRepository valueRepository;
    private final TrainingZoneRepository zoneRepository;
    private final TrainingZoneSeedService seedService;
    private final AthleteRepository athleteRepository;
    private final ClubRepository clubRepository;

    /**
     * (Re)génère les valeurs AUTO non verrouillées de l'athlète. Idempotent. Retourne le nombre
     * de valeurs écrites (créées ou mises à jour).
     */
    @Transactional
    public int resync(UUID clubId, UUID athleteId) {
        AthletePaceContext ctx = calculatorService.contextFor(clubId, athleteId);

        if (!zoneRepository.existsByClubId(clubId)) {
            seedService.seedDefaultsIfEmpty(clubRepository.getReferenceById(clubId));
        }
        List<TrainingZone> zones = zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId);
        var athlete = athleteRepository.getReferenceById(athleteId);

        int written = 0;
        for (TrainingZone zone : zones) {
            ZoneRx rx = MAPPING.get(zone.getName());
            if (rx == null) {
                continue; // zone custom : pas de pré-remplissage automatique.
            }
            Result result = engine.calculate(
                    new PrescriptionInput(rx.ref(), rx.minPct(), rx.maxPct(), null, null, null), ctx);
            if (!result.computable()) {
                continue; // profil physio insuffisant : la zone reste « à renseigner ».
            }
            for (ZoneMetric zm : zone.getMetrics()) {
                MetricType metric = zm.getMetricType();
                double[] range = extract(result, metric.getCode(), ctx);
                if (range == null) {
                    continue;
                }
                if (upsertAuto(athlete, zone, metric, athleteId, range)) {
                    written++;
                }
            }
        }
        return written;
    }

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

    /** Extrait la fourchette [min, max] du résultat moteur selon le code de métrique, ou null. */
    private double[] extract(Result r, String code, AthletePaceContext ctx) {
        return switch (code) {
            case "PACE" -> r.paceMinSecPerKm() != null && r.paceMaxSecPerKm() != null
                    ? new double[]{r.paceMinSecPerKm(), r.paceMaxSecPerKm()} : null;
            case "HR" -> r.hrMin() != null && r.hrMax() != null
                    ? new double[]{r.hrMin(), r.hrMax()} : null;
            case "SPEED" -> r.speedMinKmh() != null && r.speedMaxKmh() != null
                    ? new double[]{r.speedMinKmh(), r.speedMaxKmh()} : null;
            case "PCT_HRMAX" -> (r.hrMin() != null && r.hrMax() != null && ctx.fcMax() != null && ctx.fcMax() > 0)
                    ? new double[]{Math.round(100.0 * r.hrMin() / ctx.fcMax()),
                                   Math.round(100.0 * r.hrMax() / ctx.fcMax())} : null;
            case "RPE" -> r.rpeMin() != null && r.rpeMax() != null
                    ? new double[]{r.rpeMin(), r.rpeMax()} : null;
            default -> null; // POWER et métriques custom : pas de source physio → saisie manuelle.
        };
    }
}
