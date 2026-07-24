package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.MetricType;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneModel;
import com.coachrun.entity.enums.ZoneScope;
import com.coachrun.repository.MetricTypeRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provisionne le jeu de zones standard d'un club (façon Daniels/physiologique) pour éviter la
 * page blanche. Seed applicatif — idempotent : n'agit que si le club n'a aucune zone. Appelé
 * paresseusement à la première lecture d'un club (cf. {@link TrainingZoneService#list}), ce qui
 * couvre uniformément les clubs existants comme les futurs.
 *
 * <p>Deux échelles par métrique (façon Nolio, chantier zones v2 — P3) : une <b>échelle Allure</b>
 * fine (13 zones, des allures d'endurance jusqu'aux allures de compétition 5k/3k/1500/800/400) et une
 * <b>échelle FC</b> (6 zones physiologiques). Les zones d'endurance portent allure + FC ; les zones
 * de compétition ne portent que l'allure. Les métriques builtin sont résolues par code dans le
 * catalogue global seedé en migration 044.</p>
 */
@Service
@RequiredArgsConstructor
public class TrainingZoneSeedService {

    /** Définition d'une zone seedée : nom, couleur, et métriques portées (codes). */
    private record ZoneDef(String name, String color, List<String> metricCodes) {
    }

    private static final List<String> PACE_HR_RPE = List.of("PACE", "HR", "RPE");
    private static final List<String> PACE_ONLY = List.of("PACE");

    /**
     * Jeu de zones standard, dans l'ordre. Les 6 premières (physiologiques) portent allure + FC + RPE
     * (effort perçu, cible fixe par zone) et constituent l'échelle FC ; les 7 suivantes (allures de
     * compétition) ne portent que l'allure et complètent l'échelle Allure à 13 bandes façon Nolio.
     */
    private static final List<ZoneDef> STANDARD_ZONES = List.of(
            new ZoneDef("Récupération", "#94a3b8", PACE_HR_RPE),
            new ZoneDef("Endurance fondamentale", "#22c55e", PACE_HR_RPE),
            new ZoneDef("Marathon", "#84cc16", PACE_HR_RPE),
            new ZoneDef("Seuil", "#eab308", PACE_HR_RPE),
            new ZoneDef("VO2", "#f97316", PACE_HR_RPE),
            new ZoneDef("Anaérobie / Sprint", "#ef4444", PACE_HR_RPE),
            new ZoneDef("Allure semi", "#16a34a", PACE_ONLY),
            new ZoneDef("Allure 10 km", "#65a30d", PACE_ONLY),
            new ZoneDef("Allure 5 km", "#ca8a04", PACE_ONLY),
            new ZoneDef("Allure 3 km", "#ea580c", PACE_ONLY),
            new ZoneDef("Allure 1500 m", "#dc2626", PACE_ONLY),
            new ZoneDef("Allure 800 m", "#b91c1c", PACE_ONLY),
            new ZoneDef("Allure 400 m", "#7f1d1d", PACE_ONLY));

    /** Règle par défaut d'un couple (zone, métrique) : ancre + %min + %max + modèle nommé. */
    private record Rule(ZoneAnchor anchor, double low, double high, ZoneModel model) {
    }

