package com.coachrun.dto.response;

import java.util.List;

/**
 * Tableau de bord coach « état de forme » (cf. DARI Lab — dashboard) : compteurs et athlètes
 * répartis Route / Trail, chacun avec sa pastille de forme.
 */
public record CoachFormDashboardResponse(
        int total,
        int route,
        int trail,
        List<AthleteFormResponse> routeAthletes,
        List<AthleteFormResponse> trailAthletes
) {
}
