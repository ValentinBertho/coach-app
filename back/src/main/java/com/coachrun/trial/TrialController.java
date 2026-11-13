package com.coachrun.trial;

import com.coachrun.dto.response.AuthResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * « Essayer sans inscription » : l'ouverture d'un espace d'essai jetable, et le passage du côté
 * coach au côté athlète à l'intérieur de cet essai. Cf. {@link TrialService}.
 */
@RestController
@RequiredArgsConstructor
public class TrialController {

    private final TrialService trialService;
    /** Absent quand la limitation de débit est coupée : on retombe alors sur l'adresse brute. */
    private final ObjectProvider<RateLimitFilter> rateLimitFilter;

    /** Le front n'affiche le bouton que si l'essai est ouvert. */
    @GetMapping("/public/trial")
    public Map<String, Object> status() {
        return Map.of("enabled", trialService.enabled());
    }

    @PostMapping("/public/trial")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse start(HttpServletRequest request) {
        RateLimitFilter filter = rateLimitFilter.getIfAvailable();
        return trialService.start(filter != null ? filter.clientIp(request) : request.getRemoteAddr());
    }

    @PostMapping("/trial/switch")
    public AuthResponse switchSide(@AuthenticationPrincipal AuthPrincipal principal) {
        return trialService.switchSide(principal.userId());
    }
}
