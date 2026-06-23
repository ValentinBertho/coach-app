package com.coachrun.dto.response;

import com.coachrun.entity.ClubMember;
import com.coachrun.entity.enums.ClubRole;

import java.util.UUID;

/** Coach membre d'un club avec son rôle. */
public record ClubMemberResponse(UUID coachId, String name, ClubRole clubRole) {

    public static ClubMemberResponse from(ClubMember m) {
        return new ClubMemberResponse(m.getCoach().getId(), m.getCoach().getFullName(), m.getClubRole());
    }
}
