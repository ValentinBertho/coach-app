package com.coachrun.controller;

import com.coachrun.dto.request.ZoneSetRequest;
import com.coachrun.dto.response.ZoneSetResponse;
import com.coachrun.service.ZoneSetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Modèles de zones du club : plusieurs échelles, appliquées athlète par athlète. */
@Tag(name = "Modèles de zones")
@RestController
@RequestMapping("/clubs/{clubId}")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class ZoneSetController {

    private final ZoneSetService zoneSetService;
    private final com.coachrun.service.ZoneValueSyncService zoneValueSyncService;

    @GetMapping("/zone-sets")
    public List<ZoneSetResponse> list(@PathVariable UUID clubId) {
        return zoneSetService.list(clubId);
    }

    @PostMapping("/zone-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneSetResponse create(@PathVariable UUID clubId, @Valid @RequestBody ZoneSetRequest request) {
        return zoneSetService.create(clubId, request);
    }

    @PutMapping("/zone-sets/{id}")
    public ZoneSetResponse rename(@PathVariable UUID clubId, @PathVariable UUID id,
                                  @Valid @RequestBody ZoneSetRequest request) {
        return zoneSetService.rename(clubId, id, request);
    }

    @DeleteMapping("/zone-sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        zoneSetService.delete(clubId, id);
    }

    /** Modèle de zones appliqué à un athlète ({@code zoneSetId} null = jeu par défaut du club). */
    @GetMapping("/athletes/{athleteId}/zone-set")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
    public Map<String, UUID> current(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return java.util.Collections.singletonMap("zoneSetId", zoneSetService.zoneSetIdOf(clubId, athleteId));
    }

    /** Applique un modèle de zones à l'athlète, purge les cibles périmées et resynchronise. */
    @PutMapping("/athletes/{athleteId}/zone-set")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public void apply(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                      @RequestBody Map<String, String> body) {
        String raw = body.get("zoneSetId");
        UUID zoneSetId = raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        zoneSetService.applyToAthlete(clubId, athleteId, zoneSetId);
        // Le rattachement écrit, on repeuple les cibles depuis les règles de la nouvelle échelle :
        // sans ce resync, l'athlète se retrouverait avec des zones vides.
        zoneValueSyncService.resync(clubId, athleteId);
    }
}
