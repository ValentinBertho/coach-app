package com.coachrun.service;

import com.coachrun.dto.request.GroupScheduleRequest;
import com.coachrun.dto.request.TrainingGroupRequest;
import com.coachrun.dto.response.GroupApplyResponse;
import com.coachrun.dto.response.GroupCalendarResponse;
import com.coachrun.dto.response.ScheduledStrengthResponse;
import com.coachrun.dto.response.TrainingGroupResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.TrainingGroup;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.security.AthleteAccessValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Groupes d'entraînement (CRUD scopé club). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingGroupService {

    private final TrainingGroupRepository groupRepository;
    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final ScheduledStrengthSessionRepository strengthRepository;
    private final AthleteAccessValidator accessValidator;
    private final CourseSessionService courseSessionService;
    private final StrengthScheduleService strengthScheduleService;

    /**
     * Les groupes que ce coach voit.
     *
     * <p>Un groupe privé n'apparaît qu'à son créateur et aux coachs qu'il y a conviés. La
     * visibilité des <b>athlètes</b>, elle, ne change pas : elle reste portée par la relation
     * référente et les permissions — un athlète peut d'ailleurs appartenir à plusieurs groupes,
     * et deux mécanismes superposés deviendraient inexplicables.</p>
     */
    public List<TrainingGroupResponse> list(UUID clubId, UUID coachId) {
        return groupRepository.findByClubIdOrderByNameAsc(clubId).stream()
                .filter(g -> g.isVisibleTo(coachId))
                .map(g -> TrainingGroupResponse.of(g, athleteRepository.countByGroupId(g.getId()), coachId))
                .toList();
    }

    /**
     * Semaine du groupe : une ligne par athlète accessible, séances course et force sur la
     * plage demandée. Un seul appel côté client là où le calendrier mono-athlète en aurait
     * fait deux par athlète.
     */
    public GroupCalendarResponse calendar(UUID clubId, UUID groupId, UUID coachId,
                                          LocalDate from, LocalDate to) {
        TrainingGroup group = requireVisible(clubId, groupId, coachId);
        List<GroupCalendarResponse.AthleteRow> rows = new ArrayList<>();

        for (Athlete a : athleteRepository.findActiveByGroup(groupId, clubId, AthleteStatus.ACTIVE)) {
            // Un coach ne voit jamais hors de son périmètre : l'athlète non lisible est absent
            // de la réponse, il n'est pas simplement grisé côté client.
            Optional<PermissionLevel> level = accessValidator.effectiveLevel(coachId, a.getId());
            if (level.isEmpty()) {
                continue;
            }
            rows.add(new GroupCalendarResponse.AthleteRow(
                    a.getId(), a.getFirstName(), a.getLastName(),
                    level.get().atLeast(PermissionLevel.WRITE),
                    workoutRepository
                            .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAscOrderIndexAsc(
                                    clubId, a.getId(), from, to)
                            .stream().map(WorkoutResponse::from).toList(),
                    strengthRepository
                            .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                                    clubId, a.getId(), from, to)
                            .stream().map(ScheduledStrengthResponse::from).toList()));
        }
        return new GroupCalendarResponse(group.getId(), group.getName(), rows);
    }

    /**
     * Planifie une même séance — course ou prépa physique — pour <b>tous</b> les athlètes actifs
     * du groupe, à une date.
     *
     * <p>C'est le geste de base d'un entraînement collectif, et il n'existait pas : la vue de
     * groupe savait montrer la semaine de quinze athlètes, mais planifier revenait à répéter le
     * même glisser-déposer quinze fois, ligne par ligne. Le mésocycle de groupe, lui, part d'une
     * semaine source déjà écrite chez chacun — il ne répond pas au « tout le monde fait du seuil
     * jeudi ».</p>
     *
     * <p>L'accès en écriture est vérifié athlète par athlète, comme pour le mésocycle de groupe :
     * un athlète hors du périmètre du coach est <b>ignoré</b> et compté dans {@code skipped},
     * jamais planifié en douce. La séance est calculée pour chacun : deux athlètes du même groupe
     * n'ont pas les mêmes allures, et c'est tout l'intérêt de prescrire une séance plutôt qu'un
     * chrono.</p>
     */
    @Transactional
    public GroupApplyResponse schedule(UUID clubId, UUID groupId, UUID coachId,
                                       GroupScheduleRequest request) {
        if (!request.isValid()) {
            throw new com.coachrun.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Précise une séance course ou une séance de prépa physique, pas les deux.");
        }
        requireVisible(clubId, groupId, coachId);
        int applied = 0;
        int skipped = 0;
        int created = 0;
        for (Athlete a : athleteRepository.findActiveByGroup(groupId, clubId, AthleteStatus.ACTIVE)) {
            boolean canWrite = accessValidator.effectiveLevel(coachId, a.getId())
                    .map(l -> l.atLeast(PermissionLevel.WRITE)).orElse(false);
            if (!canWrite) {
                skipped++;
                continue;
            }
            if (request.templateId() != null) {
                courseSessionService.scheduleForAthlete(clubId, a.getId(), request.templateId(), request.date());
            } else {
                strengthScheduleService.schedule(clubId, a.getId(), request.strengthSessionId(),
                        request.date(), com.coachrun.entity.enums.FieldsPreset.AVANCE);
            }
            applied++;
            created++;
        }
        return new GroupApplyResponse(applied, skipped, created);
    }

    @Transactional
    public TrainingGroupResponse create(UUID clubId, UUID coachId, TrainingGroupRequest request) {
        TrainingGroup g = new TrainingGroup();
        g.setClub(clubRepository.getReferenceById(clubId));
        g.setName(request.name());
        g.setOwnerCoachId(coachId);
        g.setVisibility(request.visibilityOrDefault());
        g.getInvitedCoachIds().addAll(invited(request));
        return TrainingGroupResponse.of(groupRepository.save(g), 0, coachId);
    }

    @Transactional
    public TrainingGroupResponse update(UUID clubId, UUID id, UUID coachId, TrainingGroupRequest request) {
        TrainingGroup g = requireVisible(clubId, id, coachId);
        // Refermer un groupe ouvert, ou l'ouvrir, revient à décider qui le voit : cela appartient
        // à son créateur. Un groupe hérité, sans créateur connu, reste modifiable par le club.
        if (g.getOwnerCoachId() != null && !g.getOwnerCoachId().equals(coachId)) {
            throw new com.coachrun.exception.ConflictException(
                    "Seul le créateur du groupe peut en modifier la visibilité.");
        }
        g.setName(request.name());
        g.setVisibility(request.visibilityOrDefault());
        g.getInvitedCoachIds().clear();
        g.getInvitedCoachIds().addAll(invited(request));
        return TrainingGroupResponse.of(g, athleteRepository.countByGroupId(id), coachId);
    }

    private static java.util.Set<UUID> invited(TrainingGroupRequest request) {
        return request.invitedCoachIds() == null
                ? java.util.Set.of()
                : new java.util.LinkedHashSet<>(request.invitedCoachIds());
    }

    @Transactional
    public void delete(UUID clubId, UUID id, UUID coachId) {
        groupRepository.delete(requireVisible(clubId, id, coachId));
    }

    private TrainingGroup require(UUID clubId, UUID id) {
        return groupRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
    }

    /**
     * Le groupe, s'il est visible de ce coach. Sinon il n'existe pas pour lui — « introuvable »
     * plutôt qu'« interdit », un 403 confirmerait l'existence d'un groupe privé.
     */
    public TrainingGroup requireVisible(UUID clubId, UUID id, UUID coachId) {
        TrainingGroup g = require(clubId, id);
        if (!g.isVisibleTo(coachId)) {
            throw new NotFoundException("Groupe introuvable.");
        }
        return g;
    }
}
