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
        log.info("Profil physio mis à jour pour l'athlète {}", athleteId);
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
        AthletePerformance perf = new AthletePerformance();
        perf.setAthlete(a);
        perf.setDistance(req.distance());
        perf.setTimeSeconds(req.timeSeconds());
        perf.setDateSet(req.dateSet());
        perf = performanceRepository.save(perf);
        recomputeVdot(a);
        log.info("Performance {} ajoutée pour l'athlète {} (recalcul VDOT)", req.distance(), athleteId);
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

    private VdotResponse buildVdot(UUID athleteId) {
        AthleteVdotPace paces = vdotPaceRepository.findByAthleteId(athleteId).orElse(null);
        if (paces == null || paces.getVdot() == null) {
            return new VdotResponse(null, List.of());
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
        return new VdotResponse(paces.getVdot(), items);
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


    @org.springframework.transaction.annotation.Transactional
    public VcTestResponse computeVc(UUID clubId, UUID athleteId, VcTestRequest req) {
        Athlete a = requireAthlete(clubId, athleteId);
        var trials = req.trials().stream()
                .map(t -> new CriticalSpeedEngine.Trial(t.distanceM(), t.timeS()))
                .toList();
        CriticalSpeedEngine.Result r = criticalSpeedEngine.compute(trials);
        if (req.applyToProfile()) {
            a.setVcMs(java.math.BigDecimal.valueOf(r.vcMs()).setScale(3, java.math.RoundingMode.HALF_UP));
        }
        return new VcTestResponse(r.vcMs(), r.vcMs() * 3.6, r.dPrimeM());
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
