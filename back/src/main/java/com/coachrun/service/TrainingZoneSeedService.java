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

    /** Zone d'allure : porte l'allure + la vitesse (dérivée de l'allure, même règle). */
    private static final List<String> PACE_SCALE = List.of("PACE", "SPEED");
    /** Zone cardiaque : FC en bpm + % de la FC max. */
    private static final List<String> HR_SCALE = List.of("HR", "PCT_HRMAX");

    /**
     * Jeu de zones standard, dans l'ordre. Deux échelles indépendantes (façon Nolio) :
     * <ul>
     *   <li><b>Allure</b> (12 bandes) : du footing facile aux allures de compétition. Chaque bande
     *       porte l'allure <i>et</i> la vitesse (la vitesse se calibre sur l'allure).</li>
     *   <li><b>Cardio</b> (4 bandes) : endurance aérobie, seuil, VO2max, anaérobie — en bpm et en
     *       % de la FC max.</li>
     * </ul>
     * Le RPE n'est <b>pas</b> une zone : il se règle sur le contenu de la séance (par bloc).
     */
    private static final List<ZoneDef> STANDARD_ZONES = List.of(
            // --- Échelle Allure ---
            new ZoneDef("Footing facile", "#94a3b8", PACE_SCALE),
            new ZoneDef("EF", "#22c55e", PACE_SCALE),
            new ZoneDef("Steady", "#84cc16", PACE_SCALE),
            new ZoneDef("Seuil 1", "#facc15", PACE_SCALE),
            new ZoneDef("Tempo", "#eab308", PACE_SCALE),
            new ZoneDef("Seuil 2 bas", "#f59e0b", PACE_SCALE),
            new ZoneDef("Seuil 2 haut", "#f97316", PACE_SCALE),
            new ZoneDef("10 km", "#65a30d", PACE_SCALE),
            new ZoneDef("5 km", "#ca8a04", PACE_SCALE),
            new ZoneDef("3 km", "#ea580c", PACE_SCALE),
            new ZoneDef("1500 m", "#dc2626", PACE_SCALE),
            new ZoneDef("800 m", "#b91c1c", PACE_SCALE),
            // --- Échelle Cardio ---
            new ZoneDef("Endurance aérobie", "#22c55e", HR_SCALE),
            new ZoneDef("Seuil", "#eab308", HR_SCALE),
            new ZoneDef("VO2max", "#f97316", HR_SCALE),
            new ZoneDef("Anaérobie", "#ef4444", HR_SCALE));

    /**
     * Règle par défaut d'un couple (zone, métrique) : ancre + %min + %max + modèle nommé.
     * {@code highAnchor} ne sert qu'à la zone qui enjambe la frontière LT1 → LT2 ; ailleurs il vaut
     * {@code null} et la borne haute reprend l'ancre basse.
     */
    private record Rule(ZoneAnchor anchor, ZoneAnchor highAnchor, double low, double high, ZoneModel model) {
        Rule(ZoneAnchor anchor, double low, double high, ZoneModel model) {
            this(anchor, null, low, high, model);
        }
    }

    /**
     * Règles standard seedées. Clé = "nom de zone|code métrique".
     *
     * <p>Échelle <b>Allure</b> : chaque bande est ancrée sur une valeur de référence de l'athlète —
     * seuils lactiques (LT1/LT2), vitesse critique (VC), VMA, ou allure de compétition dérivée du
     * VDOT. Les allures se recalculent donc dès qu'une de ces métriques change. La <b>vitesse</b>
     * porte la même règle que l'allure (elle en est la conversion).</p>
     *
     * <p>Échelle <b>Cardio</b> : bandes en % de la FC max (bpm et % FCmax).</p>
     */
    private static final Map<String, Rule> RULES = Map.ofEntries(
            // --- Allure : une CHAÎNE contiguë, deux références seulement ---
            // La borne haute d'une zone est la borne basse de la suivante, donc aucun trou ni
            // chevauchement : un athlète n'est jamais dans deux zones à la fois, ni dans aucune.
            // Bloc LT1 (sous le seuil aérobie) :
            Map.entry("Footing facile|PACE", new Rule(ZoneAnchor.LT1, 68, 80, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("EF|PACE", new Rule(ZoneAnchor.LT1, 80, 90, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Steady|PACE", new Rule(ZoneAnchor.LT1, 90, 100, ZoneModel.LACTATE_THRESHOLD)),
            // Zone de transition : borne basse en % de LT1, borne haute en % de LT2. C'est la
            // frontière « LT1 jusqu'ici, LT2 à partir de là » — et la seule zone à deux références.
            Map.entry("Seuil 1|PACE", new Rule(ZoneAnchor.LT1, ZoneAnchor.LT2, 100, 93, ZoneModel.LACTATE_THRESHOLD)),
            // Bloc LT2 (au-delà), LT2 tombant pile entre « Seuil 2 bas » et « Seuil 2 haut » :
            Map.entry("Tempo|PACE", new Rule(ZoneAnchor.LT2, 93, 97, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Seuil 2 bas|PACE", new Rule(ZoneAnchor.LT2, 97, 100, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Seuil 2 haut|PACE", new Rule(ZoneAnchor.LT2, 100, 104, ZoneModel.LACTATE_THRESHOLD)),
            // Allures de compétition : ancrées sur les allures dérivées du VDOT, donc elles suivent
            // les RECORDS de l'athlète. Les mettre dans la chaîne LT2 les rendrait contiguës, mais
            // saisir un chrono ne les déplacerait plus (un record ne change ni LT1 ni LT2) — ce qui
            // supprimerait l'adaptation aux records. Elles restent donc hors chaîne, à dessein.
            Map.entry("10 km|PACE", new Rule(ZoneAnchor.PACE_10KM, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("5 km|PACE", new Rule(ZoneAnchor.PACE_5KM, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("3 km|PACE", new Rule(ZoneAnchor.PACE_3000M, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("1500 m|PACE", new Rule(ZoneAnchor.VMA, 98, 103, ZoneModel.VMA)),
            Map.entry("800 m|PACE", new Rule(ZoneAnchor.PACE_800M, 98, 103, ZoneModel.DANIELS_VDOT)),
            // --- Vitesse : même chaîne que l'allure (elle en est la conversion) ---
            Map.entry("Footing facile|SPEED", new Rule(ZoneAnchor.LT1, 68, 80, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("EF|SPEED", new Rule(ZoneAnchor.LT1, 80, 90, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Steady|SPEED", new Rule(ZoneAnchor.LT1, 90, 100, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Seuil 1|SPEED", new Rule(ZoneAnchor.LT1, ZoneAnchor.LT2, 100, 93, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Tempo|SPEED", new Rule(ZoneAnchor.LT2, 93, 97, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Seuil 2 bas|SPEED", new Rule(ZoneAnchor.LT2, 97, 100, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("Seuil 2 haut|SPEED", new Rule(ZoneAnchor.LT2, 100, 104, ZoneModel.LACTATE_THRESHOLD)),
            Map.entry("10 km|SPEED", new Rule(ZoneAnchor.PACE_10KM, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("5 km|SPEED", new Rule(ZoneAnchor.PACE_5KM, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("3 km|SPEED", new Rule(ZoneAnchor.PACE_3000M, 98, 102, ZoneModel.DANIELS_VDOT)),
            Map.entry("1500 m|SPEED", new Rule(ZoneAnchor.VMA, 98, 103, ZoneModel.VMA)),
            Map.entry("800 m|SPEED", new Rule(ZoneAnchor.PACE_800M, 98, 103, ZoneModel.DANIELS_VDOT)),
            // --- Cardio : bpm + % FC max ---
            Map.entry("Endurance aérobie|HR", new Rule(ZoneAnchor.FCMAX, 65, 80, ZoneModel.PCT_FCMAX)),
            Map.entry("Endurance aérobie|PCT_HRMAX", new Rule(ZoneAnchor.FCMAX, 65, 80, ZoneModel.PCT_FCMAX)),
            Map.entry("Seuil|HR", new Rule(ZoneAnchor.FCMAX, 80, 90, ZoneModel.PCT_FCMAX)),
            Map.entry("Seuil|PCT_HRMAX", new Rule(ZoneAnchor.FCMAX, 80, 90, ZoneModel.PCT_FCMAX)),
            Map.entry("VO2max|HR", new Rule(ZoneAnchor.FCMAX, 90, 96, ZoneModel.PCT_FCMAX)),
            Map.entry("VO2max|PCT_HRMAX", new Rule(ZoneAnchor.FCMAX, 90, 96, ZoneModel.PCT_FCMAX)),
            Map.entry("Anaérobie|HR", new Rule(ZoneAnchor.FCMAX, 96, 100, ZoneModel.PCT_FCMAX)),
            Map.entry("Anaérobie|PCT_HRMAX", new Rule(ZoneAnchor.FCMAX, 96, 100, ZoneModel.PCT_FCMAX)));

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
                    zm.setHighAnchor(rule.highAnchor());
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
