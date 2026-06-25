package com.coachrun.service;

import com.coachrun.dto.request.RunDrillRequest;
import com.coachrun.dto.response.RunDrillResponse;
import com.coachrun.entity.RunDrill;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.RunDrillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Éducatifs de course (gammes techniques) — CRUD scopé club. CDC §9. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunDrillService {

    private final RunDrillRepository drillRepository;
    private final ClubRepository clubRepository;

    public List<RunDrillResponse> list(UUID clubId) {
        return drillRepository.findByClubIdOrderByCategoryAscNameAsc(clubId).stream()
                .map(RunDrillResponse::of)
                .toList();
    }

    @Transactional
    public RunDrillResponse create(UUID clubId, RunDrillRequest request) {
        RunDrill d = new RunDrill();
        d.setClub(clubRepository.getReferenceById(clubId));
        apply(d, request);
        return RunDrillResponse.of(drillRepository.save(d));
    }

    @Transactional
    public RunDrillResponse update(UUID clubId, UUID id, RunDrillRequest request) {
        RunDrill d = require(clubId, id);
        apply(d, request);
        return RunDrillResponse.of(d);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        drillRepository.delete(require(clubId, id));
    }

    private void apply(RunDrill d, RunDrillRequest r) {
        d.setName(r.name());
        d.setCategory(r.category());
        d.setDescription(r.description());
        d.setVideoUrl(r.videoUrl());
    }

    private RunDrill require(UUID clubId, UUID id) {
        return drillRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Éducatif introuvable."));
    }
}
