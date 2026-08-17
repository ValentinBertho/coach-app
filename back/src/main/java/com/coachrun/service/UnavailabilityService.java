package com.coachrun.service;

import com.coachrun.dto.request.UnavailabilityRequest;
import com.coachrun.dto.response.UnavailabilityResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteUnavailability;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteUnavailabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Indisponibilités d'un athlète (CRUD scopé club) + lecture côté athlète. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnavailabilityService {

    /**
     * Le rattrapage, injecté paresseusement : il lit le calendrier et la charge, et n'est sollicité
     * qu'à la déclaration d'une période. Une indisponibilité saisie laissait jusqu'ici neuf séances
     * dans le calendrier, qui devenaient neuf séances manquées puis autant d'alertes.
     */
    private final org.springframework.beans.factory.ObjectProvider<RecoveryService> recovery;

    private final AthleteUnavailabilityRepository repository;
    private final AthleteRepository athleteRepository;
    private final ClockService clock;
    private final NotificationService notificationService;
    private final com.coachrun.security.HealthDataConsentValidator consentValidator;

    /**
     * Un motif d'indisponibilité relève-t-il de l'article 9 ? Blessure et maladie décrivent un état
     * de santé ; vacances, personnel et autre décrivent une disponibilité. Un commentaire libre est
     * traité comme médical par précaution — c'est là qu'on écrit « déchirure ischio droit ».
     */
    private static boolean isMedical(UnavailabilityRequest req) {
        return req.reason() == com.coachrun.entity.enums.UnavailabilityReason.INJURY
                || req.reason() == com.coachrun.entity.enums.UnavailabilityReason.ILLNESS
                || org.springframework.util.StringUtils.hasText(req.notes());
    }

    public List<UnavailabilityResponse> list(UUID clubId, UUID athleteId) {
        return repository.findByClubIdAndAthleteIdOrderByStartDateDesc(clubId, athleteId)
                .stream().map(UnavailabilityResponse::from).toList();
    }

    /** Indisponibilités en cours ou à venir (portail athlète). */
    public List<UnavailabilityResponse> current(UUID athleteId) {
        return repository.findByAthleteIdAndEndDateGreaterThanEqualOrderByStartDateAsc(athleteId, clock.today())
                .stream().map(UnavailabilityResponse::from).toList();
    }

    @Transactional
    public UnavailabilityResponse create(UUID clubId, UUID athleteId, UnavailabilityRequest req) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        // Saisie par le coach : un motif médical exige une base légale, et le coach doit savoir
        // pourquoi elle manque — il a un recours (relancer l'invitation, demander le consentement).
        if (isMedical(req)) {
            consentValidator.requireConsent(athlete, "un motif d'indisponibilité médical");
        }
        AthleteUnavailability u = new AthleteUnavailability();
        u.setClub(athlete.getClub());
        u.setAthlete(athlete);
        apply(u, req);
        AthleteUnavailability saved = repository.save(u);
        proposeForCoveredSessions(clubId, saved);
        return UnavailabilityResponse.from(saved);
    }

    /**
     * Propose ce qu'on fait des séances que la période recouvre — sans y toucher.
     *
     * <p>C'est la moitié manquante du geste : déclarer une coupure, c'est décider qu'on ne
     * s'entraînera pas, et le calendrier devrait le refléter. Il ne le fait pas tout seul pour
     * autant : le coach voit la liste et tranche. Un échec ici ne doit jamais empêcher
     * l'enregistrement de la période elle-même.</p>
     */
    private void proposeForCoveredSessions(UUID clubId, AthleteUnavailability u) {
        try {
            RecoveryService service = recovery.getIfAvailable();
            if (service != null) {
                service.proposeForUnavailability(clubId, u);
            }
        } catch (RuntimeException e) {
            log.warn("Propositions de rattrapage impossibles pour l'indisponibilité {} : {}",
                    u.getId(), e.getMessage());
        }
    }

    /**
     * L'athlète déclare lui-même une indisponibilité depuis son portail. Le coach référent est
     * prévenu : une blessure déclarée que personne ne lit ne sert à rien, et c'est l'information
     * qui doit faire replanifier la semaine.
     */
    @Transactional
    public UnavailabilityResponse createForAthlete(UUID athleteId, UnavailabilityRequest req) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        AthleteUnavailability u = new AthleteUnavailability();
        u.setClub(athlete.getClub());
        u.setAthlete(athlete);
        apply(u, req);
        // Déclaration par l'athlète : on ne lui refuse pas de signaler son absence — la période est
        // de l'information de planification. Sans base légale, seul le caractère médical tombe,
        // exactement comme le fait le retrait de consentement sur l'historique existant.
        if (!consentValidator.isAllowed(athlete)) {
            u.setReason(com.coachrun.entity.enums.UnavailabilityReason.OTHER);
            u.setNotes(null);
        }
        AthleteUnavailability persisted = repository.save(u);
        UnavailabilityResponse saved = UnavailabilityResponse.from(persisted);
        notificationService.notifyAthleteUnavailability(athlete, u);
        proposeForCoveredSessions(athlete.getClub().getId(), persisted);
        return saved;
    }

    /** Suppression par l'athlète : garde-fou anti-IDOR sur la propriété de l'indisponibilité. */
    @Transactional
    public void deleteForAthlete(UUID athleteId, UUID id) {
        AthleteUnavailability u = repository.findById(id)
                .filter(x -> x.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Indisponibilité introuvable."));
        repository.delete(u);
    }

    @Transactional
    public UnavailabilityResponse update(UUID clubId, UUID id, UnavailabilityRequest req) {
        AthleteUnavailability u = require(clubId, id);
        // Requalifier une absence en blessure, c'est collecter une donnée de santé : même règle
        // qu'à la création, sinon la garde se contourne par une modification.
        if (isMedical(req)) {
            consentValidator.requireConsent(u.getAthlete(), "un motif d'indisponibilité médical");
        }
        apply(u, req);
        return UnavailabilityResponse.from(u);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        repository.delete(require(clubId, id));
    }

    private AthleteUnavailability require(UUID clubId, UUID id) {
        return repository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Indisponibilité introuvable."));
    }

    private void apply(AthleteUnavailability u, UnavailabilityRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La date de fin précède la date de début.");
        }
        u.setStartDate(req.startDate());
        u.setEndDate(req.endDate());
        u.setReason(req.reason());
        u.setNotes(req.notes());
    }
}
