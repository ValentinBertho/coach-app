package com.coachrun.service;

import com.coachrun.dto.request.MesocycleTemplateRequest;
import com.coachrun.dto.response.MesocycleTemplateResponse;
import com.coachrun.entity.MesocycleTemplate;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.MesocycleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Modèles de mésocycle réutilisables (CRUD scopé club). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MesocycleTemplateService {

    private final MesocycleTemplateRepository repository;
    private final ClubRepository clubRepository;

    public List<MesocycleTemplateResponse> list(UUID clubId) {
        return repository.findByClubIdOrderByNameAsc(clubId).stream()
                .map(MesocycleTemplateResponse::from).toList();
    }

    public MesocycleTemplateResponse get(UUID clubId, UUID id) {
        return MesocycleTemplateResponse.from(require(clubId, id));
    }

    @Transactional
    public MesocycleTemplateResponse create(UUID clubId, MesocycleTemplateRequest request) {
        MesocycleTemplate m = new MesocycleTemplate();
        m.setClub(clubRepository.getReferenceById(clubId));
        apply(m, request);
        return MesocycleTemplateResponse.from(repository.save(m));
    }

    @Transactional
    public MesocycleTemplateResponse update(UUID clubId, UUID id, MesocycleTemplateRequest request) {
        MesocycleTemplate m = require(clubId, id);
        apply(m, request);
        return MesocycleTemplateResponse.from(m);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        repository.delete(require(clubId, id));
    }

    /** Modèle scopé club (anti-IDOR) — utilisé aussi par la génération de mésocycle. */
    public MesocycleTemplate require(UUID clubId, UUID id) {
        return repository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Modèle de mésocycle introuvable."));
    }

    private void apply(MesocycleTemplate m, MesocycleTemplateRequest request) {
        m.setName(request.name());
        m.setDescription(request.description());
        m.setWeeks(request.weeks());
        m.setIncreasePct(request.increasePct());
        m.setDeloadEvery(request.deloadEvery());
        m.setDeloadPct(request.deloadPct());
    }
}
