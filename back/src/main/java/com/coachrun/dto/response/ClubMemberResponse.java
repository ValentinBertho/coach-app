package com.coachrun.dto.response;

import com.coachrun.entity.ClubMember;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.UserStatus;

import java.util.UUID;

/** Coach membre d'un club avec son rôle. {@code pending} = invitation non encore acceptée. */
public record ClubMemberResponse(UUID coachId, String name, ClubRole clubRole, boolean pending) {

    public static ClubMemberResponse from(ClubMember m) {
        return new ClubMemberResponse(
                m.getCoach().getId(), m.getCoach().getFullName(), m.getClubRole(),
                m.getCoach().getStatus() == UserStatus.INVITED);
    }
}
