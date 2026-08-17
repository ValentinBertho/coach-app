package com.coachrun.service;

import com.coachrun.dto.response.ProposalResponse;
import com.coachrun.engine.SessionAdaptationEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ProposalStatus;
import com.coachrun.entity.enums.ProposalType;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteProposalRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les propositions du produit, et la <b>seule</b> porte par laquelle elles s'appliquent.
 *
 * <h2>L'invariant</h2>
 *
 * <p>Aucune détection, nulle part dans le produit, n'écrit directement dans le calendrier ou dans
 * le profil d'un athlète. Tout passe par ici, et {@link #accept} exige un utilisateur : c'est ce
 * qui garantit qu'une séance porte toujours la signature d'un humain. Un plan d'entraînement est
 * l'engagement d'un coach envers son athlète ; une application qui le réécrit toute seule détruit
 * exactement ce qu'elle prétend servir.</p>
 *
 * <h2>Deux conséquences pratiques</h2>
 *
 * <p>D'abord, la structure adaptée est <b>recalculée au moment de l'acceptation</b>, pas au moment
 * de la détection : entre les deux, le coach a pu retoucher la séance, et appliquer une version
 * figée la veille écraserait son travail sans le dire.</p>
 *
 * <p>Ensuite, refuser est une information qu'on garde ({@link ProposalStatus#DISMISSED}) et qui
 * bloque la même suggestion pour toujours, via {@code dedupeKey}. Une proposition qui revient après
 * un refus apprend à l'utilisateur à ne plus rien lire.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalService {

    private final AthleteProposalRepository repository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutService workoutService;
    private final AthletePhysioService physioService;
    private final StrengthScheduleService strengthScheduleService;
    private final SessionAdaptationEngine adaptationEngine;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ClockService clock;

    // ---------------------------------------------------------------------
    // Création
    // ---------------------------------------------------------------------

    /**
     * Enregistre une proposition, sauf si la même cause en a déjà produit une.
     *
     * @return la proposition créée, ou {@code null} si elle faisait doublon
     */
    @Transactional
    public AthleteProposal propose(Athlete athlete, ProposalType type, UUID targetId,
                                   LocalDate targetDate, String title, String reason,
                                   Object payload, String dedupeKey, boolean requestedByAthlete) {
        if (repository.existsByAthleteIdAndDedupeKey(athlete.getId(), dedupeKey)) {
            return null;
        }
        AthleteProposal p = new AthleteProposal();
        p.setClub(athlete.getClub());
        p.setAthlete(athlete);
        p.setType(type);
        p.setTargetId(targetId);
        p.setTargetDate(targetDate);
        p.setTitle(title);
        p.setReason(reason);
        p.setPayloadJson(payload == null ? null : writeJson(payload));
        p.setDedupeKey(dedupeKey);
        p.setRequestedByAthlete(requestedByAthlete);
        AthleteProposal saved = repository.save(p);
        log.info("Proposition {} créée pour l'athlète {} ({})", type, athlete.getId(), dedupeKey);
        return saved;
    }

    // ---------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------

    /** Propositions en attente d'un lot d'athlètes — la file du coach. */
    public List<ProposalResponse> pendingFor(List<Athlete> athletes) {
        if (athletes.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = athletes.stream().collect(java.util.stream.Collectors.toMap(
                Athlete::getId, a -> (a.getFirstName() + " " + a.getLastName()).trim()));
        return repository.findPending(List.copyOf(names.keySet()), ProposalStatus.PENDING).stream()
                .map(p -> ProposalResponse.from(p, names.get(p.getAthlete().getId())))
                .toList();
    }

    /** Mes propositions en attente — portail athlète. */
    public List<ProposalResponse> pendingForAthlete(UUID athleteId) {
        return repository.findByAthleteIdAndStatusOrderByCreatedAtDesc(athleteId, ProposalStatus.PENDING)
                .stream().map(p -> ProposalResponse.from(p, null)).toList();
    }

    // ---------------------------------------------------------------------
    // Décision
    // ---------------------------------------------------------------------

    /**
     * Accepte une proposition et applique son effet. L'utilisateur est obligatoire : c'est la
     * signature humaine qui manque à toute écriture automatique.
     */
    @Transactional
    public ProposalResponse accept(UUID clubId, UUID proposalId, UUID userId) {
        AthleteProposal p = require(clubId, proposalId);
        if (p.getStatus() != ProposalStatus.PENDING) {
            throw new ConflictException("Cette proposition a déjà été traitée.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        apply(p);

        p.setStatus(ProposalStatus.ACCEPTED);
        p.setDecidedBy(user);
        p.setDecidedAt(Instant.now());
        log.info("Proposition {} acceptée par {} (athlète {})", p.getType(), userId, p.getAthlete().getId());
        return ProposalResponse.from(p, null);
    }

    /** Refuse une proposition. Elle reste en base : trois refus de la même suggestion, c'est un signal. */
    @Transactional
    public ProposalResponse dismiss(UUID clubId, UUID proposalId, UUID userId) {
        AthleteProposal p = require(clubId, proposalId);
        if (p.getStatus() != ProposalStatus.PENDING) {
            throw new ConflictException("Cette proposition a déjà été traitée.");
        }
        p.setStatus(ProposalStatus.DISMISSED);
        p.setDecidedBy(userRepository.findById(userId).orElse(null));
        p.setDecidedAt(Instant.now());
        return ProposalResponse.from(p, null);
    }

    /**
     * Périme les propositions qui n'ont plus d'objet : la séance visée a disparu, ou le jour est
     * passé. Distinct d'un refus — personne n'a rien décidé, le monde a bougé.
     */
    @Transactional
    public int expireStale() {
        int expired = 0;
        for (AthleteProposal p : repository.findStale(clock.today())) {
            p.setStatus(ProposalStatus.EXPIRED);
            expired++;
        }
        return expired;
    }

    /** Périme les propositions visant une séance qu'on vient de supprimer. */
    @Transactional
    public void expireForTarget(UUID targetId) {
        for (AthleteProposal p : repository.findByTargetIdAndStatus(targetId, ProposalStatus.PENDING)) {
            p.setStatus(ProposalStatus.EXPIRED);
        }
    }

    // ---------------------------------------------------------------------
    // Le dispatcher : une branche par type, et pas de branche muette
    // ---------------------------------------------------------------------

    private void apply(AthleteProposal p) {
        switch (p.getType()) {
            case SESSION_LIGHTEN -> applySessionAdaptation(p, true);
            case SESSION_EASY -> applySessionAdaptation(p, false);
            case SESSION_MOVE -> applySessionMove(p);
            case SESSION_DROP -> applySessionDrop(p);
            case PHYSIO_UPDATE -> applyPhysioUpdate(p);
            case STRENGTH_LOAD -> applyStrengthLoad(p);
        }
    }

    /**
     * Réécrit la structure de la séance visée. La version adaptée est <b>calculée maintenant</b>,
     * à partir de la séance telle qu'elle est aujourd'hui — pas telle qu'elle était au moment de la
     * détection. Sinon, accepter le matin une proposition faite la veille écraserait, sans le dire,
     * la retouche que le coach a faite entre-temps.
     */
    private void applySessionAdaptation(AthleteProposal p, boolean lighten) {
        Workout w = requireWorkout(p);
        var prescription = workoutService.prescription(p.getClub().getId(), w.getId());
        var snapshot = prescription.snapshot();
        if (snapshot == null) {
            throw new ConflictException("Cette séance n'a pas de structure à adapter.");
        }
        Integer mainDurationS = prescription.calculated() == null ? null
                : mainDuration(prescription.calculated());

        var adapted = lighten
                ? adaptationEngine.lighten(snapshot)
                : adaptationEngine.easy(snapshot, mainDurationS);
        workoutService.updateStructure(p.getClub().getId(), w.getId(), adapted);
    }

    /** Durée estimée du corps de séance, telle que le calculateur l'a établie. */
    private Integer mainDuration(com.coachrun.dto.response.CalculatedSessionResponse calc) {
        int total = 0;
        for (var e : calc.main()) {
            if (e.calc() != null && e.calc().estimatedDurationS() != null) {
                total += e.calc().estimatedDurationS();
            }
        }
        return total > 0 ? total : null;
    }

    private void applySessionMove(AthleteProposal p) {
        Workout w = requireWorkout(p);
        LocalDate date = readDate(p, "date");
        workoutService.reschedule(p.getClub().getId(), w.getId(), date);
    }

    private void applySessionDrop(AthleteProposal p) {
        Workout w = requireWorkout(p);
        workoutService.delete(p.getClub().getId(), w.getId());
    }

    /**
     * Enregistre la performance détectée comme une performance de référence.
     *
     * <p>On passe volontairement par {@code addPerformance} plutôt que d'écrire le VDOT à la main :
     * c'est ce chemin qui déclenche la cascade complète — VDOT, allures d'équivalence,
     * resynchronisation des zones, et donc cibles des séances à venir. Écrire le VDOT seul
     * laisserait le reste du profil derrière.</p>
     */
    private void applyPhysioUpdate(AthleteProposal p) {
        JsonNode payload = readPayload(p);
        String distance = text(payload, "distance");
        int timeSeconds = intValue(payload, "timeSeconds", 0);
        String dateIso = text(payload, "date");
        if (distance == null || timeSeconds <= 0) {
            throw new ConflictException("Cette proposition n'a pas de performance exploitable.");
        }
        physioService.addPerformance(p.getClub().getId(), p.getAthlete().getId(),
                new com.coachrun.dto.request.PerformanceRequest(
                        com.coachrun.entity.enums.RunDistance.valueOf(distance),
                        timeSeconds,
                        dateIso != null ? LocalDate.parse(dateIso) : clock.today()));
    }

    /** Ajuste la charge d'un exercice dans la prochaine séance de renforcement déjà planifiée. */
    private void applyStrengthLoad(AthleteProposal p) {
        JsonNode payload = readPayload(p);
        UUID exerciseId = UUID.fromString(text(payload, "exerciseId"));
        double deltaKg = payload.path("deltaKg").asDouble(0);
        if (p.getTargetId() == null || deltaKg == 0) {
            throw new ConflictException("Cette proposition n'a pas de charge à appliquer.");
        }
        strengthScheduleService.shiftExerciseCharge(
                p.getClub().getId(), p.getAthlete().getId(), p.getTargetId(), exerciseId, deltaKg);
    }

    // ---------------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------------

    private AthleteProposal require(UUID clubId, UUID proposalId) {
        return repository.findByIdAndClubId(proposalId, clubId)
                .orElseThrow(() -> new NotFoundException("Proposition introuvable."));
    }

    private Workout requireWorkout(AthleteProposal p) {
        if (p.getTargetId() == null) {
            throw new ConflictException("Cette proposition ne vise aucune séance.");
        }
        return workoutRepository.findById(p.getTargetId())
                .filter(w -> w.getAthlete().getId().equals(p.getAthlete().getId()))
                .orElseThrow(() -> new NotFoundException("La séance visée n'existe plus."));
    }

    private LocalDate readDate(AthleteProposal p, String field) {
        String raw = text(readPayload(p), field);
        if (raw == null) {
            throw new ConflictException("Cette proposition n'a pas de date cible.");
        }
        return LocalDate.parse(raw);
    }

    private JsonNode readPayload(AthleteProposal p) {
        try {
            return objectMapper.readTree(p.getPayloadJson() == null ? "{}" : p.getPayloadJson());
        } catch (Exception e) {
            throw new ConflictException("Proposition illisible.");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? fallback : v.asInt(fallback);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Sérialisation impossible.", e);
        }
    }

    /** Prévient le coach référent qu'un athlète demande quelque chose — une demande attend une réponse. */
    void notifyCoachOfRequest(AthleteProposal p) {
        notificationService.notifyAthleteProposalRequest(p.getAthlete(), p.getTitle(), p.getReason());
    }
}
