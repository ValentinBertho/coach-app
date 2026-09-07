package com.coachrun.controller;

import com.coachrun.dto.request.LoginRequest;
import com.coachrun.dto.request.RefreshRequest;
import com.coachrun.dto.request.RegisterRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.dto.request.StreamTokenRequest;
import com.coachrun.dto.response.StreamTokenResponse;
import com.coachrun.dto.response.UserResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.security.JwtService;
import com.coachrun.security.StreamTokenService;
import com.coachrun.security.TokenBlacklist;
import com.coachrun.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification coach : inscription, connexion, rafraîchissement, profil courant.
 * Routes /auth/** publiques (cf. SecurityConfig) sauf /auth/me (token requis).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;
    private final StreamTokenService streamTokens;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.currentUser(principal.userId());
    }

    /** Renvoie l'e-mail de vérification au compte courant. */
    /** Édition du profil courant (nom, e-mail, préférence d'unité d'allure). */
    @org.springframework.web.bind.annotation.PatchMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody com.coachrun.dto.request.UpdateProfileRequest request) {
        return authService.updateProfile(principal.userId(), request);
    }

    /** Changement de mot de passe (l'actuel est exigé). */
    @PostMapping("/change-password")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody com.coachrun.dto.request.ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
    }

    /**
     * Jeton à usage unique pour la requête qui suit, quand elle ne peut pas porter d'en-tête.
     *
     * <p>Deux cas, et deux seulement : l'ouverture d'un flux {@code EventSource} et l'ouverture
     * d'une pièce jointe dans un onglet. Le jeton de session ne doit jamais y servir — placé dans
     * une URL, il se retrouve dans les journaux du relais, l'historique du navigateur et le
     * {@code Referer} de la page suivante, et il vaut une heure sur toute l'API.</p>
     *
     * <p>La demande, elle, porte l'en-tête : c'est une requête ordinaire, faite juste avant
     * l'usage. Elle est plafonnée avec les autres canaux de présence (cf. {@code RateLimitFilter}),
     * ce qui borne le nombre de jetons qu'un compte peut avoir en circulation.</p>
     */
    @PostMapping("/stream-token")
    public StreamTokenResponse streamToken(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody StreamTokenRequest request) {
        return new StreamTokenResponse(
                streamTokens.issue(principal, request.scope()), streamTokens.ttlSeconds());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.resendVerification(principal.userId());
    }

    /**
     * Déconnexion : périme durablement les jetons du compte.
     *
     * <p>La liste noire en mémoire reste utile — elle coupe l'access token courant immédiatement,
     * sans attendre une lecture en base — mais elle ne suffisait pas : elle ignorait le refresh
     * token (trente jours) et disparaissait au premier redéploiement. L'horodatage en base prend
     * le relais et vaut pour les deux types de jetons.</p>
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                tokenBlacklist.revoke(claims.getId(), claims.getExpiration().toInstant());
            } catch (RuntimeException ignored) {
                // token déjà invalide → rien à révoquer côté liste noire
            }
        }
        if (principal != null) {
            authService.logout(principal.userId());
        }
    }
}
