package com.coachrun.service;

import com.coachrun.dto.request.LoginRequest;
import com.coachrun.dto.request.RefreshRequest;
import com.coachrun.dto.request.RegisterRequest;
import com.coachrun.dto.response.AuthResponse;
import com.coachrun.dto.response.UserResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.exception.UnauthorizedException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
import com.coachrun.util.SlugUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authentification : inscription d'un coach (compte HEAD_COACH + club implicite),
 * connexion et rafraîchissement de jeton. Stateless (JWT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;
    private final com.coachrun.repository.ClubMemberRepository clubMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.coachrun.security.TokenBlacklist tokenBlacklist;
    private final NotificationService notificationService;

    private static final java.security.SecureRandom RESET_RANDOM = new java.security.SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Un compte existe déjà avec cet email.");
        }

        Club club = new Club();
        club.setName(request.clubName());
        club.setSlug(uniqueSlug(request.clubName()));
        club.setStatus(ClubStatus.ACTIVE);
        club = clubRepository.save(club);

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(UserRole.HEAD_COACH);
        user.setStatus(UserStatus.ACTIVE);
        user.setClub(club);
        // E-mail à vérifier : on n'enferme pas le coach hors de son espace, mais on l'invite à confirmer.
        user.setEmailVerified(false);
        user.setVerifyToken(randomToken());
        user.setVerifyExpiresAt(java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS));
        user = userRepository.save(user);

        // Le créateur du club en est le propriétaire (membership multi-coach).
        com.coachrun.entity.ClubMember owner = new com.coachrun.entity.ClubMember();
        owner.setClub(club);
        owner.setCoach(user);
        owner.setClubRole(com.coachrun.entity.enums.ClubRole.OWNER);
        owner.setActive(true);
        clubMemberRepository.save(owner);

        notificationService.notifyEmailVerification(user.getEmail(), user.getFullName(),
                frontendUrl + "/verify-email/" + user.getVerifyToken());
        log.info("Nouveau coach inscrit (club={}, e-mail à vérifier)", club.getId());
        return toAuthResponse(user);
    }

    /** Confirme l'adresse e-mail à partir du jeton de vérification (lien d'inscription). */
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerifyToken(token)
                .filter(u -> u.getVerifyExpiresAt() != null
                        && u.getVerifyExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new NotFoundException("Lien de vérification invalide ou expiré."));
        user.setEmailVerified(true);
        user.setVerifyToken(null);
        user.setVerifyExpiresAt(null);
        log.info("E-mail vérifié (user={})", user.getId());
    }

    /** Renvoie un e-mail de vérification au compte courant (s'il n'est pas déjà vérifié). */
    @Transactional
    public void resendVerification(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Session invalide."));
        if (user.isEmailVerified() || user.getEmail() == null
                || user.getEmail().endsWith("@athlete.coachrun.local")) {
            return; // déjà vérifié ou compte sans e-mail réel : rien à faire
        }
        user.setVerifyToken(randomToken());
        user.setVerifyExpiresAt(java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS));
        notificationService.notifyEmailVerification(user.getEmail(), user.getFullName(),
                frontendUrl + "/verify-email/" + user.getVerifyToken());
        log.info("E-mail de vérification renvoyé (user={})", user.getId());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RESET_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Email ou mot de passe incorrect."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email ou mot de passe incorrect.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Ce compte est suspendu.");
        }
        return toAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        final Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (RuntimeException ex) {
            throw new UnauthorizedException("Jeton de rafraîchissement invalide ou expiré.");
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            throw new UnauthorizedException("Jeton de rafraîchissement invalide.");
        }
        if (tokenBlacklist.isRevoked(claims.getId())) {
            throw new UnauthorizedException("Jeton de rafraîchissement déjà utilisé.");
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(() -> new UnauthorizedException("Compte introuvable."));
        // Rotation : on révoque l'ancien refresh avant d'en émettre un nouveau.
        tokenBlacklist.revoke(claims.getId(), claims.getExpiration().toInstant());
        return toAuthResponse(user);
    }

    /**
     * Onboarding athlète par lien magique : valide le token, crée (ou réutilise) le compte
     * ATHLETE rattaché à l'athlète, puis émet les jetons. Sans mot de passe.
     */
    @Transactional
    public AuthResponse acceptInvitation(String token, boolean healthDataConsent, String email, String password) {
        Athlete athlete = athleteRepository.findByInviteToken(token)
                .filter(a -> a.getInviteExpiresAt() != null
                        && a.getInviteExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new NotFoundException("Invitation invalide ou expirée."));

        if (healthDataConsent && athlete.getHealthDataConsentAt() == null) {
            athlete.setHealthDataConsentAt(java.time.Instant.now());
        }

        // Identifiant de connexion : e-mail fourni, sinon e-mail de l'athlète, sinon adresse interne.
        String provided = org.springframework.util.StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
        String existing = org.springframework.util.StringUtils.hasText(athlete.getEmail())
                ? athlete.getEmail().toLowerCase() : null;
        String loginEmail = provided != null ? provided
                : (existing != null ? existing : "ath-" + athlete.getId() + "@athlete.coachrun.local");

        User user = userRepository.findByAthleteId(athlete.getId()).orElse(null);
        if (user == null) {
            user = new User();
            user.setFullName(athlete.getFirstName() + " " + athlete.getLastName());
            user.setRole(UserRole.ATHLETE);
            user.setClub(athlete.getClub());
            user.setAthlete(athlete);
        }
        // Anti-collision : un autre compte ne doit pas déjà porter cet e-mail réel.
        if (!loginEmail.endsWith("@athlete.coachrun.local")) {
            User other = userRepository.findByEmailIgnoreCase(loginEmail).orElse(null);
            if (other != null && !other.getId().equals(user.getId())) {
                throw new ConflictException("Un compte existe déjà avec cet e-mail.");
            }
        }
        user.setEmail(loginEmail);
        user.setStatus(UserStatus.ACTIVE);
        if (org.springframework.util.StringUtils.hasText(password)) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        user = userRepository.save(user);
        if (provided != null && existing == null) {
            athlete.setEmail(loginEmail);
        }

        athlete.setStatus(AthleteStatus.ACTIVE);
        athlete.setInviteToken(null);
        athlete.setInviteExpiresAt(null);
        return toAuthResponse(user);
    }

    /** Infos publiques d'une invitation coach (page d'acceptation). */
    public com.coachrun.dto.response.CoachInvitationInfoResponse coachInvitationInfo(String token) {
        User user = requireCoachInvite(token);
        String clubName = user.getClub() != null ? user.getClub().getName() : null;
        return new com.coachrun.dto.response.CoachInvitationInfoResponse(
                user.getEmail(), user.getFullName(), clubName);
    }

    /** Acceptation d'une invitation coach : définit le mot de passe et active le compte. */
    @Transactional
    public AuthResponse acceptCoachInvitation(String token, String password, String fullName) {
        User user = requireCoachInvite(token);
        user.setPasswordHash(passwordEncoder.encode(password));
        if (org.springframework.util.StringUtils.hasText(fullName)) {
            user.setFullName(fullName.trim());
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setInviteToken(null);
        user.setInviteExpiresAt(null);
        log.info("Invitation coach acceptée (user={})", user.getId());
        return toAuthResponse(user);
    }

    /**
     * Demande de réinitialisation : envoie un lien si un compte avec e-mail réel existe. Ne révèle
     * jamais l'existence du compte (réponse identique dans tous les cas).
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (!org.springframework.util.StringUtils.hasText(email)) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email.trim().toLowerCase()).ifPresent(u -> {
            if (u.getEmail() == null || u.getEmail().endsWith("@athlete.coachrun.local")) {
                return; // compte sans e-mail réel (athlète lien magique) → pas de reset par e-mail
            }
            byte[] bytes = new byte[32];
            RESET_RANDOM.nextBytes(bytes);
            String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            u.setResetToken(token);
            u.setResetExpiresAt(java.time.Instant.now().plus(2, java.time.temporal.ChronoUnit.HOURS));
            notificationService.notifyPasswordReset(u.getEmail(), u.getFullName(),
                    frontendUrl + "/reset-password/" + token);
            log.info("Réinitialisation de mot de passe demandée (user={})", u.getId());
        });
    }

    /** Vrai si le jeton de réinitialisation est valide et non expiré. */
    public boolean resetTokenValid(String token) {
        return userRepository.findByResetToken(token)
                .filter(u -> u.getResetExpiresAt() != null
                        && u.getResetExpiresAt().isAfter(java.time.Instant.now()))
                .isPresent();
    }

    /** Applique le nouveau mot de passe et ouvre une session. */
    @Transactional
    public AuthResponse resetPassword(String token, String password) {
        User user = userRepository.findByResetToken(token)
                .filter(u -> u.getResetExpiresAt() != null
                        && u.getResetExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new NotFoundException("Lien de réinitialisation invalide ou expiré."));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setResetToken(null);
        user.setResetExpiresAt(null);
        log.info("Mot de passe réinitialisé (user={})", user.getId());
        return toAuthResponse(user);
    }

    private User requireCoachInvite(String token) {
        return userRepository.findByInviteToken(token)
                .filter(u -> u.getInviteExpiresAt() != null
                        && u.getInviteExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new NotFoundException("Invitation invalide ou expirée."));
    }

    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Session invalide."));
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                jwtService.getAccessTtlSeconds(),
                UserResponse.from(user));
    }

    private String uniqueSlug(String clubName) {
        String base = SlugUtil.slugify(clubName);
        String slug = base;
        int i = 1;
        while (clubRepository.existsBySlug(slug)) {
            slug = base + "-" + (++i);
        }
        return slug;
    }
}
