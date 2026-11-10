package com.coachrun.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie qui porte le jeton de rafraîchissement.
 *
 * <p>Le jeton vivait dans le {@code localStorage}, lisible par n'importe quel script de la page :
 * une seule faille XSS livrait trente jours de session. Dans un cookie {@code HttpOnly}, aucun
 * script ne peut le lire ; {@code Secure} l'interdit en clair ; {@code SameSite=Strict} l'empêche
 * de partir d'un autre site ; et le chemin {@code /api/auth} le réserve aux seules routes qui en
 * ont l'usage (rafraîchissement et déconnexion), au lieu de l'envoyer avec chaque requête.</p>
 *
 * <p>Le front est servi sur le même site que l'API (réécriture {@code /api} de Vercel, proxy
 * en développement) : {@code Strict} ne gêne aucun usage légitime.</p>
 */
@Component
public class RefreshCookie {

    public static final String NAME = "darilab_refresh";

    private final boolean secure;
    private final String path;
    private final Duration maxAge;

    public RefreshCookie(@Value("${app.security.refresh-cookie.secure:true}") boolean secure,
                         @Value("${server.servlet.context-path:}") String contextPath,
                         RefreshTokenService refreshTokens) {
        this.secure = secure;
        this.path = (contextPath == null ? "" : contextPath) + "/auth";
        this.maxAge = refreshTokens.ttl();
    }

    public String set(String rawToken) {
        return build(rawToken, maxAge).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(path)
                .maxAge(age)
                .build();
    }
}
