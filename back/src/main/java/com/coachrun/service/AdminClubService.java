package com.coachrun.service;

import com.coachrun.dto.request.ClubRequest;
import com.coachrun.dto.response.AdminClubDetailResponse;
import com.coachrun.dto.response.ClubResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Administration des clubs (PLATFORM_ADMIN). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminClubService {

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;
    private final DeviceConnectionRepository deviceConnectionRepository;
    private final AdminAuditService audit;
    private final ClockService clock;

    public PageResponse<ClubResponse> list(String q, ClubStatus status, Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : "";
        return PageResponse.from(clubRepository.searchAdmin(status, query, pageable),
                ClubResponse::from);
    }

    public ClubResponse get(UUID id) {
        return ClubResponse.from(require(id));
    }

    /**
     * Fiche club : composition, activité, et ce que sa suppression détruirait.
     *
     * <p>Les compteurs servent d'abord à répondre « que se passe-t-il ici ? » — un club à
     * douze athlètes dont la dernière sortie remonte à trois mois n'appelle pas la même réaction
     * qu'un club vide créé hier. Ils servent ensuite d'aperçu d'impact avant suppression : la
     * modale disait « et toutes ses données » sans jamais dire combien.</p>
     */
    public AdminClubDetailResponse detail(UUID id) {
        Club club = require(id);
        LocalDate today = clock.today();
        List<AdminClubDetailResponse.Member> members = userRepository.findAllCoachesOfClub(id)
                .stream()
                .map(u -> toMember(u, id))
                .toList();

        return new AdminClubDetailResponse(
                club.getId(), club.getName(), club.getSlug(), club.getStatus(), club.getCreatedAt(),
                members.size(),
                athleteRepository.countByClubId(id),
                athleteRepository.countByClubIdAndStatus(id, AthleteStatus.ACTIVE),
                athleteRepository.countByClubIdAndStatus(id, AthleteStatus.PAUSED),
                athleteRepository.countByClubIdAndStatus(id, AthleteStatus.ARCHIVED),
                athleteRepository.countByClubIdAndInviteTokenIsNotNull(id),
                workoutRepository.countByClubId(id),
                activityRepository.countByClubId(id),
                activityRepository.countByClubIdAndActivityDateAfter(id, today.minusDays(30)),
                deviceConnectionRepository.countByClub(id),
                activityRepository.findLastActivityDate(id).orElse(null),
                members);
    }

    @Transactional
    public ClubResponse create(ClubRequest request) {
        Club club = new Club();
        club.setName(request.name());
        club.setSlug(uniqueSlug(request.name()));
        club.setStatus(request.status() != null ? request.status() : ClubStatus.ACTIVE);
        Club saved = clubRepository.save(club);
        audit.record(AdminAuditAction.CLUB_CREATED, AdminAuditTarget.CLUB,
                saved.getId(), saved.getName(), "Slug " + saved.getSlug());
        return ClubResponse.from(saved);
    }

    @Transactional
    public ClubResponse update(UUID id, ClubRequest request) {
        Club club = require(id);
        StringBuilder changes = new StringBuilder();
        if (!club.getName().equals(request.name())) {
            changes.append("nom « ").append(club.getName()).append(" » → « ")
                    .append(request.name()).append(" »");
            club.setName(request.name());
        }
        if (request.status() != null && request.status() != club.getStatus()) {
            // Consigné à part : suspendre un club ferme l'accès de tout un tenant, c'est le geste
            // qu'on veut retrouver sans le chercher au milieu des renommages.
            audit.record(AdminAuditAction.CLUB_STATUS_CHANGED, AdminAuditTarget.CLUB,
                    club.getId(), club.getName(),
                    "Statut " + club.getStatus() + " → " + request.status());
            club.setStatus(request.status());
        }
        if (!changes.isEmpty()) {
            audit.record(AdminAuditAction.CLUB_UPDATED, AdminAuditTarget.CLUB,
                    club.getId(), club.getName(), changes.toString());
        }
        return ClubResponse.from(club);
    }

    @Transactional
    public void delete(UUID id) {
        Club club = require(id);
        long athletes = athleteRepository.countByClubId(id);
        long coaches = userRepository.findAllCoachesOfClub(id).size();
        // Consigné AVANT : après la cascade, plus rien ne dit ce que le club contenait.
        audit.record(AdminAuditAction.CLUB_DELETED, AdminAuditTarget.CLUB,
                club.getId(), club.getName(),
                coaches + " coach(s) et " + athletes + " athlète(s) supprimés en cascade.");
        clubRepository.delete(club);
        log.warn("Club supprimé par l'administration (club={}, athlètes={}, coachs={})",
                id, athletes, coaches);
    }

    private AdminClubDetailResponse.Member toMember(User u, UUID clubId) {
        boolean primary = u.getClub() != null && clubId.equals(u.getClub().getId());
        return new AdminClubDetailResponse.Member(
                u.getId(), u.getFullName(), u.getEmail(),
                u.getRole().name(), roleLabel(u.getRole().name()),
                u.getStatus().name(), primary, u.getLastSeenAt());
    }

    /** Libellés français, alignés sur ceux du front (`ROLE_LABELS`). */
    private static String roleLabel(String role) {
        return switch (role) {
            case "PLATFORM_ADMIN" -> "Admin plateforme";
            case "HEAD_COACH" -> "Responsable club";
            case "COACH" -> "Coach";
            case "ATHLETE" -> "Athlète";
            default -> role;
        };
    }

    private Club require(UUID id) {
        return clubRepository.findById(id).orElseThrow(() -> new NotFoundException("Club introuvable."));
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.slugify(name);
        String slug = base;
        int i = 1;
        while (clubRepository.existsBySlug(slug)) {
            slug = base + "-" + (++i);
        }
        return slug;
    }
}
