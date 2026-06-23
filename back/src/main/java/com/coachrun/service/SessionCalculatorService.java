package com.coachrun.service;

import com.coachrun.dto.request.SessionCalcRequest;
import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.CoursePrescription;
import com.coachrun.dto.session.CourseRecovery;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.engine.SessionCalculatorEngine;
import com.coachrun.engine.SessionCalculatorEngine.AthletePaceContext;
import com.coachrun.engine.SessionCalculatorEngine.PrescriptionInput;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteVdotPace;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteVdotPaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calcule les cibles d'entraînement pour un athlète : un bloc isolé (aperçu live de l'éditeur)
 * ou une séance entière (échauffement/corps/retour). Assemble le profil physio + les allures VDOT
 * de l'athlète puis délègue au {@link SessionCalculatorEngine}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionCalculatorService {

    private final AthleteRepository athleteRepository;
    private final AthleteVdotPaceRepository vdotPaceRepository;
    private final SessionCalculatorEngine engine;

    /** Calcul d'un bloc isolé (aperçu live de l'éditeur). */
    public CalculatedBlockResponse calculate(UUID clubId, UUID athleteId, SessionCalcRequest req) {
        AthletePaceContext ctx = contextFor(clubId, athleteId);
        PrescriptionInput input = new PrescriptionInput(
                req.ref(), req.minPct(), req.maxPct(), req.reps(), req.distanceM(), req.durationS());
        return CalculatedBlockResponse.from(engine.calculate(input, ctx));
    }

    /** Calcul d'une séance entière pour un athlète, avec totaux estimés. */
    public CalculatedSessionResponse calculateSession(UUID clubId, UUID athleteId, SessionStructure structure) {
        AthletePaceContext ctx = contextFor(clubId, athleteId);
        SessionStructure s = structure == null ? SessionStructure.empty() : structure;

        Totals totals = new Totals();
        List<CalculatedBlockEntry> warmup = calcSection(s.warmup(), ctx, totals);
        List<CalculatedBlockEntry> main = calcSection(s.main(), ctx, totals);
        List<CalculatedBlockEntry> cooldown = calcSection(s.cooldown(), ctx, totals);

        return new CalculatedSessionResponse(warmup, main, cooldown,
                totals.distanceM > 0 ? totals.distanceM : null,
                totals.durationS > 0 ? totals.durationS : null);
    }

    // --- Internes -------------------------------------------------------------

    private List<CalculatedBlockEntry> calcSection(List<CourseBlock> blocks, AthletePaceContext ctx, Totals totals) {
        List<CalculatedBlockEntry> entries = new ArrayList<>();
        if (blocks == null) {
            return entries;
        }
        for (CourseBlock block : blocks) {
            CalculatedBlockResponse calc = calcBlock(block, ctx);
            CalculatedBlockResponse recoveryCalc = calcRecovery(block.recovery(), ctx);
            if (calc != null && calc.computable()) {
                if (calc.estimatedDistanceM() != null) {
                    totals.distanceM += calc.estimatedDistanceM();
                }
                if (calc.estimatedDurationS() != null) {
                    totals.durationS += calc.estimatedDurationS();
                }
            }
            // Récupération entre répétitions (reps-1 intervalles).
            if (block.recovery() != null && block.recovery().durationS() != null) {
                int reps = block.reps() == null || block.reps() <= 1 ? 1 : block.reps() - 1;
                totals.durationS += block.recovery().durationS() * reps;
            }
            entries.add(new CalculatedBlockEntry(block, calc, recoveryCalc));
        }
        return entries;
    }

    private CalculatedBlockResponse calcBlock(CourseBlock block, AthletePaceContext ctx) {
        CoursePrescription p = block.prescription();
        if (p == null || p.ref() == null || p.minPct() == null || p.maxPct() == null) {
            return null;
        }
        PrescriptionInput input = new PrescriptionInput(
                p.ref(), p.minPct(), p.maxPct(), block.reps(), block.distanceM(), block.durationS());
        return CalculatedBlockResponse.from(engine.calculate(input, ctx));
    }

    private CalculatedBlockResponse calcRecovery(CourseRecovery recovery, AthletePaceContext ctx) {
        if (recovery == null || recovery.prescription() == null
                || recovery.prescription().ref() == null
                || recovery.prescription().minPct() == null || recovery.prescription().maxPct() == null) {
            return null;
        }
        CoursePrescription p = recovery.prescription();
        PrescriptionInput input = new PrescriptionInput(
                p.ref(), p.minPct(), p.maxPct(), null, recovery.distanceM(), recovery.durationS());
        return CalculatedBlockResponse.from(engine.calculate(input, ctx));
    }

    private AthletePaceContext contextFor(UUID clubId, UUID athleteId) {
        Athlete a = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        AthleteVdotPace paces = vdotPaceRepository.findByAthleteId(athleteId).orElse(null);
        return new AthletePaceContext(
                toDouble(a.getLt1Ms()), toDouble(a.getLt2Ms()), toDouble(a.getVcMs()),
                a.getFcLt1(), a.getFcLt2(), a.getHrMax(),
                pct(a.getFcDomain1Pct(), 80), pct(a.getFcDomain2Pct(), 90),
                paces == null ? null : paces.getPace800mS(),
                paces == null ? null : paces.getPace1500mS(),
                paces == null ? null : paces.getPace3000mS(),
                paces == null ? null : paces.getPace5kmS(),
                paces == null ? null : paces.getPace10kmS(),
                paces == null ? null : paces.getPace15kmS(),
                paces == null ? null : paces.getPaceSemiS(),
                paces == null ? null : paces.getPaceMarathonS());
    }

    private Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private double pct(BigDecimal v, double fallback) {
        return v == null ? fallback : v.doubleValue();
    }

    private static final class Totals {
        int distanceM;
        int durationS;
    }
}
