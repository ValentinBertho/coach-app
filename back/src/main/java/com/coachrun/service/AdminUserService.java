package com.coachrun.service;

import com.coachrun.dto.request.AdminUserCreateRequest;
import com.coachrun.dto.request.AdminUserUpdateRequest;
import com.coachrun.dto.response.AdminAuditResponse;
import com.coachrun.dto.response.AdminUserDetailResponse;
import com.coachrun.dto.response.AdminUserResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.PushSubscriptionRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Administration des comptes utilisateurs (PLATFORM_ADMIN).
 *
 * <p><b>Trois garde-fous ajoutés</b>, tous contre le même accident : perdre l'accès au
 * back-office. Rien n'empêchait un administrateur de changer son propre rôle, de se suspendre ou
 * de supprimer le dernier compte d'administration — et il n'existe <i>aucun</i> chemin dans le
 * produit pour se rétablir : {@code PlatformAdminBootstrap} ne crée un compte que s'il n'y en a
 * pas, et seul un administrateur peut promouvoir un administrateur. La seule issue aurait été un
 * accès direct à la base de production.</p>
 *
 * <p><b>Chaque mutation laisse une trace</b> ({@code AdminAuditService}). Un back-office qui peut
 * supprimer un compte coach avec tout son historique d'entraînement doit pouvoir dire qui l'a
 * fait ; c'est aussi ce qui protège l'administrateur de bonne foi.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService audit;
    private final AuthService authService;

    /**
     * @param verified filtre sur la vérification d'adresse ({@code false} = comptes bloqués sur
     *                 leur e-mail de confirmation, exactement ce que le signal du pilotage pointe)
     */
    public PageResponse<AdminUserResponse> list(UserRole role, UserStatus status, UUID clubId,
                                                Boolean verified, String q, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim();
        return PageResponse.from(
                userRepository.searchAdmin(role, status, clubId, verified, query, pageable),
                AdminUserResponse::from);
    }

    public AdminUserResponse get(UUID id) {
        return AdminUserResponse.from(require(id));
    }

    /** Fiche complète : ce qu'il faut pour traiter un ticket sans ouvrir la base. */
    public AdminUserDetailResponse detail(UUID id) {
        User user = require(id);
        List<AdminAuditResponse> history = audit.forTarget(id);
        long coached = user.getRole() == UserRole.HEAD_COACH || user.getRole() == UserRole.COACH
                ? athleteRepository.countByCoachId(id)
                : 0;
        return AdminUserDetailResponse.from(user,
                pushSubscriptionRepository.countByUserId(id),
                coached,
                history);
    }

    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Un compte existe déjà avec cet email.");
        }
        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        applyClub(user, request.role(), request.clubId());
        User saved = userRepository.save(user);

        audit.record(AdminAuditAction.USER_CREATED, AdminAuditTarget.USER,
                saved.getId(), saved.getEmail(),
                "Rôle " + saved.getRole()
                        + (saved.getClub() != null ? ", club " + saved.getClub().getName() : ""));
        return AdminUserResponse.from(saved);
    }

    @Transactional
    public AdminUserResponse update(UUID id, AdminUserUpdateRequest request) {
        User user = require(id);
        UUID actor = currentActorId();
        StringBuilder changes = new StringBuilder();

        if (request.fullName() != null && !request.fullName().equals(user.getFullName())) {
            append(changes, "nom « " + user.getFullName() + " » → « " + request.fullName() + " »");
            user.setFullName(request.fullName());
        }
        if (request.role() != null && request.role() != user.getRole()) {
            // Se démettre soi-même n'a aucun chemin de retour : plus aucune route ne permet de se
            // repromouvoir, et le bootstrap ne recrée pas un compte qui existe déjà.
            if (id.equals(actor)) {
                throw new ForbiddenException(
                        "Vous ne pouvez pas changer votre propre rôle : personne ne pourrait vous "
                                + "le rendre. Demandez à un autre administrateur.");
            }
            requireRemainingAdmin(user, request.role(), user.getStatus());
            append(changes, "rôle " + user.getRole() + " → " + request.role());
            audit.record(AdminAuditAction.USER_ROLE_CHANGED, AdminAuditTarget.USER,
                    user.getId(), user.getEmail(),
                    "Rôle " + user.getRole() + " → " + request.role());
            user.setRole(request.role());
        }
        if (request.status() != null && request.status() != user.getStatus()) {
            if (id.equals(actor)) {
                throw new ForbiddenException("Vous ne pouvez pas changer le statut de votre propre compte.");
            }
            requireRemainingAdmin(user, user.getRole(), request.status());
            append(changes, "statut " + user.getStatus() + " → " + request.status());
            applyStatus(user, request.status());
        }
        if (request.clubId() != null
                && (user.getClub() == null || !request.clubId().equals(user.getClub().getId()))) {
            applyClub(user, user.getRole(), request.clubId());
            append(changes, "club principal → "
                    + (user.getClub() != null ? user.getClub().getName() : "aucun"));
        }

        if (!changes.isEmpty()) {
            audit.record(AdminAuditAction.USER_UPDATED, AdminAuditTarget.USER,
                    user.getId(), user.getEmail(), changes.toString());
        }
        return AdminUserResponse.from(user);
    }

    /**
     * Suspend un compte <b>et ferme ses sessions</b>.
     *
     * <p>Le statut {@code SUSPENDED} n'était vérifié qu'à la connexion : suspendre un compte
     * compromis le laissait travailler jusqu'à l'expiration de son jeton d'accès, puis se
     * rafraîchir pendant trente jours. Poser {@code sessionsInvalidatedAt} rend la suspension
     * immédiate — c'est le même mécanisme que la déconnexion volontaire.</p>
     */
    @Transactional
    public AdminUserResponse suspend(UUID id, String reason) {
        User user = require(id);
        if (id.equals(currentActorId())) {
            throw new ForbiddenException("Vous ne pouvez pas suspendre votre propre compte.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ConflictException("Ce compte est déjà suspendu.");
        }
        requireRemainingAdmin(user, user.getRole(), UserStatus.SUSPENDED);
        applyStatus(user, UserStatus.SUSPENDED);
        audit.record(AdminAuditAction.USER_SUSPENDED, AdminAuditTarget.USER,
                user.getId(), user.getEmail(),
                "Sessions fermées immédiatement"
                        + (reason != null && !reason.isBlank() ? " — motif : " + reason.trim() : ""));
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse reactivate(UUID id) {
        User user = require(id);
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new ConflictException("Ce compte est déjà actif.");
        }
        user.setStatus(UserStatus.ACTIVE);
        audit.record(AdminAuditAction.USER_REACTIVATED, AdminAuditTarget.USER,
                user.getId(), user.getEmail(), null);
        return AdminUserResponse.from(user);
    }

    /**
     * Ferme toutes les sessions du compte sans le suspendre. Geste de support courant : « j'ai
     * perdu mon téléphone », « je crois qu'on a accédé à mon compte ».
     */
    @Transactional
    public AdminUserResponse revokeSessions(UUID id) {
        User user = require(id);
        user.setSessionsInvalidatedAt(Instant.now());
        audit.record(AdminAuditAction.USER_SESSIONS_REVOKED, AdminAuditTarget.USER,
                user.getId(), user.getEmail(), "Toutes les sessions ouvertes ont été fermées.");
        return AdminUserResponse.from(user);
    }

    /**
     * Envoie un lien de réinitialisation à l'utilisateur.
     *
     * <p>L'administrateur ne choisit <b>jamais</b> le mot de passe : il n'a aucune raison de le
     * connaître, et un mot de passe transmis par un canal tiers est un mot de passe partagé. Le
     * lien part à l'adresse enregistrée, et seul son titulaire peut s'en servir.</p>
     */
    @Transactional
    public void sendPasswordReset(UUID id) {
        User user = require(id);
        if (user.getEmail() == null || user.getEmail().endsWith("@athlete.coachrun.local")) {
            throw new ConflictException(
                    "Ce compte n'a pas d'adresse e-mail réelle (athlète créé par lien magique) : "
                            + "renvoyez-lui une invitation depuis sa fiche athlète.");
        }
        authService.requestPasswordReset(user.getEmail());
        audit.record(AdminAuditAction.USER_PASSWORD_RESET_SENT, AdminAuditTarget.USER,
                user.getId(), user.getEmail(), "Lien valable 2 heures.");
    }

    /** Renvoie l'e-mail de confirmation d'adresse — le blocage n° 1 des nouveaux coachs. */
    @Transactional
    public void resendVerification(UUID id) {
        User user = require(id);
        if (user.isEmailVerified()) {
            throw new ConflictException("L'adresse de ce compte est déjà vérifiée.");
        }
        authService.resendVerification(id);
        audit.record(AdminAuditAction.USER_VERIFICATION_RESENT, AdminAuditTarget.USER,
                user.getId(), user.getEmail(), null);
    }

    @Transactional
    public void delete(UUID id) {
        User user = require(id);
        if (id.equals(currentActorId())) {
            throw new ForbiddenException("Vous ne pouvez pas supprimer votre propre compte.");
        }
        requireRemainingAdmin(user, user.getRole(), UserStatus.SUSPENDED);
        // Consigné AVANT la suppression : après, l'e-mail et le rôle n'existent plus nulle part.
        audit.record(AdminAuditAction.USER_DELETED, AdminAuditTarget.USER,
                user.getId(), user.getEmail(),
                "Rôle " + user.getRole()
                        + (user.getClub() != null ? ", club " + user.getClub().getName() : "")
                        + " — suppression en cascade de ses données.");
        userRepository.delete(user);
        log.warn("Compte supprimé par l'administration (user={}, rôle={})", id, user.getRole());
    }

    /** Rattache un club additionnel à un coach (modèle multi-club). */
    @Transactional
    public AdminUserResponse addClub(UUID userId, UUID clubId) {
        User user = require(userId);
        if (user.getClub() != null && clubId.equals(user.getClub().getId())) {
            throw new ConflictException("Ce club est déjà le club principal de l'utilisateur.");
        }
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new NotFoundException("Club introuvable."));
        if (!user.getAdditionalClubs().add(club)) {
            throw new ConflictException("Ce club est déjà rattaché à l'utilisateur.");
        }
        audit.record(AdminAuditAction.USER_CLUB_ADDED, AdminAuditTarget.USER,
                user.getId(), user.getEmail(), "Club rattaché : " + club.getName());
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse removeClub(UUID userId, UUID clubId) {
        User user = require(userId);
        String removed = user.getAdditionalClubs().stream()
                .filter(c -> c.getId().equals(clubId))
                .map(Club::getName)
                .findFirst()
                .orElse(null);
        user.getAdditionalClubs().removeIf(c -> c.getId().equals(clubId));
        if (removed != null) {
            audit.record(AdminAuditAction.USER_CLUB_REMOVED, AdminAuditTarget.USER,
                    user.getId(), user.getEmail(), "Club détaché : " + removed);
        }
        return AdminUserResponse.from(user);
    }

    /**
     * Refuse le geste s'il retirerait le dernier administrateur actif.
     *
     * <p>Le contrôle est fait <b>avant</b> la mutation, sur l'état visé : c'est le seul moment où
     * l'on peut encore refuser. Après, il n'y a plus personne pour rétablir quoi que ce soit.</p>
     */
    private void requireRemainingAdmin(User user, UserRole targetRole, UserStatus targetStatus) {
        boolean wasActiveAdmin = user.getRole() == UserRole.PLATFORM_ADMIN
                && user.getStatus() == UserStatus.ACTIVE;
        boolean staysActiveAdmin = targetRole == UserRole.PLATFORM_ADMIN
                && targetStatus == UserStatus.ACTIVE;
        if (!wasActiveAdmin || staysActiveAdmin) {
            return;
        }
        long others = userRepository.countByRoleAndStatusAndIdNot(
                UserRole.PLATFORM_ADMIN, UserStatus.ACTIVE, user.getId());
        if (others == 0) {
            throw new ConflictException(
                    "C'est le dernier administrateur actif : le retirer fermerait le back-office "
                            + "pour tout le monde, sans aucun moyen de le rouvrir depuis "
                            + "l'application. Créez d'abord un autre administrateur.");
        }
    }

    /** Passer à SUSPENDED ferme aussi les sessions en cours ; c'est ce qui rend le geste effectif. */
    private void applyStatus(User user, UserStatus status) {
        user.setStatus(status);
        if (status == UserStatus.SUSPENDED) {
            user.setSessionsInvalidatedAt(Instant.now());
        }
    }

    private void applyClub(User user, UserRole role, UUID clubId) {
        if (role == UserRole.HEAD_COACH || role == UserRole.COACH) {
            if (clubId == null) {
                throw new ConflictException("Un coach doit être rattaché à un club.");
            }
            user.setClub(clubRepository.findById(clubId)
                    .orElseThrow(() -> new NotFoundException("Club introuvable.")));
        } else if (clubId != null) {
            user.setClub(clubRepository.findById(clubId)
                    .orElseThrow(() -> new NotFoundException("Club introuvable.")));
        }
    }

    private UUID currentActorId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return (auth != null
                && auth.getPrincipal() instanceof com.coachrun.security.AuthPrincipal principal)
                ? principal.userId()
                : null;
    }

    private static void append(StringBuilder sb, String change) {
        if (!sb.isEmpty()) {
            sb.append(" ; ");
        }
        sb.append(change);
    }

    private User require(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }
}