    /**
     * Règles standard seedées : allure dérivée des seuils/VDOT, FC en % de la FC max.
     * Clé = "nom de zone|code métrique".
     */
    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("Récupération|PACE", new Rule(ZoneAnchor.LT1, 60, 72, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Récupération|HR", new Rule(ZoneAnchor.FCMAX, 60, 70, ZoneModel.PCT_FCMAX)),
            Map.entry("Endurance fondamentale|PACE", new Rule(ZoneAnchor.LT1, 80, 92, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Endurance fondamentale|HR", new Rule(ZoneAnchor.FCMAX, 70, 80, ZoneModel.PCT_FCMAX)),
            Map.entry("Marathon|PACE", new Rule(ZoneAnchor.LT1, 95, 102, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Marathon|HR", new Rule(ZoneAnchor.FCMAX, 80, 85, ZoneModel.PCT_FCMAX)),
            Map.entry("Seuil|PACE", new Rule(ZoneAnchor.LT2, 96, 103, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Seuil|HR", new Rule(ZoneAnchor.FCMAX, 85, 90, ZoneModel.PCT_FCMAX)),
            Map.entry("VO2|PACE", new Rule(ZoneAnchor.VC, 100, 107, ZoneModel.VC)),
            Map.entry("VO2|HR", new Rule(ZoneAnchor.FCMAX, 90, 95, ZoneModel.PCT_FCMAX)),
            Map.entry("Anaérobie / Sprint|PACE", new Rule(ZoneAnchor.PACE_800M, 98, 110, ZoneModel.DANIELS_VDOT)),
            Map.entry("Anaérobie / Sprint|HR", new Rule(ZoneAnchor.FCMAX, 95, 100, ZoneModel.PCT_FCMAX)),
            // RPE (effort perçu 1–10) : cible fixe par zone, sans ancre (identique pour tous les athlètes).
            Map.entry("Récupération|RPE", new Rule(null, 2, 3, ZoneModel.CUSTOM)),
            Map.entry("Endurance fondamentale|RPE", new Rule(null, 3, 4, ZoneModel.CUSTOM)),
            Map.entry("Marathon|RPE", new Rule(null, 5, 6, ZoneModel.CUSTOM)),
            Map.entry("Seuil|RPE", new Rule(null, 7, 8, ZoneModel.CUSTOM)),
            Map.entry("VO2|RPE", new Rule(null, 8, 9, ZoneModel.CUSTOM)),
            Map.entry("Anaérobie / Sprint|RPE", new Rule(null, 9, 10, ZoneModel.CUSTOM)),
            // Allures de compétition (échelle Allure fine, dérivées du VDOT) : la bande encadre l'allure de la distance.
            Map.entry("Allure semi|PACE", new Rule(ZoneAnchor.PACE_SEMI, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("Allure 10 km|PACE", new Rule(ZoneAnchor.PACE_10KM, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("Allure 5 km|PACE", new Rule(ZoneAnchor.PACE_5KM, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("Allure 3 km|PACE", new Rule(ZoneAnchor.PACE_3000M, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("Allure 1500 m|PACE", new Rule(ZoneAnchor.PACE_1500M, 98, 103, ZoneModel.DANIELS_VDOT)),
            Map.entry("Allure 800 m|PACE", new Rule(ZoneAnchor.PACE_800M, 98, 103, ZoneModel.DANIELS_VDOT)),
            Map.entry("Allure 400 m|PACE", new Rule(ZoneAnchor.PACE_800M, 104, 112, ZoneModel.DANIELS_VDOT)));

    private final TrainingZoneRepository zoneRepository;
    private final MetricTypeRepository metricTypeRepository;

    /**
     * Seed le club s'il n'a aucune zone. Retourne {@code true} si un seed a été effectué.
     * À appeler dans une transaction en écriture.
     */
    @Transactional
    public boolean seedDefaultsIfEmpty(Club club) {
        if (zoneRepository.existsByClubId(club.getId())) {
            return false;
        }
        Map<String, MetricType> byCode = resolveBuiltinMetrics(club.getId());
        int order = 0;
        for (ZoneDef def : STANDARD_ZONES) {
            TrainingZone zone = new TrainingZone();
            zone.setClub(club);
            zone.setName(def.name());
            zone.setColor(def.color());
            zone.setSortOrder(order++);
            zone.setScope(ZoneScope.CLUB);
            zone.setBuiltin(true);
            int metricOrder = 0;
            for (String code : def.metricCodes()) {
                MetricType metric = byCode.get(code);
                if (metric == null) {
                    continue;
                }
                ZoneMetric zm = new ZoneMetric();
                zm.setZone(zone);
                zm.setMetricType(metric);
                zm.setSortOrder(metricOrder++);
                Rule rule = RULES.get(def.name() + "|" + code);
                if (rule != null) {
                    zm.setAnchor(rule.anchor());
                    zm.setLowPct(rule.low());
                    zm.setHighPct(rule.high());
                    zm.setModel(rule.model());
                }
                zone.getMetrics().add(zm);
            }
            zoneRepository.save(zone);
        }
        return true;
    }

    /** Métriques builtin du club indexées par code (PACE, HR, …). */
    private Map<String, MetricType> resolveBuiltinMetrics(UUID clubId) {
        Map<String, MetricType> byCode = new java.util.HashMap<>();
        for (MetricType m : metricTypeRepository.findVisibleForClub(clubId)) {
            if (m.isBuiltin()) {
                byCode.putIfAbsent(m.getCode(), m);
            }
        }
        return byCode;
    }
}
