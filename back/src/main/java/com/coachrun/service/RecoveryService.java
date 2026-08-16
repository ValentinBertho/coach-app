package com.coachrun.service;

import com.coachrun.dto.response.ProposalResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.AthleteUnavailability;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ProposalType;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le rattrapage : ce qu'on fait d'une séance qui n'a pas eu lieu.
 *
 * <h2>Les deux impasses qu'il ouvre</h2>
 *
 * <p>L'athlète déclarait « pas fait — pas eu le temps », et rien ne se passait : la séance restait
 * manquée, la semaine trouée, personne ne proposait rien. Symétriquement, le coach saisissait une
 * indisponibilité de douze jours et les neuf séances qui tombaient dedans restaient au calendrier,
 * pour devenir autant de séances manquées et autant d'alertes.</p>
 *
 * <h2>Ce qu'il ne fait pas</h2>
 *
 * <p>Rien n'est déplacé ni supprimé ici. Ce service <b>fabrique des propositions</b>, que
 * {@link ProposalService} n'appliquera que si quelqu'un les accepte. Un calendrier qui se réorganise
 * tout seul pendant qu'on déclare une blessure serait la meilleure façon de faire perdre au coach le
 * fil de son propre plan.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecoveryService {

    private final WorkoutRepository workoutRepository;
    private final AthleteRepository athleteRepository;
    private final AthleteLoadService loadService;
    private final ProposalService proposalService;
    private final ClockService clock;

    /** Au-delà, replacer une séance n'a plus de sens : la semaine est passée. */
    private static final int MAX_SHIFT_DAYS = 6;

    /**
     * Une séance vient d'être déclarée non faite : propose une date de report, s'il en existe une
     * qui tienne debout.
     *
     * <p>Trois raisons de ne rien proposer, et il faut les respecter : la séance a été manquée pour
     * raison de santé (on ne rattrape pas une séance qu'on n'a pas pu faire parce qu'on avait mal),
     * il ne reste aucun jour libre dans la semaine, ou la charge ne le permet pas. Une proposition
     * qui ne tient pas debout coûte plus cher que pas de proposition.</p>
     *
     * @return la proposition créée, ou {@code null}
     */
    @Transactional
    public AthleteProposal proposeCatchUp(Workout missed) {
        if (missed.getType() == WorkoutType.REST) {
            return null;
        }
        if (missed.getMissedReason() == com.coachrun.entity.enums.MissedReason.HEALTH) {
            // Une séance manquée pour raison de santé ne se rattrape pas : elle se remplace par du
            // repos, et c'est au coach d'en décider — pas à un automatisme de la reproposer demain.
            return null;
        }
        Athlete athlete = missed.getAthlete();
        LocalDate today = clock.today();

        LocalDate slot = firstFreeSlot(athlete, missed.getScheduledDate(), today);
        if (slot == null) {
            return null;
        }
        if (!loadAllows(athlete)) {
            return null;
        }

        String reason = "Seul jour libre de ta semaine, et ta charge le permet.";
        AthleteProposal p = proposalService.propose(athlete, ProposalType.SESSION_MOVE,
                missed.getId(), slot,
                missed.getTitle() + " — décaler au " + dayLabel(slot),
                reason, Map.of("date", slot.toString()),
                "catchup:" + missed.getId(), true);
        if (p != null) {
            log.info("Rattrapage proposé pour la séance {} au {}", missed.getId(), slot);
        }
        return p;
    }

    /**
     * Une indisponibilité vient d'être déclarée : propose ce qu'on fait des séances qu'elle couvre.
     *
     * <p>Une seule proposition par séance, et le type dépend de la durée : sur une coupure courte,
     * on décale ce qui peut l'être ; sur une coupure longue, décaler n'a plus de sens — la reprise
     * ne se fait pas en avalant quinze jours de retard, et la bonne réponse est de retirer les
     * séances.</p>
     *
     * @return les propositions créées
     */
    @Transactional
    public List<ProposalResponse> proposeForUnavailability(UUID clubId, AthleteUnavailability window) {
        Athlete athlete = window.getAthlete();
        LocalDate today = clock.today();
        LocalDate from = window.getStartDate().isBefore(today) ? today : window.getStartDate();

        List<Workout> covered = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athlete.getId(), from, window.getEndDate())
                .stream()
                .filter(w -> w.getStatus() == WorkoutStatus.PLANNED)
                .filter(w -> w.getType() != WorkoutType.REST)
                .toList();
        if (covered.isEmpty()) {
            return List.of();
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(window.getStartDate(), window.getEndDate()) + 1;
        boolean longBreak = days > 7;
        LocalDate resume = window.getEndDate().plusDays(1);

        List<ProposalResponse> created = new ArrayList<>();
        for (Workout w : covered) {
            AthleteProposal p;
            if (longBreak) {
                p = proposalService.propose(athlete, ProposalType.SESSION_DROP, w.getId(),
                        w.getScheduledDate(),
                        w.getTitle() + " — retirer (" + dayLabel(w.getScheduledDate()) + ")",
                        "Tombe dans une indisponibilité de " + days + " jours.",
                        Map.of(), "unavail:drop:" + w.getId(), false);
            } else {
                LocalDate target = resume.plusDays(
                        java.time.temporal.ChronoUnit.DAYS.between(window.getStartDate(), w.getScheduledDate()));
                p = proposalService.propose(athlete, ProposalType.SESSION_MOVE, w.getId(),
                        w.getScheduledDate(),
                        w.getTitle() + " — décaler au " + dayLabel(target),
                        "Tombe dans une indisponibilité de " + days + " jours.",
                        Map.of("date", target.toString()), "unavail:move:" + w.getId(), false);
            }
            if (p != null) {
                created.add(ProposalResponse.from(p, null));
            }
        }
        log.info("Indisponibilité {} : {} proposition(s) pour l'athlète {}",
                window.getId(), created.size(), athlete.getId());
        return created;
    }

    /**
     * Les séances manquées d'un athlète sur les derniers jours, converties en propositions de
     * report. Sert à la file du coach : trois séances sautées appellent une décision, pas une
     * alerte de plus.
     */
    @Transactional
    public List<ProposalResponse> proposeCatchUpForAthlete(UUID clubId, UUID athleteId, int days) {
        if (athleteRepository.findByIdAndClubMembership(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        LocalDate today = clock.today();
        List<ProposalResponse> out = new ArrayList<>();
        for (Workout w : workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, today.minusDays(Math.max(1, days)), today.minusDays(1))) {
            if (w.getStatus() != WorkoutStatus.PLANNED && w.getStatus() != WorkoutStatus.MISSED) {
                continue;
            }
            AthleteProposal p = proposeCatchUp(w);
            if (p != null) {
                out.add(ProposalResponse.from(p, null));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------------
    // Détail
    // ---------------------------------------------------------------------

    /**
     * Premier jour libre de la semaine à venir : sans séance déjà prévue, et pas la veille d'une
     * séance dure — deux jours durs collés valent moins qu'une séance perdue.
     */
    private LocalDate firstFreeSlot(Athlete athlete, LocalDate missedDate, LocalDate today) {
        LocalDate from = today.isAfter(missedDate) ? today : missedDate;
        LocalDate endOfWeek = from.with(DayOfWeek.SUNDAY);
        LocalDate limit = from.plusDays(MAX_SHIFT_DAYS);
        LocalDate last = endOfWeek.isBefore(limit) ? endOfWeek : limit;

        Set<LocalDate> busy = new HashSet<>();
        for (Workout w : workoutRepository.findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                athlete.getId(), from, last.plusDays(1))) {
            if (w.getType() != WorkoutType.REST) {
                busy.add(w.getScheduledDate());
                // La veille d'une séance dure n'est pas un jour libre : y poser un rattrapage
                // reviendrait à créer l'enchaînement qu'on cherche à éviter.
                busy.add(w.getScheduledDate().minusDays(1));
            }
        }
        for (LocalDate d = from.plusDays(1); !d.isAfter(last); d = d.plusDays(1)) {
            if (!busy.contains(d)) {
                return d;
            }
        }
        return null;
    }

    /**
     * La charge autorise-t-elle une séance de plus cette semaine&nbsp;? Au-dessus de 1,4, rattraper
     * une séance manquée revient à ajouter du risque à un athlète qui en accumule déjà.
     */
    private boolean loadAllows(Athlete athlete) {
        try {
            var load = loadService.load(athlete.getClub().getId(), athlete.getId());
            return load.ratio() == null || load.ratio() <= 1.4;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private String dayLabel(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
                + " " + d.getDayOfMonth();
    }
}
