package com.coachrun.dto.response;

import com.coachrun.entity.Club;

import java.math.BigDecimal;

/**
 * Bornes des domaines d'intensité par défaut du club, appliquées aux NOUVEAUX athlètes.
 * Les athlètes existants gardent les leurs — un défaut ne réécrit jamais rétroactivement.
 */
public record ClubDefaultsResponse(
        BigDecimal vcDomain1Pct,
        BigDecimal vcDomain2Pct,
        BigDecimal fcDomain1Pct,
        BigDecimal fcDomain2Pct) {

    public static ClubDefaultsResponse from(Club c) {
        return new ClubDefaultsResponse(
                c.getDefaultVcDomain1Pct(), c.getDefaultVcDomain2Pct(),
                c.getDefaultFcDomain1Pct(), c.getDefaultFcDomain2Pct());
    }
}
