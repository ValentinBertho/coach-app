package com.coachrun.dto.response;

import com.coachrun.entity.MesocycleTemplate;

import java.util.UUID;

public record MesocycleTemplateResponse(
        UUID id, String name, String description,
        int weeks, double increasePct, int deloadEvery, double deloadPct) {

    public static MesocycleTemplateResponse from(MesocycleTemplate m) {
        return new MesocycleTemplateResponse(m.getId(), m.getName(), m.getDescription(),
                m.getWeeks(), m.getIncreasePct(), m.getDeloadEvery(), m.getDeloadPct());
    }
}
