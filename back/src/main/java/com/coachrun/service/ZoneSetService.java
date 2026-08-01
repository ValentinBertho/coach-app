package com.coachrun.service;

import com.coachrun.dto.request.ZoneSetRequest;
import com.coachrun.dto.response.ZoneSetResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.TrainingZone;
import com.coachrun.entity.ZoneMetric;
import com.coachrun.entity.ZoneSet;
import com.coachrun.exception.NotFoundException;
import com.coachrun.exception.ConflictException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.TrainingZoneRepository;
import com.coachrun.repository.ZoneSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Modèles de zones d'un club : plusieurs échelles nommées, dont une par défaut, appliquées
 * athlète par athlète.
 *
 * <p>Le jeu par défaut est provisionné <b>paresseusement</b> au premier accès (même idiome que le
 * seed des zones standard) : les zones existantes d'avant ce chantier lui sont rattachées, sans
 * migration de données ni rupture pour les clubs en place.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ZoneSetService {

    private final ZoneSetRepository setRepository;
    private final TrainingZoneRepository zoneRepository;
    private final AthleteRepository athleteRepository;
    private final AthleteZoneValueRepository valueRepository;
    private final ClubRepository clubRepository;
    private final TrainingZoneSeedService seedService;

    /** Libellé du jeu créé d'office pour les clubs qui n'en avaient pas. */
    private static final String DEFAULT_NAME = "Zones par défaut";

    @Transactional
    public List<ZoneSetResponse> list(UUID clubId) {
        ensureDefault(clubId);
        return setRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId).stream()
                .map(s -> ZoneSetResponse.of(s, countZones(clubId, s.getId())))
                .toList();
    }

    /**
     * Jeu par défaut du club, créé au besoin. Les zones orphelines (antérieures aux modèles de
     * zones) lui sont rattachées : sans cela, un club existant verrait un modèle vide.
     */
    @Transactional
    public ZoneSet ensureDefault(UUID clubId) {
        var existing = setRepository.findFirstByClubIdAndDefaultSetTrue(clubId);
        if (existing.isPresent()) {
            attachOrphans(clubId, existing.get());
            return existing.get();
        }
        if (!zoneRepository.existsByClubId(clubId)) {
            seedService.seedDefaultsIfEmpty(clubRepository.getReferenceById(clubId));
        }
        ZoneSet set = new ZoneSet();
        set.setClub(clubRepository.getReferenceById(clubId));
        set.setName(DEFAULT_NAME);
        set.setDefaultSet(true);
        set.setSortOrder(0);
        set = setRepository.save(set);
        attachOrphans(clubId, set);
        log.info("Modèle de zones par défaut créé pour le club {}", clubId);
        return set;
    }

    private void attachOrphans(UUID clubId, ZoneSet target) {
        for (TrainingZone z : zoneRepository.findByClubIdAndZoneSetIsNull(clubId)) {
            z.setZoneSet(target);
        }
    }

    /**
     * Crée un modèle de zones. Par défaut il recopie l'échelle d'un jeu source (le jeu par défaut
     * si aucun n'est précisé) : partir d'une page blanche obligerait à ressaisir douze zones et
     * leurs règles avant que le modèle ne serve à quoi que ce soit.
     */
    @Transactional
    public ZoneSetResponse create(UUID clubId, ZoneSetRequest req) {
        ZoneSet source = req.copyFromSetId() != null
                ? require(clubId, req.copyFromSetId())
                : ensureDefault(clubId);

        ZoneSet set = new ZoneSet();
        set.setClub(clubRepository.getReferenceById(clubId));
        set.setName(req.name().trim());
        set.setSortOrder(setRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId).size());
        set = setRepository.save(set);

        if (!Boolean.FALSE.equals(req.copyZones())) {
            copyZones(clubId, source, set);
        }
        return ZoneSetResponse.of(set, countZones(clubId, set.getId()));
    }

    /** Recopie les zones d'un jeu vers un autre, métriques et règles de calcul comprises. */
    private void copyZones(UUID clubId, ZoneSet source, ZoneSet target) {
        for (TrainingZone src : zoneRepository.findByClubIdAndZoneSetIdOrderBySortOrderAscNameAsc(clubId, source.getId())) {
            TrainingZone copy = new TrainingZone();
            copy.setClub(src.getClub());
            copy.setZoneSet(target);
            copy.setName(src.getName());
            copy.setColor(src.getColor());
            copy.setDescription(src.getDescription());
            copy.setSortOrder(src.getSortOrder());
            copy.setScope(src.getScope());
            copy.setDiscipline(src.getDiscipline());
            for (ZoneMetric zm : src.getMetrics()) {
                ZoneMetric m = new ZoneMetric();
                m.setZone(copy);
                m.setMetricType(zm.getMetricType());
                m.setSortOrder(zm.getSortOrder());
                m.setAnchor(zm.getAnchor());
                m.setHighAnchor(zm.getHighAnchor());
                m.setLowPct(zm.getLowPct());
                m.setHighPct(zm.getHighPct());
                m.setModel(zm.getModel());
                copy.getMetrics().add(m);
            }
            zoneRepository.save(copy);
        }
    }

    @Transactional
    public ZoneSetResponse rename(UUID clubId, UUID id, ZoneSetRequest req) {
        ZoneSet set = require(clubId, id);
        set.setName(req.name().trim());
        return ZoneSetResponse.of(set, countZones(clubId, id));
    }

    /**
     * Supprime un modèle (et ses zones). Le jeu par défaut n'est pas supprimable : les athlètes
     * qui n'en désignent aucun s'y rabattent. Les athlètes rattachés repassent au jeu par défaut.
     */
    @Transactional
    public void delete(UUID clubId, UUID id) {
        ZoneSet set = require(clubId, id);
        if (set.isDefaultSet()) {
            throw new ConflictException("Le modèle par défaut ne peut pas être supprimé.");
        }
        for (Athlete a : athleteRepository.findByZoneSetId(id)) {
            a.setZoneSet(null);
            dropStaleValues(clubId, a.getId());
        }
        setRepository.delete(set);
    }

    /**
     * Applique un modèle de zones à un athlète (null = jeu par défaut du club). Le
     * re-remplissage des cibles est déclenché ensuite par l'appelant
     * ({@code ZoneValueSyncService.resync}), une fois le rattachement écrit.
     */
    @Transactional
    public void applyToAthlete(UUID clubId, UUID athleteId, UUID zoneSetId) {
        Athlete a = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        a.setZoneSet(zoneSetId == null ? null : require(clubId, zoneSetId));
        athleteRepository.flush();
        // Les cibles de l'ancienne échelle n'ont plus de sens : elles resteraient affichées sur des
        // zones que l'athlète ne travaille plus.
        dropStaleValues(clubId, athleteId);
    }

    /** Purge les valeurs de zones qui n'appartiennent plus à l'échelle de l'athlète. */
    private void dropStaleValues(UUID clubId, UUID athleteId) {
        Set<UUID> current = zonesForAthlete(clubId, athleteId).stream()
                .map(TrainingZone::getId).collect(Collectors.toSet());
        var stale = valueRepository.findByAthleteId(athleteId).stream()
                .filter(v -> !current.contains(v.getZone().getId()))
                .toList();
        if (!stale.isEmpty()) {
            valueRepository.deleteAll(stale);
        }
    }

    /** Zones effectivement appliquées à un athlète : celles de son modèle, sinon du jeu par défaut. */
    public List<TrainingZone> zonesForAthlete(UUID clubId, UUID athleteId) {
        UUID setId = zoneSetIdOf(clubId, athleteId);
        return zones(clubId, setId);
    }

    /**
     * Zones d'un modèle donné ; {@code null} = jeu par défaut du club.
     *
     * <p>Ne provisionne rien : la lecture doit rester possible depuis une transaction en lecture
     * seule. Tant qu'un club n'a pas de jeu par défaut (il n'est créé qu'au premier passage sur
     * l'écran des zones), toutes ses zones font office d'échelle — l'état d'avant ce chantier.</p>
     */
    public List<TrainingZone> zones(UUID clubId, UUID setId) {
        if (setId != null) {
            return zoneRepository.findByClubIdAndZoneSetIdOrderBySortOrderAscNameAsc(clubId, setId);
        }
        return setRepository.findFirstByClubIdAndDefaultSetTrue(clubId)
                .map(s -> zoneRepository.findByClubIdAndZoneSetIdOrderBySortOrderAscNameAsc(clubId, s.getId()))
                .orElseGet(() -> zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId));
    }

    /** Modèle appliqué à un athlète ({@code null} = jeu par défaut). */
    public UUID zoneSetIdOf(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .map(Athlete::getZoneSet)
                .map(ZoneSet::getId)
                .orElse(null);
    }

    private int countZones(UUID clubId, UUID setId) {
        return zoneRepository.findByClubIdAndZoneSetIdOrderBySortOrderAscNameAsc(clubId, setId).size();
    }

    ZoneSet require(UUID clubId, UUID id) {
        return setRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Modèle de zones introuvable."));
    }
}
