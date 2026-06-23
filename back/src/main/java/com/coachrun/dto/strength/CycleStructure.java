package com.coachrun.dto.strength;

import java.util.List;
import java.util.UUID;

/** Structure d'un cycle de force : semaines → séances + ajustement de charge (%). */
public record CycleStructure(List<CycleWeek> weeks) {

    public CycleStructure {
        weeks = weeks == null ? List.of() : weeks;
    }

    public record CycleWeek(int week, List<UUID> sessionIds, double chargePctAdjustment) {
        public CycleWeek {
            sessionIds = sessionIds == null ? List.of() : sessionIds;
        }
    }
}
