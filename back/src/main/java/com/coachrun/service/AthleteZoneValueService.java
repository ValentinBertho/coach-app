package com.coachrun.service;

import com.coachrun.dto.request.AthleteZoneValueRequest;
import com.coachrun.dto.response.AthleteZoneValueResponse;
import com.coachrun.entity.AthleteZoneValue;
import com.coachrun.entity.enums.ZoneValueSource;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.MetricTypeRepository;
import com.coachrun.repository.TrainingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
