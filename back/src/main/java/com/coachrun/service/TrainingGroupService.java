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

    public List<TrainingGroupResponse> list(UUID clubId) {
        return groupRepository.findByClubIdOrderByNameAsc(clubId).stream()
                .map(g -> TrainingGroupResponse.of(g, athleteRepository.countByGroupId(g.getId())))
                .toList();
    }

    /**
     * Semaine du groupe : une ligne par athlète accessible, séances course et force sur la
     * plage demandée. Un seul appel côté client là où le calendrier mono-athlète en aurait
     * fait deux par athlète.
     */
    public GroupCalendarResponse calendar(UUID clubId, UUID groupId, UUID coachId,
                                          LocalDate from, LocalDate to) {
        TrainingGroup group = require(clubId, groupId);
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
        require(clubId, groupId);
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
    public TrainingGroupResponse create(UUID clubId, TrainingGroupRequest request) {
        TrainingGroup g = new TrainingGroup();
        g.setClub(clubRepository.getReferenceById(clubId));
        g.setName(request.name());
        return TrainingGroupResponse.of(groupRepository.save(g), 0);
    }

    @Transactional
    public TrainingGroupResponse update(UUID clubId, UUID id, TrainingGroupRequest request) {
        TrainingGroup g = require(clubId, id);
        g.setName(request.name());
        return TrainingGroupResponse.of(g, athleteRepository.countByGroupId(id));
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        groupRepository.delete(require(clubId, id));
    }

    private TrainingGroup require(UUID clubId, UUID id) {
        return groupRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
    }
}
