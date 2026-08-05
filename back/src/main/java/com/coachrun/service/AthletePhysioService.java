package com.coachrun.service;

import com.coachrun.dto.request.PerformanceRequest;
import com.coachrun.dto.request.PhysioProfileRequest;
import com.coachrun.dto.request.VcTestRequest;
import com.coachrun.dto.response.VcTestResponse;
import com.coachrun.engine.CriticalSpeedEngine;
import com.coachrun.dto.response.PerformanceResponse;
import com.coachrun.dto.response.PhysioProfileResponse;
import com.coachrun.dto.response.VdotResponse;
import com.coachrun.engine.PaceUtil;
import com.coachrun.engine.VdotEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthletePerformance;
import com.coachrun.entity.AthleteVdotPace;
import com.coachrun.entity.enums.RunDistance;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthletePerformanceRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteVdotPaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Profil physiologique : seuils, performances et VDOT. Le VDOT et les allures d'équivalence
 * sont <strong>recalculés automatiquement</strong> à chaque modification des performances
 * (cf. DARI Lab — recalcul auto). Scoping tenant systématique (anti-IDOR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthletePhysioService {

    private final AthleteRepository athleteRepository;
    private final AthletePerformanceRepository performanceRepository;
    private final AthleteVdotPaceRepository vdotPaceRepository;
    private final CriticalSpeedEngine criticalSpeedEngine;
    private final VdotEngine vdotEngine;
    private final ZoneValueSyncService zoneValueSyncService;
    private final NotificationService notificationService;

    // ---------------------------------------------------------------------
    // Profil physiologique
    // ---------------------------------------------------------------------

    public PhysioProfileResponse getProfile(UUID clubId, UUID athleteId) {
        return PhysioProfileResponse.from(requireAthlete(clubId, athleteId));
    }

    @Transactional
    public PhysioProfileResponse updateProfile(UUID clubId, UUID athleteId, PhysioProfileRequest req) {
        Athlete a = requireAthlete(clubId, athleteId);
        if (req.discipline() != null) {
            a.setDiscipline(req.discipline());
        }
        a.setLt1Ms(req.lt1Ms());
        a.setLt2Ms(req.lt2Ms());
        a.setVcMs(req.vcMs());
        if (req.fcMax() != null) {
            a.setHrMax(req.fcMax());
        }
        a.setFcLt1(req.fcLt1());
        a.setFcLt2(req.fcLt2());
        if (req.vcDomain1Pct() != null) {
            a.setVcDomain1Pct(req.vcDomain1Pct());
        }
        if (req.vcDomain2Pct() != null) {
            a.setVcDomain2Pct(req.vcDomain2Pct());
        }
        if (req.fcDomain1Pct() != null) {
            a.setFcDomain1Pct(req.fcDomain1Pct());
        }
        if (req.fcDomain2Pct() != null) {
            a.setFcDomain2Pct(req.fcDomain2Pct());
        }
        // Recalcul auto des zones (valeurs AUTO non verrouillées) : une ancre a changé.
        zoneValueSyncService.resync(clubId, athleteId);
        log.info("Profil physio mis à jour pour l'athlète {} (zones resynchronisées)", athleteId);
        return PhysioProfileResponse.from(a);
    }

    // ---------------------------------------------------------------------
    // Performances + VDOT
    // ---------------------------------------------------------------------

    public List<PerformanceResponse> listPerformances(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        return performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(athleteId).stream()
                .map(p -> PerformanceResponse.from(p, vdotOf(p)))
                .toList();
    }

    @Transactional
    public PerformanceResponse addPerformance(UUID clubId, UUID athleteId, PerformanceRequest req) {
        Athlete a = requireAthlete(clubId, athleteId);
        // Avant l'enregistrement : c'est le meilleur chrono *antérieur* sur cette distance qui
        // dit si celui-ci est un record.
        boolean record = beatsPreviousBest(athleteId, req);

        AthletePerformance perf = new AthletePerformance();
        perf.setAthlete(a);
        perf.setDistance(req.distance());
        perf.setTimeSeconds(req.timeSeconds());
        perf.setDateSet(req.dateSet());
        perf = performanceRepository.save(perf);
        recomputeVdot(a);
        // Recalcul auto des zones : les allures VDOT (ancres) ont changé.
        zoneValueSyncService.resync(clubId, athleteId);
        log.info("Performance {} ajoutée pour l'athlète {} (recalcul VDOT + zones)", req.distance(), athleteId);
        if (record) {
            notificationService.notifyPersonalRecord(a, req.distance().code(), formatTime(req.timeSeconds()));
        }
        return PerformanceResponse.from(perf, vdotOf(perf));
    }

    /**
     * Ce chrono bat-il tout ce que l'athlète a déjà couru sur cette distance ?
     *
     * <p>Un <b>premier</b> chrono sur une distance n'est pas un record : c'est une référence. On ne
     * félicite que ce qui a été amélioré — sinon la saisie initiale d'un profil, qui enchaîne
     * volontiers cinq distances, déclencherait cinq célébrations d'un coup.</p>
     */
    private boolean beatsPreviousBest(UUID athleteId, PerformanceRequest req) {
        return performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(athleteId).stream()
                .filter(p -> p.getDistance() == req.distance())
                .mapToInt(AthletePerformance::getTimeSeconds)
                .min()
                .stream()
                .anyMatch(best -> req.timeSeconds() < best);
    }

    /** « 38:42 », ou « 2:59:31 » au-delà de l'heure. */
    private static String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** Portail athlète : l'athlète déclare lui-même une perf de référence (bootstrap VDOT/allures). */
    @Transactional
    public PerformanceResponse addPerformanceForAthlete(UUID athleteId, PerformanceRequest req) {
        Athlete a = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return addPerformance(a.getClub().getId(), athleteId, req);
    }

    /**
     * Corrige un record existant (temps, date, distance). Une faute de frappe imposait jusqu'ici
     * de supprimer puis resaisir — ce qui faisait descendre puis remonter le VDOT et les zones.
     * Même resync que l'ajout : le VDOT et les allures d'ancrage suivent la correction.
     */
    @Transactional
    public PerformanceResponse updatePerformance(UUID clubId, UUID athleteId, UUID performanceId,
                                                 PerformanceRequest req) {
        Athlete a = requireAthlete(clubId, athleteId);
        AthletePerformance perf = performanceRepository.findByIdAndAthleteId(performanceId, athleteId)
                .orElseThrow(() -> new NotFoundException("Performance introuvable."));
        perf.setDistance(req.distance());
        perf.setTimeSeconds(req.timeSeconds());
        perf.setDateSet(req.dateSet());
        performanceRepository.flush();
        recomputeVdot(a);
        zoneValueSyncService.resync(clubId, athleteId);
        log.info("Performance {} corrigée pour l'athlète {} (recalcul VDOT + zones)",
                req.distance(), athleteId);
        return PerformanceResponse.from(perf, vdotOf(perf));
    }

    @Transactional
    public void deletePerformance(UUID clubId, UUID athleteId, UUID performanceId) {
        Athlete a = requireAthlete(clubId, athleteId);
        AthletePerformance perf = performanceRepository.findByIdAndAthleteId(performanceId, athleteId)
                .orElseThrow(() -> new NotFoundException("Performance introuvable."));
        performanceRepository.delete(perf);
        performanceRepository.flush();
        recomputeVdot(a);
        // Symétrique de l'ajout : retirer un record redescend le VDOT, donc les allures qui
        // ancrent les zones de compétition. Sans ce resync les cibles resteraient sur le record
        // supprimé.
        zoneValueSyncService.resync(clubId, athleteId);
        log.info("Performance {} supprimée pour l'athlète {} (recalcul VDOT + zones)",
                perf.getDistance(), athleteId);
    }

    public VdotResponse getVdot(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        return buildVdot(athleteId);
    }

    /** Profil physio — variante athlète-scopée (portail /me, lecture seule). */
    public PhysioProfileResponse getProfileForAthlete(UUID athleteId) {
        return PhysioProfileResponse.from(athleteRepository.findById(athleteId)
                .orElseThrow(() -> new com.coachrun.exception.NotFoundException("Athlète introuvable.")));
    }

    /** VDOT + allures — variante athlète-scopée (portail /me). */
    public VdotResponse getVdotForAthlete(UUID athleteId) {
        return buildVdot(athleteId);
    }

    /** Mes performances/records — variante athlète-scopée (portail /me, lecture seule). */
    public List<PerformanceResponse> listPerformancesForAthlete(UUID athleteId) {
        return performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(athleteId).stream()
                .map(p -> PerformanceResponse.from(p, vdotOf(p)))
                .toList();
    }

    private VdotResponse buildVdot(UUID athleteId) {
        AthleteVdotPace paces = vdotPaceRepository.findByAthleteId(athleteId).orElse(null);
        if (paces == null || paces.getVdot() == null) {
            return VdotResponse.empty();
        }
        List<VdotResponse.VdotPaceItem> items = new ArrayList<>();
        items.add(item(RunDistance.D800, paces.getPace800mS()));
        items.add(item(RunDistance.D1500, paces.getPace1500mS()));
        items.add(item(RunDistance.D3000, paces.getPace3000mS()));
        items.add(item(RunDistance.D5KM, paces.getPace5kmS()));
        items.add(item(RunDistance.D10KM, paces.getPace10kmS()));
        items.add(item(RunDistance.D15KM, paces.getPace15kmS()));
        items.add(item(RunDistance.SEMI, paces.getPaceSemiS()));
        items.add(item(RunDistance.MARATHON, paces.getPaceMarathonS()));
        return new VdotResponse(paces.getVdot(), items, trainingPaces(paces.getVdot().doubleValue()));
    }

    /**
     * Allures d'entraînement dérivées du VDOT : endurance fondamentale et seuil. Les équivalences
     * de course ne disent pas à quelle allure faire un footing ou un seuil — c'est pourtant ce que
     * le coach lit en premier pour prescrire.
     */
    private List<VdotResponse.VdotPaceItem> trainingPaces(double vdot) {
        return List.of(
                pace("EASY", vdotEngine.easyPaceSecPerKm(vdot)),
                pace("THRESHOLD", vdotEngine.thresholdPaceSecPerKm(vdot)));
    }

    private VdotResponse.VdotPaceItem pace(String code, int paceSecPerKm) {
        double kmh = Math.round(PaceUtil.secPerKmToKmh(paceSecPerKm) * 10.0) / 10.0;
        return new VdotResponse.VdotPaceItem(code, paceSecPerKm, PaceUtil.formatPace(paceSecPerKm), kmh);
    }

    // ---------------------------------------------------------------------
    // Recalcul automatique
    // ---------------------------------------------------------------------

    /** Recalcule le meilleur VDOT de l'athlète et ses allures d'équivalence. */
    private void recomputeVdot(Athlete athlete) {
        Double best = performanceRepository.findByAthleteIdOrderByDateSetDescCreatedAtDesc(athlete.getId())
                .stream()
                .filter(p -> p.getDistance().hasFixedDistance() && p.getTimeSeconds() > 0)
                .map(this::vdotOf)
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);

        AthleteVdotPace paces = vdotPaceRepository.findByAthleteId(athlete.getId())
                .orElseGet(() -> {
                    AthleteVdotPace p = new AthleteVdotPace();
                    p.setAthlete(athlete);
                    return p;
                });

        if (best == null) {
            athlete.setVdot(null);
            paces.setVdot(null);
            setAllPaces(paces, null);
        } else {
            BigDecimal rounded = BigDecimal.valueOf(best).setScale(2, RoundingMode.HALF_UP);
            athlete.setVdot(rounded);
            paces.setVdot(rounded);
            paces.setPace800mS(vdotEngine.racePaceSecPerKm(best, RunDistance.D800.meters()));
            paces.setPace1500mS(vdotEngine.racePaceSecPerKm(best, RunDistance.D1500.meters()));
            paces.setPace3000mS(vdotEngine.racePaceSecPerKm(best, RunDistance.D3000.meters()));
            paces.setPace5kmS(vdotEngine.racePaceSecPerKm(best, RunDistance.D5KM.meters()));
            paces.setPace10kmS(vdotEngine.racePaceSecPerKm(best, RunDistance.D10KM.meters()));
            paces.setPace15kmS(vdotEngine.racePaceSecPerKm(best, RunDistance.D15KM.meters()));
            paces.setPaceSemiS(vdotEngine.racePaceSecPerKm(best, RunDistance.SEMI.meters()));
            paces.setPaceMarathonS(vdotEngine.racePaceSecPerKm(best, RunDistance.MARATHON.meters()));
        }
        vdotPaceRepository.save(paces);
    }

    private void setAllPaces(AthleteVdotPace p, Integer value) {
        p.setPace800mS(value);
        p.setPace1500mS(value);
        p.setPace3000mS(value);
        p.setPace5kmS(value);
        p.setPace10kmS(value);
        p.setPace15kmS(value);
        p.setPaceSemiS(value);
        p.setPaceMarathonS(value);
    }

    private Double vdotOf(AthletePerformance p) {
        if (!p.getDistance().hasFixedDistance() || p.getTimeSeconds() <= 0) {
            return null;
        }
        double v = vdotEngine.vdot(p.getDistance().meters(), p.getTimeSeconds());
        return Math.round(v * 100.0) / 100.0;
    }

    private VdotResponse.VdotPaceItem item(RunDistance distance, Integer paceSecPerKm) {
        if (paceSecPerKm == null) {
            return new VdotResponse.VdotPaceItem(distance.code(), null, "—", null);
        }
        double kmh = Math.round(PaceUtil.secPerKmToKmh(paceSecPerKm) * 10.0) / 10.0;
        return new VdotResponse.VdotPaceItem(distance.code(), paceSecPerKm,
                PaceUtil.formatPace(paceSecPerKm), kmh);
    }


    /**
     * Test de Vitesse Critique. Les FC moyennes relevées sur chaque effort, si elles sont
     * fournies, donnent la FC tenue autour de la VC (moyenne pondérée par la durée des efforts) :
     * appliquée au profil, elle sert de FC de seuil et resynchronise les zones cardio.
     */
    @org.springframework.transaction.annotation.Transactional
    public VcTestResponse computeVc(UUID clubId, UUID athleteId, VcTestRequest req) {
        Athlete a = requireAthlete(clubId, athleteId);
        var trials = req.trials().stream()
                .map(t -> new CriticalSpeedEngine.Trial(t.distanceM(), t.timeS()))
                .toList();
        CriticalSpeedEngine.Result r = criticalSpeedEngine.compute(trials);
        Integer avgHr = weightedAvgHr(req);
        if (req.applyToProfile()) {
            a.setVcMs(java.math.BigDecimal.valueOf(r.vcMs()).setScale(3, java.math.RoundingMode.HALF_UP));
            if (avgHr != null) {
                a.setFcLt2(avgHr);
            }
            // Les zones s'ancrent sur la VC et la FC de seuil : sans resync, les cibles resteraient
            // sur les valeurs d'avant le test.
            zoneValueSyncService.resync(clubId, athleteId);
        }
        return new VcTestResponse(r.vcMs(), r.vcMs() * 3.6, r.dPrimeM(), avgHr);
    }

    /** FC moyenne des efforts, pondérée par leur durée ; null si aucune FC n'est renseignée. */
    private Integer weightedAvgHr(VcTestRequest req) {
        double weighted = 0;
        double seconds = 0;
        for (var t : req.trials()) {
            if (t.avgHr() != null) {
                weighted += t.avgHr() * t.timeS();
                seconds += t.timeS();
            }
        }
        return seconds == 0 ? null : (int) Math.round(weighted / seconds);
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
