package com.coachrun.dto.response;

/** Infos publiques d'une invitation coach (page d'acceptation). */
public record CoachInvitationInfoResponse(String email, String fullName, String clubName) {
}
