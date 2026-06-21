package com.coachrun.service;

import com.coachrun.dto.request.AthleteRequest;
import com.coachrun.dto.response.AdminAthleteResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.InvitationAdminResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/** Administration des athlètes et invitations (PLATFORM_ADMIN, cross-club). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAthleteService {

    private final AthleteRepository athleteRepository;

    public PageResponse<AdminAthleteResponse> list(UUID clubId, AthleteStatus status, String q, Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        return PageResponse.from(athleteRepository.searchAdmin(clubId, status, query, pageable),
                AdminAthleteResponse::from);
    }

    public AthleteResponse get(UUID id) {
        return AthleteResponse.from(require(id));
    }

    @Transactional
    public AthleteResponse update(UUID id, AthleteRequest request) {
        Athlete a = require(id);
        a.setFirstName(request.firstName());
        a.setLastName(request.lastName());
        a.setEmail(StringUtils.hasText(request.email()) ? request.email().toLowerCase() : null);
        a.setBirthDate(request.birthDate());
        a.setSex(request.sex());
        a.setLevel(request.level());
        a.setHrMax(request.hrMax());
        a.setHrRest(request.hrRest());
        a.setVma(request.vma());
        a.setWeightKg(request.weightKg());
        a.setMedicalNotes(StringUtils.hasText(request.medicalNotes()) ? request.medicalNotes() : null);
        return AthleteResponse.from(a);
    }

    @Transactional
    public void delete(UUID id) {
        athleteRepository.delete(require(id));
    }

    public PageResponse<InvitationAdminResponse> pendingInvitations(Pageable pageable) {
        return PageResponse.from(athleteRepository.findByInviteTokenIsNotNull(pageable),
                InvitationAdminResponse::from);
    }

    @Transactional
    public void revokeInvitation(UUID athleteId) {
        Athlete a = require(athleteId);
        a.setInviteToken(null);
        a.setInviteExpiresAt(null);
    }

    private Athlete require(UUID id) {
        return athleteRepository.findById(id).orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
