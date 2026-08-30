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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingZoneService {

    private final TrainingZoneRepository zoneRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final ClubRepository clubRepository;
    private final TrainingZoneSeedService seedService;
    private final ZoneSetService zoneSetService;
    private final ZoneValueSyncService valueSyncService;
    private final com.coachrun.repository.AthleteRepository athleteRepository;

    /**
     * Liste ordonnée des zones d'un modèle ({@code setId} null = jeu par défaut du club) ; seed du
     * jeu standard si le club n'a aucune zone (transaction en écriture).
     */
    @Transactional
    public List<TrainingZoneResponse> list(UUID clubId, UUID setId) {
        if (!zoneRepository.existsByClubId(clubId)) {
            seedService.seedDefaultsIfEmpty(clubRepository.getReferenceById(clubId));
        }
        return zoneSetService.zones(clubId, setId).stream()
                .map(TrainingZoneResponse::from)
                .toList();
    }

    /** Zones effectivement appliquées à un athlète (son modèle de zones, sinon celui par défaut). */
    @Transactional
    public List<TrainingZoneResponse> listForAthlete(UUID clubId, UUID athleteId) {
        return zoneSetService.zonesForAthlete(clubId, athleteId).stream()
                .map(TrainingZoneResponse::from)
                .toList();
    }

    @Transactional
    public TrainingZoneResponse create(UUID clubId, TrainingZoneRequest req) {
        TrainingZone z = new TrainingZone();
        z.setClub(clubRepository.getReferenceById(clubId));
        // Une zone appartient toujours à un modèle : sans quoi elle n'apparaîtrait dans aucune
        // échelle et resterait invisible.
        z.setZoneSet(req.zoneSetId() != null
                ? zoneSetService.require(clubId, req.zoneSetId())
                : zoneSetService.ensureDefault(clubId));
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

    /** Édite la règle de calcul (ancre + %min/max + modèle) d'une métrique d'une zone. */
    @Transactional
    public TrainingZoneResponse setRule(UUID clubId, UUID zoneId, UUID metricId,
                                        com.coachrun.dto.request.ZoneRuleRequest req) {
        TrainingZone z = require(clubId, zoneId);
        com.coachrun.entity.ZoneMetric zm = z.getMetrics().stream()
                .filter(m -> m.getMetricType().getId().equals(metricId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Métrique de zone introuvable."));
        zm.setAnchor(req.anchor());
        // Vide ou identique à l'ancre basse ⇒ on ne stocke rien : une seule référence pour la zone.
        zm.setHighAnchor(req.highAnchor() == req.anchor() ? null : req.highAnchor());
        zm.setLowPct(req.lowPct());
        zm.setHighPct(req.highPct());
        zm.setModel(req.model() != null ? req.model() : com.coachrun.entity.enums.ZoneModel.CUSTOM);
        resyncAthletes(clubId, "règle de la zone « " + z.getName() + " »");
        return TrainingZoneResponse.from(z);
    }

    /**
     * Répercute un changement de règle sur les valeurs des athlètes.
     *
     * <p>Sans cela, changer « 88–92 % » en « 90–94 % » ne changeait <b>rien</b> à ce qui est
     * prescrit : la règle bougeait, les allures et fréquences cardiaques déjà calculées restaient
     * en place jusqu'à ce que quelqu'un pense à ouvrir la fiche de chaque athlète et à cliquer
     * « Resynchroniser ». Le coach modifiait le pourcentage, sa séance continuait d'annoncer
     * l'ancienne allure, et rien ne disait pourquoi.</p>
     *
     * <p>La resynchronisation ne touche que les valeurs AUTO non verrouillées : un ajustement posé
     * à la main sur un athlète survit au changement d'échelle — c'était le sens du verrou.</p>
     *
     * <p>Tout le club, et non le seul athlète regardé : l'échelle est celle du club, la changer
     * pour un seul laisserait les autres sur des valeurs devenues fausses. Le coût est celui d'un
     * geste rare et délibéré, pas d'un chemin chaud.</p>
     */
    private void resyncAthletes(UUID clubId, String cause) {
        int touched = 0;
        for (com.coachrun.entity.Athlete a : athleteRepository.findByClubIdOrderByLastNameAsc(clubId)) {
            touched += valueSyncService.resync(clubId, a.getId());
        }
        log.info("Zones resynchronisées après {} : {} valeur(s) mise(s) à jour (club={})",
                cause, touched, clubId);
    }

    /** Remplace les métriques portées par la zone (dans l'ordre fourni). */
    @Transactional
    public TrainingZoneResponse setMetrics(UUID clubId, UUID zoneId, ZoneMetricsRequest req) {
        TrainingZone z = require(clubId, zoneId);
        List<UUID> wanted = req.metricTypeIds() == null ? List.of() : req.metricTypeIds();

        // Réconciliation (et non clear + réinsertion) : on retire les métriques non désirées et on
        // n'ajoute que les nouvelles. Cela préserve les règles (ancre + %) des métriques conservées
        // et évite la violation de la contrainte unique (zone, métrique) au flush (insert avant delete).
        z.getMetrics().removeIf(zm -> !wanted.contains(zm.getMetricType().getId()));
        Map<UUID, ZoneMetric> existing = new java.util.HashMap<>();
        for (ZoneMetric zm : z.getMetrics()) {
            existing.put(zm.getMetricType().getId(), zm);
        }
        int order = 0;
        for (UUID metricId : wanted) {
            ZoneMetric zm = existing.get(metricId);
            if (zm == null) {
                MetricType metric = metricTypeRepository.findVisibleForClub(metricId, clubId)
                        .orElseThrow(() -> new NotFoundException("Métrique introuvable."));
                zm = new ZoneMetric();
                zm.setZone(z);
                zm.setMetricType(metric);
                z.getMetrics().add(zm);
                existing.put(metricId, zm);
            }
            zm.setSortOrder(order++);
        }
        return TrainingZoneResponse.from(z);
    }

    private void apply(TrainingZone z, TrainingZoneRequest req) {
        z.setName(req.name().trim());
        z.setColor(req.color() == null || req.color().isBlank() ? null : req.color().trim());
        z.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
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
