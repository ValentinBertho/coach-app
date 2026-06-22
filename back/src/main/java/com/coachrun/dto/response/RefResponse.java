package com.coachrun.dto.response;

import java.util.UUID;

/** Référence légère (id + libellé) pour exposer une relation sans charger l'entité complète. */
public record RefResponse(UUID id, String name) {
}
