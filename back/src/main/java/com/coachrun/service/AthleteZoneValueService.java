package com.coachrun.service;

import com.coachrun.dto.request.AthleteZoneValueRequest;
import com.coachrun.dto.response.AthleteZoneSheetResponse;
import com.coachrun.dto.response.AthleteZoneValueResponse;
import com.coachrun.entity.AthleteZoneValue;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.ZoneValueSource;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.MetricTypeRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Valeurs de zones par athlète (fiche athlète). Lecture avec pré-remplissage AUTO au premier accès,
 * saisie/ajustement manuel (verrou compris) et resync explicite. Cf. AUDIT-COACH-ZONES-METRIQUES §3 (Z2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteZoneValueService {

    private final AthleteZoneValueRepository valueRepository;
    private final TrainingZoneRepository zoneRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final AthleteRepository athleteRepository;
    private final ZoneValueSyncService syncService;
    private final ZoneSetService zoneSetService;

    /** Valeurs de l'athlète ; pré-remplissage AUTO au tout premier accès (pas d'écran vide). */
    @Transactional
    public List<AthleteZoneValueResponse> list(UUID clubId, UUID athleteId) {
        if (!valueRepository.existsByAthleteId(athleteId)) {
            syncService.resync(clubId, athleteId);
        }
        return valueRepository.findByAthleteId(athleteId).stream()
                .map(AthleteZoneValueResponse::from)
                .toList();
    }


    /**
     * L'échelle de zones telle que l'athlète la lit (portail {@code /me}).
     *
     * <p>Il voyait jusqu'ici les allures d'<b>une</b> séance, jamais l'échelle dont elles sortent :
     * la table qui dit à quelle allure et à quelle fréquence cardiaque chacune de ses zones se
     * court. C'est pourtant la donnée la plus quotidienne du système — celle qu'on relit avant de
     * partir, pas celle qu'on consulte une fois par saison.</p>
     *
     * <p>On reprend le même chemin que le coach, aux mêmes sources : le modèle de zones appliqué à
     * cet athlète, et ses valeurs. Il ne s'agit pas d'une seconde vérité affichée à côté de la
     * première — les deux écrans doivent montrer le même tableau, sans quoi coach et athlète ne
     * parleraient plus de la même séance.</p>
     */
    @Transactional
    public List<AthleteZoneSheetResponse> sheetForAthlete(UUID athleteId) {
        UUID clubId = athleteRepository.findById(athleteId)
                .map(a -> a.getClub().getId())
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        // Même pré-remplissage qu'au premier accès du coach : sans lui, un athlète dont le coach
        // n'a jamais ouvert l'onglet Zones tomberait sur un écran vide alors que ses valeurs sont
        // entièrement dérivables de son profil.
        if (!valueRepository.existsByAthleteId(athleteId)) {
            syncService.resync(clubId, athleteId);
        }

        Map<String, AthleteZoneValue> values = valueRepository.findByAthleteId(athleteId).stream()
                .collect(Collectors.toMap(
                        v -> v.getZone().getId() + ":" + v.getMetricType().getId(),
                        Function.identity(),
                        (a, b) -> a));

        return zoneSetService.zonesForAthlete(clubId, athleteId).stream()
                .map(z -> sheetOf(z, values))
                .toList();
    }

    private AthleteZoneSheetResponse sheetOf(TrainingZone zone, Map<String, AthleteZoneValue> values) {
        List<AthleteZoneSheetResponse.Metric> metrics = zone.getMetrics().stream()
                .sorted(Comparator.comparingInt(ZoneMetric::getSortOrder))
                .map(zm -> metricOf(zone, zm, values))
                .toList();
        return new AthleteZoneSheetResponse(
                zone.getId(), zone.getName(), zone.getColor(), zone.getDescription(),
                zone.getSortOrder(), metrics);
    }

    private AthleteZoneSheetResponse.Metric metricOf(TrainingZone zone, ZoneMetric zm,
                                                     Map<String, AthleteZoneValue> values) {
        var metric = zm.getMetricType();
        // Une métrique déclarée sans valeur reste dans la liste : la ligne « — » dit qu'on attend
        // une donnée (un test de FC max, par exemple), là où l'omettre laisserait croire que la
        // zone ne se lit pas dans cette unité.
        AthleteZoneValue v = values.get(zone.getId() + ":" + metric.getId());
        return new AthleteZoneSheetResponse.Metric(
                metric.getId(), metric.getCode(), metric.getName(), metric.getUnit(),
                metric.getFormat(),
                v == null ? null : v.getValueMin(),
                v == null ? null : v.getValueMax(),
                v == null ? null : v.getSource(),
                zm.getAnchor(), zm.getHighAnchor(), zm.getLowPct(), zm.getHighPct());
    }

    /** Regénère les valeurs AUTO non verrouillées depuis le moteur physio. */
    @Transactional
    public List<AthleteZoneValueResponse> resync(UUID clubId, UUID athleteId) {
        syncService.resync(clubId, athleteId);
        return valueRepository.findByAthleteId(athleteId).stream()
                .map(AthleteZoneValueResponse::from)
                .toList();
    }

    /**
     * Saisie/ajustement d'une valeur. Fournir min/max la bascule en MANUAL (protégée du resync) ;
     * {@code locked} verrouille la valeur en place (une valeur AUTO verrouillée survit au resync).
     */
    @Transactional
    public AthleteZoneValueResponse upsert(UUID clubId, UUID athleteId, UUID zoneId, UUID metricId,
                                           AthleteZoneValueRequest req) {
        // Scoping : la zone et la métrique doivent être visibles du club, l'athlète en faire partie.
        zoneRepository.findByIdAndClubId(zoneId, clubId)
                .orElseThrow(() -> new NotFoundException("Zone introuvable."));
        metricTypeRepository.findVisibleForClub(metricId, clubId)
                .orElseThrow(() -> new NotFoundException("Métrique introuvable."));
        athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        AthleteZoneValue value = valueRepository
                .findByAthleteIdAndZoneIdAndMetricTypeId(athleteId, zoneId, metricId)
                .orElseGet(() -> {
                    AthleteZoneValue v = new AthleteZoneValue();
                    v.setAthlete(athleteRepository.getReferenceById(athleteId));
                    v.setZone(zoneRepository.getReferenceById(zoneId));
                    v.setMetricType(metricTypeRepository.getReferenceById(metricId));
                    return v;
                });

        if (req.valueMin() != null || req.valueMax() != null) {
            value.setValueMin(req.valueMin());
            value.setValueMax(req.valueMax());
            value.setSource(ZoneValueSource.MANUAL);
        }
        if (req.locked() != null) {
            value.setLocked(req.locked());
        }
        return AthleteZoneValueResponse.from(valueRepository.save(value));
    }
}
