package com.coachrun.controller;

import com.coachrun.dto.response.StravaStatusResponse;
import com.coachrun.service.StravaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Connexion Strava (OAuth) et import d'activités d'un athlète. Scoping tenant. */
@Tag(name = "Sync — Strava")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/strava")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class StravaController {

    private final StravaService stravaService;

    @GetMapping
    public StravaStatusResponse status(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return stravaService.status(clubId, athleteId);
    }

    /** URL d'autorisation Strava à ouvrir dans le navigateur. */
    @GetMapping("/authorize")
    public Map<String, String> authorize(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return Map.of("url", stravaService.authorizeUrl(clubId, athleteId));
    }

    /** Finalise la connexion avec le code d'autorisation renvoyé par Strava. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/connect")
    public StravaStatusResponse connect(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @RequestBody Map<String, String> body) {
        return stravaService.connect(clubId, athleteId, body.get("code"));
    }

    /** Importe les nouvelles activités Strava. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/import")
    public Map<String, Integer> importActivities(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return Map.of("imported", stravaService.importActivities(clubId, athleteId));
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        stravaService.disconnect(clubId, athleteId);
    }
}
