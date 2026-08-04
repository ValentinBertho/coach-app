package com.coachrun.service;

import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CoursePrescription;
import com.coachrun.dto.session.CourseRecovery;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.enums.PrescriptionRef;
import com.coachrun.repository.ClubRepository;
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
 * Migration douce des modèles course legacy (chantier Z4) : associe une <b>zone</b> aux blocs encore
 * prescrits en {@code ref + %}, en conservant les champs legacy (réversible, non destructif).
 *
 * <p>Enrichissement <b>à la lecture</b> de la structure d'un modèle : la zone déduite (plus proche
 * zone standard selon le référentiel et la bande %) s'affiche dans l'éditeur épuré et se persiste au
 * prochain enregistrement. Les snapshots figés ne passent pas par ici — ils restent lus en legacy.</p>
 */
@Service
@RequiredArgsConstructor
public class PrescriptionZoneMapper {

    private final TrainingZoneRepository zoneRepository;
    private final TrainingZoneSeedService seedService;
    private final ClubRepository clubRepository;

    /** Retourne une structure où les blocs legacy sans zone reçoivent un {@code zoneId} déduit. */
    @Transactional
    public SessionStructure withZones(UUID clubId, SessionStructure s) {
        if (s == null) {
            return SessionStructure.empty();
        }
        if (!needsMapping(s)) {
            return s;
        }
        if (!zoneRepository.existsByClubId(clubId)) {
            seedService.seedDefaultsIfEmpty(clubRepository.getReferenceById(clubId));
        }
        Map<String, UUID> zonesByName = zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId)
                .stream().collect(Collectors.toMap(z -> z.getName(), z -> z.getId(), (a, b) -> a));
        UUID fallback = zonesByName.values().stream().findFirst().orElse(null);
        return new SessionStructure(
                mapBlocks(s.warmup(), zonesByName, fallback),
                mapBlocks(s.main(), zonesByName, fallback),
                mapBlocks(s.cooldown(), zonesByName, fallback));
    }

    private boolean needsMapping(SessionStructure s) {
        return List.of(s.warmup(), s.main(), s.cooldown()).stream()
                .flatMap(List::stream)
                .anyMatch(b -> isLegacyWithoutZone(b.prescription())
                        || (b.recovery() != null && isLegacyWithoutZone(b.recovery().prescription())));
    }

    /**
     * Bloc encore prescrit en {@code ref + %} sans zone. Une fourchette <b>voulue</b> par le coach
     * (« 102–106 % de VC ») n'est pas un reliquat à migrer : lui recoller une zone reviendrait à
     * remplacer sa cible par la bande standard la plus proche, à son insu.
     */
    private boolean isLegacyWithoutZone(CoursePrescription p) {
        return p != null && !p.hasZone() && p.ref() != null && !p.isCustomRange();
    }

    private List<CourseBlock> mapBlocks(List<CourseBlock> blocks, Map<String, UUID> zonesByName, UUID fallback) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream().map(b -> new CourseBlock(
                b.id(), b.type(), b.reps(), b.distanceM(), b.durationS(),
                mapPrescription(b.prescription(), zonesByName, fallback),
                mapRecovery(b.recovery(), zonesByName, fallback),
                b.rpe(), b.note(), b.drillIds())).toList();
    }

    private CourseRecovery mapRecovery(CourseRecovery r, Map<String, UUID> zonesByName, UUID fallback) {
        if (r == null) {
            return null;
        }
        return new CourseRecovery(r.type(), r.durationS(), r.distanceM(),
                mapPrescription(r.prescription(), zonesByName, fallback));
    }

    /** Ajoute le {@code zoneId} déduit à une prescription legacy ; laisse tout le reste intact. */
    private CoursePrescription mapPrescription(CoursePrescription p, Map<String, UUID> zonesByName, UUID fallback) {
        if (!isLegacyWithoutZone(p)) {
            return p;
        }
        UUID zoneId = zonesByName.getOrDefault(zoneNameFor(p.ref(), p.minPct(), p.maxPct()), fallback);
        return new CoursePrescription(zoneId, p.hrZoneId(), p.ref(), p.minPct(), p.maxPct(), p.custom());
    }

    /**
     * Zone standard la plus proche d'un référentiel + bande % (miroir des règles seedées), pour la
     * migration douce des anciens blocs {@code ref + %} vers l'échelle d'allure nommée.
     */
    private String zoneNameFor(PrescriptionRef ref, Double minPct, Double maxPct) {
        double mid = (nz(minPct) + nz(maxPct)) / 2.0;
        return switch (ref) {
            case PCT_LT1 -> mid < 76 ? "Footing facile" : (mid < 93 ? "EF" : (mid < 100 ? "Steady" : "Seuil 1"));
            case PCT_LT2 -> mid < 96 ? "Tempo" : (mid < 100 ? "Seuil 2 bas" : "Seuil 2 haut");
            case PCT_PACE_10KM, PCT_PACE_15KM -> "10 km";
            case PCT_VC, PCT_PACE_5KM -> "5 km";
            case PCT_PACE_3000M -> "3 km";
            case PCT_VMA, PCT_PACE_1500M -> "1500 m";
            case PCT_PACE_800M -> "800 m";
            case PCT_PACE_SEMI, PCT_PACE_MARATHON -> "EF";
        };
    }

    private double nz(Double v) {
        return v == null ? 100.0 : v;
    }
}
