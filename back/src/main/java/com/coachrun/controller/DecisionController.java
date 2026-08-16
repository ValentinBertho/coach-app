package com.coachrun.controller;

import com.coachrun.dto.request.PlanFromRaceRequest;
import com.coachrun.dto.request.RaceResultRequest;
import com.coachrun.dto.response.ComplianceResponse;
import com.coachrun.dto.response.PlanPreviewResponse;
import com.coachrun.dto.response.ProposalResponse;
import com.coachrun.dto.response.TimelineResponse;
import com.coachrun.dto.response.TrajectoryResponse;
import com.coachrun.dto.response.WeekOutlookResponse;
import com.coachrun.service.AthleteTimelineService;
import com.coachrun.service.ComplianceService;
import com.coachrun.service.PlanBuilderService;
import com.coachrun.service.RecoveryService;
import com.coachrun.service.TrajectoryService;
import com.coachrun.service.WeekOutlookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Les lectures qui concluent, côté coach : conformité d'une séance, trajectoire vers l'objectif,
 * perspective de la semaine, trame de plan, frise de l'athlète.
 *
 * <p>Regroupées ici parce qu'elles répondent toutes à la même question — <i>et donc, qu'est-ce
 * qu'on fait ?</i> — et parce qu'aucune n'a assez de surface pour justifier son propre contrôleur.
 * Les deux seules routes qui écrivent ({@code /plan/apply} et {@code /result}) exigent le droit
 * d'écriture sur l'athlète, comme partout ailleurs.</p>
 */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) "
        + "and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class DecisionController {

    private final ComplianceService complianceService;
    private final TrajectoryService trajectoryService;
    private final WeekOutlookService weekOutlookService;
    private final PlanBuilderService planBuilderService;
    private final AthleteTimelineService timelineService;
    private final RecoveryService recoveryService;

    /** « Séance tenue ? » — le verdict d'une séance, bloc par bloc. */
    @GetMapping("/workouts/{workoutId}/compliance")
    public ComplianceResponse compliance(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                         @PathVariable UUID workoutId) {
        return complianceService.forCoach(clubId, workoutId);
    }

    /**
     * Trajectoire vers la prochaine course. {@code 204} quand l'athlète n'a pas d'objectif : une
     * absence d'objectif n'est pas une erreur, et l'interface doit pouvoir se taire.
     */
    @GetMapping("/trajectory")
    public ResponseEntity<TrajectoryResponse> trajectory(@PathVariable UUID clubId,
                                                         @PathVariable UUID athleteId) {
        TrajectoryResponse t = trajectoryService.forCoach(clubId, athleteId);
        return t == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(t);
    }

    /** La semaine prévue, jugée avant d'être courue. */
    @GetMapping("/week-outlook")
    public WeekOutlookResponse weekOutlook(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return weekOutlookService.forWeek(clubId, athleteId, week);
    }

    /** La frise : six mois de suivi rassemblés. */
    @GetMapping("/timeline")
    public TimelineResponse timeline(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return timelineService.forCoach(clubId, athleteId, from, to);
    }

    /** Trame du plan jusqu'à une course — aperçu, sans rien écrire. */
    @PostMapping("/races/{raceId}/plan/preview")
    public PlanPreviewResponse planPreview(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @PathVariable UUID raceId,
                                           @Valid @RequestBody PlanFromRaceRequest request) {
        return planBuilderService.preview(clubId, raceId, request);
    }

    /** Pose le plan dans le calendrier. Geste explicite, jamais un effet de bord de l'aperçu. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) "
            + "and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/races/{raceId}/plan/apply")
    public PlanPreviewResponse planApply(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                         @PathVariable UUID raceId,
                                         @Valid @RequestBody PlanFromRaceRequest request) {
        return planBuilderService.apply(clubId, raceId, request);
    }

    /**
     * Enregistre le chrono réalisé sur une course. Sur une distance de référence, il devient une
     * performance — et le VDOT, les allures et les zones suivent.
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) "
            + "and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/races/{raceId}/result")
    public ResponseEntity<TrajectoryResponse> recordResult(@PathVariable UUID clubId,
                                                           @PathVariable UUID athleteId,
                                                           @PathVariable UUID raceId,
                                                           @Valid @RequestBody RaceResultRequest request) {
        TrajectoryResponse t = trajectoryService.recordResult(clubId, raceId, request.timeSeconds());
        return t == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(t);
    }

    /**
     * Propose de replanifier les séances manquées des derniers jours.
     *
     * <p>Écrit des propositions, pas des séances : rien ne bouge tant que le coach n'a pas accepté.
     * C'est pour cela que la route exige le droit d'écriture bien qu'elle ne déplace rien — créer
     * une file de décisions dans le calendrier de quelqu'un est déjà une intervention.</p>
     */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) "
            + "and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/catch-up")
    public List<ProposalResponse> catchUp(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                          @RequestParam(defaultValue = "14") int days) {
        return recoveryService.proposeCatchUpForAthlete(clubId, athleteId, days);
    }
}
