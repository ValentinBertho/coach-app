package com.coachrun.dto.response;

import com.coachrun.entity.AthleteUnavailability;
import com.coachrun.entity.enums.UnavailabilityReason;

import java.time.LocalDate;
import java.util.UUID;

/** Indisponibilité athlète. */
public record UnavailabilityResponse(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        UnavailabilityReason reason,
        String notes
) {

    public static UnavailabilityResponse from(AthleteUnavailability u) {
        return new UnavailabilityResponse(
                u.getId(), u.getStartDate(), u.getEndDate(), u.getReason(), u.getNotes());
    }
}
