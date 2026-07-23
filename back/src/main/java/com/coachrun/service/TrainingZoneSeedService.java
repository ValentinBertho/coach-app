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
 * <p>Chaque zone porte par défaut les métriques builtin PACE + HR (résolues par code dans le
 * catalogue global seedé en migration 044).</p>
 */
@Service
@RequiredArgsConstructor
public class TrainingZoneSeedService {

    /** Jeu de zones standard : nom + couleur d'affichage, dans l'ordre. */
    private static final List<String[]> STANDARD_ZONES = List.of(
            new String[]{"Récupération", "#94a3b8"},
            new String[]{"Endurance fondamentale", "#22c55e"},
            new String[]{"Marathon", "#84cc16"},
            new String[]{"Seuil", "#eab308"},
            new String[]{"VO2", "#f97316"},
            new String[]{"Anaérobie / Sprint", "#ef4444"});

    /** Métriques portées par défaut (codes du catalogue builtin). */
    private static final List<String> DEFAULT_METRIC_CODES = List.of("PACE", "HR");

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
            Map.entry("Anaérobie / Sprint|HR", new Rule(ZoneAnchor.FCMAX, 95, 100, ZoneModel.PCT_FCMAX)));

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
        List<MetricType> defaultMetrics = resolveDefaultMetrics(club.getId());
        int order = 0;
        for (String[] def : STANDARD_ZONES) {
            TrainingZone zone = new TrainingZone();
            zone.setClub(club);
            zone.setName(def[0]);
            zone.setColor(def[1]);
            zone.setSortOrder(order++);
            zone.setScope(ZoneScope.CLUB);
            zone.setBuiltin(true);
            int metricOrder = 0;
            for (MetricType metric : defaultMetrics) {
                ZoneMetric zm = new ZoneMetric();
                zm.setZone(zone);
                zm.setMetricType(metric);
                zm.setSortOrder(metricOrder++);
                Rule rule = RULES.get(def[0] + "|" + metric.getCode());
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

    private List<MetricType> resolveDefaultMetrics(UUID clubId) {
        List<MetricType> visible = metricTypeRepository.findVisibleForClub(clubId);
        return DEFAULT_METRIC_CODES.stream()
                .map(code -> visible.stream()
                        .filter(m -> m.isBuiltin() && code.equals(m.getCode()))
                        .findFirst()
                        .orElse(null))
                .filter(m -> m != null)
                .toList();
    }
}
