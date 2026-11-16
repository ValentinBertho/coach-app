package com.coachrun.service;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.CoachingRequest;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.CoachingRequestStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.CoachingRequestRepository;
import com.coachrun.repository.InviteLinkRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un coach quitte un club — de lui-même, ou retiré par le propriétaire.
 *
 * <p>Avant, seule son adhésion tombait : ses relations référentes restaient actives, et il gardait
 * l'écriture sur les athlètes du club ; son jeton portait encore le club. Désormais chaque athlète
 * a une suite explicite :</p>
 * <ul>
 *   <li><b>athlète du club</b> : il reste au club, un autre coach du club (choisi, ou le
 *       propriétaire) devient son référent ;</li>
 *   <li><b>athlète privé avec un compte</b> : il a choisi ce coach, pas le club — il le suit dans
 *       son espace (celui qu'il a déjà, ou un espace indépendant ouvert pour l'occasion), avec sa
 *       montre et ses sorties ;</li>
 *   <li><b>athlète privé sans compte</b> (fiche tenue par le coach) : rien ne peut le « suivre »,
 *       sa fiche reste au club et passe au successeur, comme un athlète du club.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClubDepartureService {

    private static final String REASON = "Départ du coach du club";

    private final ClubMemberRepository memberRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final UserRepository userRepository;
    private final AthleteAccountRepository accountRepository;
    private final CoachingRequestRepository requestRepository;
    private final InviteLinkRepository inviteLinkRepository;
    private final ClubProvisioningService provisioning;
    private final CoachingRelationService relations;
    private final CoachingRequestService requests;
    private final NotificationService notifications;
    private final AdminAuditService audit;

    public record Person(UUID athleteId, String name) {
    }

    public record Successor(UUID coachId, String name, boolean owner) {
    }

    /** Ce que le départ va produire, pour que le propriétaire (ou le coach) décide en connaissance. */
    public record Impact(String coachName, List<Person> handedOver, List<Person> followers,
                         List<Successor> successors, boolean opensSoloSpace) {
    }

    @Transactional(readOnly = true)
    public Impact preview(UUID clubId, UUID coachId) {
        ClubMember member = requireMember(clubId, coachId);
        Split split = split(clubId, coachId);
        List<Successor> successors = memberRepository.findByClubIdAndActiveTrue(clubId).stream()
                .filter(m -> !m.getCoach().getId().equals(coachId))
                .map(m -> new Successor(m.getCoach().getId(), m.getCoach().getFullName(),
                        m.getClubRole() == ClubRole.OWNER))
                .toList();
        return new Impact(member.getCoach().getFullName(),
                split.handedOver.stream().map(r -> person(r.getAthlete())).toList(),
                split.followers.stream().map(r -> person(r.getAthlete())).toList(),
                successors, opensSoloSpace(member.getCoach(), clubId));
    }

    /**
     * Le départ lui-même.
     *
     * @param successorId coach du club qui reprend les athlètes restant au club ; absent = le
     *                    propriétaire
     */
    @Transactional
    public Impact depart(UUID clubId, UUID coachId, UUID successorId, UUID byUserId) {
        Impact impact = preview(clubId, coachId);
        ClubMember member = requireMember(clubId, coachId);
        if (member.getClubRole() == ClubRole.OWNER) {
            throw new ConflictException("Le propriétaire du club ne peut pas le quitter : "
                    + "confiez d'abord le club à un autre coach.");
        }
        User coach = member.getCoach();
        Club club = member.getClub();
        User successor = successor(clubId, coachId, successorId);
        Split split = split(clubId, coachId);

        // 1. Les athlètes qui restent au club changent de référent.
        for (CoachAthleteRelation relation : split.handedOver) {
            handOver(relation, successor, club, byUserId);
        }
        notifications.notifyAthletesHandedOver(successor, coach.getFullName(), split.handedOver.size());

        // 2. Le coach garde un espace : le sien, ou un espace indépendant ouvert maintenant.
        boolean newSpace = opensSoloSpace(coach, clubId);
        if (newSpace) {
            provisioning.openSoloSpaceFor(coach);
        }

        // 3. Ses athlètes privés le suivent dans cet espace.
        for (CoachAthleteRelation relation : split.followers) {
            follow(relation, coach, club, byUserId);
        }

        // 4. Plus d'accès au club : adhésion close, liens d'invitation du club coupés, sessions fermées.
        member.setActive(false);
        coach.getAdditionalClubs().removeIf(c -> c.getId().equals(clubId));
        inviteLinkRepository.findByCoachIdOrderByCreatedAtDesc(coachId).stream()
                .filter(l -> l.getClub() != null && l.getClub().getId().equals(clubId) && l.getRevokedAt() == null)
                .forEach(l -> l.setRevokedAt(Instant.now()));
        coach.setSessionsInvalidatedAt(Instant.now());
        notifications.notifyClubLeft(coach, club.getName(), split.followers.size(), newSpace);
        audit.record(AdminAuditAction.COACH_REMOVED, AdminAuditTarget.USER, coach.getId(), coach.getEmail(),
                "Club « " + club.getName() + " », rôle " + member.getClubRole() + " — "
                        + split.handedOver.size() + " athlète(s) confiés à " + successor.getFullName() + ", "
                        + split.followers.size() + " athlète(s) privé(s) suivent le coach");
        log.info("Départ du coach {} du club {} : {} confiés, {} suivent", coachId, clubId,
                split.handedOver.size(), split.followers.size());
        return impact;
    }

    private void handOver(CoachAthleteRelation relation, User successor, Club club, UUID byUserId) {
        Athlete athlete = relation.getAthlete();
        String former = relation.getCoach().getFullName();
        relation.end(byUserId, REASON);
        relation.setPlannedEndAt(null);
        relationRepository.saveAndFlush(relation);

        CoachAthleteRelation next = relationRepository
                .findFirstByCoachIdAndAthleteId(successor.getId(), athlete.getId())
                .orElseGet(CoachAthleteRelation::new);
        next.reopen();
        next.setAthlete(athlete);
        next.setCoach(successor);
        next.setClub(club);
        next.setReferent(true);
        relationRepository.save(next);
        userRepository.findByAthleteId(athlete.getId()).ifPresent(u ->
                notifications.notifyReferentChanged(u, former, successor.getFullName(), club.getName()));
    }

    private void follow(CoachAthleteRelation relation, User coach, Club club, UUID byUserId) {
        User athleteUser = userRepository.findByAthleteId(relation.getAthlete().getId()).orElseThrow();
        AthleteAccount account = accountRepository.findByUserId(athleteUser.getId()).orElse(null);
        relations.closeForMove(relation, athleteUser, byUserId, REASON);
        if (account == null) {
            account = accountRepository.findByUserId(athleteUser.getId()).orElseThrow();
        }
        CoachingRequest request = new CoachingRequest();
        request.setAthleteAccount(account);
        request.setCoach(coach);
        request.setStatus(CoachingRequestStatus.PENDING);
        request.setMessage("Suivi poursuivi après le départ de " + club.getName() + ".");
        request.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        // Ses sorties étaient déjà sous les yeux de ce coach : elles le suivent avec lui.
        request.setShareHistory(true);
        request = requestRepository.save(request);
        requests.startCoaching(request);
        notifications.notifyFollowedCoach(athleteUser, coach.getFullName(), club.getName());
    }

    private record Split(List<CoachAthleteRelation> handedOver, List<CoachAthleteRelation> followers) {
    }

    private Split split(UUID clubId, UUID coachId) {
        List<CoachAthleteRelation> handedOver = new ArrayList<>();
        List<CoachAthleteRelation> followers = new ArrayList<>();
        for (CoachAthleteRelation r : relationRepository.findByCoachIdAndActiveTrue(coachId)) {
            if (!r.isReferent() || r.getAthlete().getClub() == null
                    || !r.getAthlete().getClub().getId().equals(clubId)) {
                continue;
            }
            boolean hasAccount = userRepository.findByAthleteId(r.getAthlete().getId()).isPresent();
            if (r.getClub() == null && hasAccount) {
                followers.add(r);
            } else {
                handedOver.add(r);
            }
        }
        return new Split(handedOver, followers);
    }

    /**
     * Le coach n'a pas d'autre espace que ce club : il lui en faut un. Pas pour une invitation
     * jamais acceptée : on n'ouvre pas d'espace à quelqu'un qui n'est jamais venu.
     */
    private boolean opensSoloSpace(User coach, UUID clubId) {
        if (coach.getStatus() == com.coachrun.entity.enums.UserStatus.INVITED) {
            return false;
        }
        return coach.getClub() == null || coach.getClub().getId().equals(clubId);
    }

    private User successor(UUID clubId, UUID leavingId, UUID successorId) {
        if (successorId != null) {
            if (successorId.equals(leavingId)) {
                throw new ConflictException("Choisissez un autre coach que celui qui part.");
            }
            return memberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, successorId)
                    .map(ClubMember::getCoach)
                    .orElseThrow(() -> new NotFoundException("Ce coach ne fait pas partie du club."));
        }
        return memberRepository.findByClubIdAndActiveTrue(clubId).stream()
                .filter(m -> m.getClubRole() == ClubRole.OWNER)
                .map(ClubMember::getCoach)
                .findFirst()
                .orElseThrow(() -> new ConflictException("Le club n'a pas de propriétaire."));
    }

    private ClubMember requireMember(UUID clubId, UUID coachId) {
        return memberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, coachId)
                .orElseThrow(() -> new NotFoundException("Membre introuvable."));
    }

    private static Person person(Athlete a) {
        return new Person(a.getId(), (a.getFirstName() + " " + a.getLastName()).trim());
    }
}
