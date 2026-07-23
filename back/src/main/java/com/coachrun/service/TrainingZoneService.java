package com.coachrun.service;

import com.coachrun.dto.request.TrainingZoneRequest;
import com.coachrun.dto.request.ZoneMetricsRequest;
import com.coachrun.dto.response.TrainingZoneResponse;
import com.coachrun.entity.MetricType;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.enums.ZoneScope;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.MetricTypeRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Zones de travail d'un club : CRUD, réordonnancement et configuration des métriques portées.
 * La liste provisionne paresseusement le jeu standard si le club n'a encore aucune zone
 * ({@link TrainingZoneSeedService}) — pas de page blanche. Cf. AUDIT-COACH-ZONES-METRIQUES §3.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingZoneService {

    private final TrainingZoneRepository zoneRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final ClubRepository clubRepository;
    private final TrainingZoneSeedService seedService;

    /** Liste ordonnée ; seed du jeu standard si le club n'a aucune zone (transaction en écriture). */
    @Transactional
    public List<TrainingZoneResponse> list(UUID clubId) {
        if (!zoneRepository.existsByClubId(clubId)) {
            seedService.seedDefaultsIfEmpty(clubRepository.getReferenceById(clubId));
        }
        return zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId).stream()
                .map(TrainingZoneResponse::from)
                .toList();
    }

    @Transactional
    public TrainingZoneResponse create(UUID clubId, TrainingZoneRequest req) {
        TrainingZone z = new TrainingZone();
        z.setClub(clubRepository.getReferenceById(clubId));
        apply(z, req);
        z.setBuiltin(false);
        return TrainingZoneResponse.from(zoneRepository.save(z));
    }

    @Transactional
    public TrainingZoneResponse update(UUID clubId, UUID id, TrainingZoneRequest req) {
        TrainingZone z = require(clubId, id);
        apply(z, req);
        return TrainingZoneResponse.from(z);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        zoneRepository.delete(require(clubId, id));
    }

    /** Applique l'ordre des zones passées ; les zones absentes de la liste sont laissées telles quelles. */
    @Transactional
    public void reorder(UUID clubId, List<UUID> orderedIds) {
        Map<UUID, TrainingZone> byId = zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId)
                .stream().collect(Collectors.toMap(TrainingZone::getId, Function.identity()));
        int order = 0;
        for (UUID id : orderedIds) {
            TrainingZone z = byId.get(id);
            if (z != null) {
                z.setSortOrder(order++);
            }
        }
    }

    /** Remplace les métriques portées par la zone (dans l'ordre fourni). */
    @Transactional
    public TrainingZoneResponse setMetrics(UUID clubId, UUID zoneId, ZoneMetricsRequest req) {
        TrainingZone z = require(clubId, zoneId);
        z.getMetrics().clear();
        int order = 0;
        for (UUID metricId : req.metricTypeIds()) {
            MetricType metric = metricTypeRepository.findVisibleForClub(metricId, clubId)
                    .orElseThrow(() -> new NotFoundException("Métrique introuvable."));
            ZoneMetric zm = new ZoneMetric();
            zm.setZone(z);
            zm.setMetricType(metric);
            zm.setSortOrder(order++);
            z.getMetrics().add(zm);
        }
        return TrainingZoneResponse.from(z);
    }

    private void apply(TrainingZone z, TrainingZoneRequest req) {
        z.setName(req.name().trim());
        z.setColor(req.color() == null || req.color().isBlank() ? null : req.color().trim());
        z.setScope(req.scope() != null ? req.scope() : ZoneScope.CLUB);
        z.setDiscipline(req.discipline());
        if (req.sortOrder() != null) {
            z.setSortOrder(req.sortOrder());
        }
    }

    private TrainingZone require(UUID clubId, UUID id) {
        return zoneRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Zone introuvable."));
    }
}
