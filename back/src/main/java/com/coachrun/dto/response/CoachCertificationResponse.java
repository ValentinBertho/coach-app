package com.coachrun.dto.response;

import com.coachrun.entity.CoachCertification;

import java.util.UUID;

/** Un diplôme, tel qu'il s'affiche — déclaré par le coach, jamais certifié par la plateforme. */
public record CoachCertificationResponse(
        UUID id,
        String label,
        String organisation,
        Integer obtainedYear) {

    public static CoachCertificationResponse from(CoachCertification c) {
        return new CoachCertificationResponse(
                c.getId(), c.getLabel(), c.getOrganisation(), c.getObtainedYear());
    }
}
