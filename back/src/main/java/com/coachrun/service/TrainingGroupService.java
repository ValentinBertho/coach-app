package com.coachrun.service;

import com.coachrun.dto.request.TrainingGroupRequest;
import com.coachrun.dto.response.TrainingGroupResponse;
import com.coachrun.entity.TrainingGroup;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.TrainingGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Groupes d'entraînement (CRUD scopé club). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingGroupService {

    private final TrainingGroupRepository groupRepository;
    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;

    public List<TrainingGroupResponse> list(UUID clubId) {
        return groupRepository.findByClubIdOrderByNameAsc(clubId).stream()
                .map(g -> TrainingGroupResponse.of(g, athleteRepository.countByGroupId(g.getId())))
                .toList();
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
