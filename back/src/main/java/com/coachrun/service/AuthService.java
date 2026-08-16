package com.coachrun.service;

import com.coachrun.dto.request.LoginRequest;
import com.coachrun.dto.request.ChangePasswordRequest;
import com.coachrun.dto.request.RefreshRequest;
import com.coachrun.dto.request.UpdateProfileRequest;
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
    private final com.coachrun.security.TokenFreshnessValidator tokenFreshness;
    private final com.coachrun.security.LoginAttemptTracker loginAttempts;
    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;
    private final ClubStarterKitService starterKitService;

    private static final java.security.SecureRandom RESET_RANDOM = new java.security.SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    /** Mode d'inscription : « invite » (cohorte fermée) ou « open ». Prod : invite par défaut. */
    @org.springframework.beans.factory.annotation.Value("${app.registration.mode:open}")
    private String registrationMode;

    /** Code partagé de la cohorte, exigé en mode « invite ». */
    @org.springframework.beans.factory.annotation.Value("${app.registration.invite-code:}")
    private String registrationInviteCode;

    /**
     * Inscription libre ou sur code, selon {@code app.registration.mode}. Le runbook prévoit une
     * bêta sur cohorte fermée, mais {@code /auth/register} était public et n'exigeait que
     * l'unicité de l'e-mail : n'importe qui pouvait créer un club sur l'instance de production.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        requireValidInvitation(request.invitationCode());
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
        // Preuve de consentement RGPD (termsAccepted est garanti true par la validation).
        user.setTermsAcceptedAt(java.time.Instant.now());
        user = userRepository.save(user);

        // Le créateur du club en est le propriétaire (membership multi-coach).
        com.coachrun.entity.ClubMember owner = new com.coachrun.entity.ClubMember();
        owner.setClub(club);
        owner.setCoach(user);
        owner.setClubRole(com.coachrun.entity.enums.ClubRole.OWNER);
        owner.setActive(true);
        clubMemberRepository.save(owner);

        installStarterKit(club.getId());
        notificationService.notifyEmailVerification(user.getEmail(), user.getFullName(),
                frontendUrl + "/verify-email/" + user.getVerifyToken());
        log.info("Nouveau coach inscrit (club={}, e-mail à vérifier)", club.getId());
        return toAuthResponse(user);
    }

    /**
     * Pose le jeu de départ du club — <b>après le commit, et sans jamais faire échouer
     * l'inscription</b>.
     *
     * <p>Deux raisons de ne pas l'inclure dans la transaction d'inscription. La première est de
     * principe : créer un compte est l'opération la moins remplaçable du produit, et une
     * bibliothèque d'exemple est un agrément — un défaut dans dix modèles de séance ne doit pas
     * empêcher un coach d'ouvrir un compte. La seconde est pratique : le jeu de départ écrit une
     * trentaine de lignes, ce qui rallongerait d'autant une transaction tenue pendant que le
     * visiteur attend sa réponse.</p>
     *
     * <p>Conséquence assumée : sur l'échec, le coach arrive dans une bibliothèque vide — l'état
     * d'avant. Il est journalisé et remonté à Sentry, et le jeu reste installable après coup
     * puisqu'il est idempotent.</p>
     */
    private void installStarterKit(UUID clubId) {
        Runnable install = () -> {
            try {
                starterKitService.install(clubId);
            } catch (RuntimeException ex) {
                log.error("Jeu de départ non installé pour le club {} — l'inscription reste valide",
                        clubId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            install.run();
                        }
                    });
            return;
        }
        install.run();
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

    /**
     * Édite le profil de l'utilisateur courant. Un changement d'e-mail repasse le compte en
     * « non vérifié » et renvoie un lien de confirmation : sans ça, on pourrait s'attribuer une
     * adresse qu'on ne contrôle pas.
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Session invalide."));
        String email = request.email().trim().toLowerCase();
        boolean emailChanged = !email.equalsIgnoreCase(user.getEmail());
        if (emailChanged && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Cette adresse e-mail est déjà utilisée.");
        }
        user.setFullName(request.fullName().trim());
        if (request.paceUnit() != null) {
            user.setPaceUnit(request.paceUnit());
        }
        if (emailChanged) {
            user.setEmail(email);
            user.setEmailVerified(false);
            user.setVerifyToken(randomToken());
            user.setVerifyExpiresAt(java.time.Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS));
            notificationService.notifyEmailVerification(email, user.getFullName(),
                    frontendUrl + "/verify-email/" + user.getVerifyToken());
        }
        log.info("Profil mis à jour (user={}, emailChanged={})", userId, emailChanged);
        return UserResponse.from(user);
    }

    /** Changement de mot de passe : l'actuel est exigé (protection contre le vol de session). */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Session invalide."));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Mot de passe actuel incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Toutes les sessions ouvertes tombent : c'est le but d'un changement de mot de passe.
        user.setPasswordChangedAt(java.time.Instant.now());
        log.info("Mot de passe changé (user={}) — sessions antérieures révoquées", userId);
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

    /**
     * Vérifie le code d'invitation quand l'inscription est fermée. Message explicite : « accès
     * refusé » laisserait le coach invité penser que son compte est bloqué, alors qu'il s'est
     * seulement trompé de code.
     */
    private void requireValidInvitation(String submitted) {
        if (!"invite".equalsIgnoreCase(registrationMode)) {
            return;
        }
        if (!org.springframework.util.StringUtils.hasText(registrationInviteCode)) {
            // Mode fermé sans code configuré : personne ne pourrait s'inscrire. C'est une erreur
            // d'exploitation, pas une faute de l'utilisateur — on la signale comme telle.
            log.error("Inscription en mode « invite » sans REGISTRATION_INVITE_CODE configuré.");
            throw new com.coachrun.exception.ForbiddenException(
                    "Les inscriptions sont momentanément fermées. Contactez l'équipe Darilab.");
        }
        if (submitted == null || !registrationInviteCode.equals(submitted.trim())) {
            throw new com.coachrun.exception.ForbiddenException(
                    "Code d'invitation invalide. La bêta est ouverte sur invitation : "
                            + "utilisez le code reçu par e-mail.");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RESET_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public AuthResponse login(LoginRequest request) {
        // Verrou par compte : le rate limiting par IP n'arrête pas un attaquant qui répartit ses
        // essais sur plusieurs adresses pour forcer un compte précis.
        java.time.Duration lock = loginAttempts.lockRemaining(request.email());
        if (lock != null) {
            throw new com.coachrun.exception.ApiException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives — réessayez dans " + Math.max(1, lock.toSeconds()) + " s.");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Le compteur est alimenté même pour un compte inexistant : sinon la présence d'un
            // verrou révélerait quels e-mails existent.
            loginAttempts.recordFailure(request.email());
            throw new UnauthorizedException("Email ou mot de passe incorrect.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Ce compte est suspendu.");
        }
        loginAttempts.recordSuccess(request.email());
        return toAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        // Les cinq causes de refus sont journalisées distinctement. Elles se présentaient toutes
        // au client sous la forme d'un unique 401, donc d'un « Session expirée » indifférencié :
        // impossible, en production, de dire si un utilisateur déconnecté l'a été par un jeton
        // périmé, par une rotation rejouée ou par un changement de mot de passe.
        final Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (RuntimeException ex) {
            log.info("Refresh refusé : jeton illisible ou expiré ({})", ex.getClass().getSimpleName());
            throw new UnauthorizedException("Jeton de rafraîchissement invalide ou expiré.");
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))) {
            log.warn("Refresh refusé : jeton du mauvais type (user={})", claims.getSubject());
            throw new UnauthorizedException("Jeton de rafraîchissement invalide.");
        }
        if (tokenBlacklist.isRevoked(claims.getId())) {
            log.info("Refresh refusé : jeton déjà utilisé, rotation rejouée (user={})", claims.getSubject());
            throw new UnauthorizedException("Jeton de rafraîchissement déjà utilisé.");
        }
        User user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(() -> {
                    log.warn("Refresh refusé : compte introuvable (user={})", claims.getSubject());
                    return new UnauthorizedException("Compte introuvable.");
                });
        if (tokenFreshness.isStale(claims)) {
            log.info("Refresh refusé : jeton antérieur à la dernière révocation (user={})", user.getId());
            throw new UnauthorizedException("Le mot de passe a changé, reconnectez-vous.");
        }
        // Rotation : on révoque l'ancien refresh avant d'en émettre un nouveau.
        tokenBlacklist.revoke(claims.getId(), claims.getExpiration().toInstant());
        return toAuthResponse(user);
    }

    /**
     * Onboarding athlète par lien magique : valide le token, crée (ou réutilise) le compte
     * ATHLETE rattaché à l'athlète, puis émet les jetons. Sans mot de passe.
     */
    @Transactional
    public AuthResponse acceptInvitation(String token, boolean healthDataConsent,
                                         boolean termsAccepted, String email, String password) {
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
        // Première activation, par opposition à un lien rejoué : seule la première intéresse le
        // coach, qui attend de savoir quand il peut commencer à poser des séances.
        boolean firstActivation = user == null;
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
        // Preuve de consentement aux CGU, au même titre que pour un coach. Elle manquait pour les
        // athlètes : l'avertissement santé et la clause de bêta ne leur étaient pas opposables.
        if (termsAccepted && user.getTermsAcceptedAt() == null) {
            user.setTermsAcceptedAt(java.time.Instant.now());
        }
        user = userRepository.save(user);
        if (provided != null && existing == null) {
            athlete.setEmail(loginEmail);
        }

        athlete.setStatus(AthleteStatus.ACTIVE);
        athlete.setInviteToken(null);
        athlete.setInviteExpiresAt(null);
        if (firstActivation) {
            notificationService.notifyAthleteJoined(athlete);
        }
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
    public AuthResponse acceptCoachInvitation(String token, String password, String fullName,
                                              Boolean termsAccepted) {
        User user = requireCoachInvite(token);
        user.setPasswordHash(passwordEncoder.encode(password));
        if (org.springframework.util.StringUtils.hasText(fullName)) {
            user.setFullName(fullName.trim());
        }
        if (Boolean.TRUE.equals(termsAccepted)) {
            user.setTermsAcceptedAt(java.time.Instant.now());
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
        // Un reset sert souvent à reprendre la main sur un compte compromis : les jetons déjà
        // émis (refresh : 30 jours) doivent cesser de valoir, sinon l'intrus reste connecté.
        user.setPasswordChangedAt(java.time.Instant.now());
        log.info("Mot de passe réinitialisé (user={}) — sessions antérieures révoquées", user.getId());
        return toAuthResponse(user);
    }

    private User requireCoachInvite(String token) {
        return userRepository.findByInviteToken(token)
                .filter(u -> u.getInviteExpiresAt() != null
                        && u.getInviteExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new NotFoundException("Invitation invalide ou expirée."));
    }

    /**
     * Déconnexion : périme tous les jetons du compte émis jusqu'ici.
     *
     * <p>La liste noire en mémoire ne couvrait que l'access token présenté, et disparaissait au
     * premier redéploiement. Le refresh token — trente jours — restait donc valable côté serveur :
     * se déconnecter effaçait une copie locale, sans rien fermer.</p>
     *
     * <p>La révocation vaut pour <b>tous</b> les appareils du compte. C'est un choix : conserver
     * une granularité par appareil demanderait de stocker un jeton par session, alors que la
     * déconnexion est justement le moment où l'on veut être certain que plus rien ne traîne.</p>
     *
     * <p>Les abonnements push partent avec. Le navigateur prévenait déjà le serveur avant de se
     * déconnecter, mais au mieux : hors ligne ou session déjà expirée, l'appel échouait en
     * silence et l'appareil continuait d'afficher « Retour de votre coach — <i>titre de
     * séance</i> » pour le compte précédent. Sur un téléphone partagé, c'est l'entraînement de
     * quelqu'un d'autre qui s'affiche sur l'écran verrouillé. Révoquer les jetons sans couper le
     * canal qui, lui, continue de parler à l'appareil serait une demi-déconnexion.</p>
     */
    @Transactional
    public void logout(UUID userId) {
        userRepository.findById(userId)
                .ifPresent(user -> user.setSessionsInvalidatedAt(java.time.Instant.now()));
        pushNotificationService.unsubscribeUser(userId);
        log.info("Déconnexion (user={}) — sessions antérieures révoquées", userId);
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
