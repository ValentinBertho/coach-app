package com.coachrun.service;

import com.coachrun.entity.Club;
import com.coachrun.entity.MetricType;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.ZoneScope;
import com.coachrun.repository.MetricTypeRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
