package com.coachrun.repository;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Les deux dates qui périment un jeton — changement de mot de passe et déconnexion — en une
     * seule lecture. Le filtre JWT s'exécute à chaque requête authentifiée : deux requêtes là où
     * une projection suffit se paieraient sur toute la surface de l'API.
     */
    @org.springframework.data.jpa.repository.Query(
            "select u.passwordChangedAt as passwordChangedAt,"
                    + " u.sessionsInvalidatedAt as sessionsInvalidatedAt"
                    + " from User u where u.id = :userId")
    Optional<TokenCutoff> findTokenCutoff(
            @org.springframework.data.repository.query.Param("userId") UUID userId);

    /** Projection des dates de péremption des jetons d'un compte. */
    interface TokenCutoff {
        java.time.Instant getPasswordChangedAt();

        java.time.Instant getSessionsInvalidatedAt();

        /** La plus récente des deux, ou {@code null} si le compte n'a jamais rien révoqué. */
        default java.time.Instant latest() {
            java.time.Instant a = getPasswordChangedAt();
            java.time.Instant b = getSessionsInvalidatedAt();
            if (a == null) {
                return b;
            }
            return b == null || a.isAfter(b) ? a : b;
        }
    }

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByAthleteId(UUID athleteId);

    Optional<User> findByInviteToken(String inviteToken);

    Optional<User> findByResetToken(String resetToken);

    Optional<User> findByVerifyToken(String verifyToken);

    Optional<User> findFirstByClubIdAndRole(UUID clubId, UserRole role);

    long countByRole(UserRole role);

    @Query("""
            select distinct u from User u
            left join u.additionalClubs ac
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:clubId is null or u.club.id = :clubId or ac.id = :clubId)
              and (:verified is null or u.emailVerified = :verified)
              and (lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<User> searchAdmin(@Param("role") UserRole role,
                           @Param("status") UserStatus status,
                           @Param("clubId") UUID clubId,
                           @Param("verified") Boolean verified,
                           @Param("q") String q,
                           Pageable pageable);

    /** Vrai si le club appartient aux clubs additionnels de l'utilisateur (modèle multi-club). */
    @Query("""
            select case when count(c) > 0 then true else false end
            from User u join u.additionalClubs c
            where u.id = :userId and c.id = :clubId
            """)
    boolean hasClubAccess(@Param("userId") UUID userId, @Param("clubId") UUID clubId);

    /**
     * Pose la date de dernière activité sans charger l'entité.
     *
     * <p>Appelée depuis le filtre d'authentification : une lecture + un flush JPA y coûteraient
     * bien plus que l'{@code UPDATE} lui-même, et feraient sauter le verrou optimiste sur une
     * colonne qui n'a aucune sémantique métier. {@code clearAutomatically} est volontairement
     * absent — rien d'autre ne lit l'entité dans cette transaction.</p>
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update User u set u.lastSeenAt = :seenAt where u.id = :userId")
    void touchLastSeen(@Param("userId") UUID userId,
                       @Param("seenAt") java.time.Instant seenAt);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    long countByStatus(UserStatus status);

    /** Comptes créés depuis une date : croissance affichée par le pilotage. */
    long countByCreatedAtAfter(java.time.Instant since);

    /** Comptes vus depuis une date : « utilisateurs actifs » sur 24 h / 7 j / 30 j. */
    long countByLastSeenAtAfter(java.time.Instant since);

    /**
     * Comptes dont l'adresse n'est toujours pas confirmée passé un délai. Un coach bloqué là
     * n'écrit pas toujours au support : il abandonne. C'est le signal qui le rend visible.
     */
    @Query("""
            select count(u) from User u
            where u.emailVerified = false
              and u.status <> com.coachrun.entity.enums.UserStatus.SUSPENDED
              and u.createdAt < :before
            """)
    long countStaleUnverified(@Param("before") java.time.Instant before);

    /** Administrateurs actifs restants : garde-fou contre la perte d'accès au back-office. */
    long countByRoleAndStatusAndIdNot(UserRole role, UserStatus status, UUID excludedId);

    /** Coachs d'un club, club principal ou club additionnel (modèle multi-club). */
    @Query("""
            select distinct u from User u
            left join u.additionalClubs ac
            where (u.club.id = :clubId or ac.id = :clubId)
              and u.role in (com.coachrun.entity.enums.UserRole.HEAD_COACH,
                             com.coachrun.entity.enums.UserRole.COACH)
            order by u.fullName
            """)
    java.util.List<User> findAllCoachesOfClub(@Param("clubId") UUID clubId);

    /** Recherche libre bornée, pour la recherche globale du back-office. */
    @Query("""
            select u from User u
            where lower(u.email) like lower(concat('%', :q, '%'))
               or lower(u.fullName) like lower(concat('%', :q, '%'))
            order by u.fullName
            """)
    java.util.List<User> quickSearch(@Param("q") String q, Pageable pageable);

    /**
     * Clubs portant au moins un coach actif, rattachement principal <b>ou</b> additionnel.
     *
     * <p>Deux requêtes réunies plutôt qu'un {@code coalesce} : un coach ayant à la fois un club
     * principal et des clubs additionnels n'aurait rendu que le premier, et les clubs animés
     * uniquement par des coachs invités seraient apparus « sans encadrant » à tort.</p>
     */
    @Query("""
            select distinct u.club.id from User u
            where u.club is not null
              and u.role in (com.coachrun.entity.enums.UserRole.HEAD_COACH,
                             com.coachrun.entity.enums.UserRole.COACH)
              and u.status = com.coachrun.entity.enums.UserStatus.ACTIVE
            """)
    java.util.List<UUID> findPrimaryClubIdsWithActiveCoach();

    @Query("""
            select distinct ac.id from User u join u.additionalClubs ac
            where u.role in (com.coachrun.entity.enums.UserRole.HEAD_COACH,
                             com.coachrun.entity.enums.UserRole.COACH)
              and u.status = com.coachrun.entity.enums.UserStatus.ACTIVE
            """)
    java.util.List<UUID> findAdditionalClubIdsWithActiveCoach();

    /** Coachs (HEAD_COACH/COACH) actifs d'un club, pour rattachement à un athlète. */
    @Query("""
            select u from User u
            where u.club.id = :clubId and u.role in (com.coachrun.entity.enums.UserRole.HEAD_COACH,
                                                     com.coachrun.entity.enums.UserRole.COACH)
            order by u.fullName
            """)
    java.util.List<User> findCoachesByClub(@Param("clubId") UUID clubId);

    // ------------------------------------------------------------------------
    // Comptes inactifs (politique de conservation : 24 mois, préavis par e-mail)
    // ------------------------------------------------------------------------

    /**
     * Comptes à prévenir : inactifs depuis assez longtemps pour que l'échéance approche, et pas
     * encore prévenus <i>depuis</i> leur dernière activité.
     *
     * <p><b>Le {@code coalesce} n'est pas un détail.</b> {@code last_seen_at} n'existe que depuis
     * la migration 091 : le lire seul ferait passer tout compte antérieur pour inactif depuis
     * toujours — donc supprimable dès le premier passage. La date de connexion, puis celle de
     * création, servent de planchers.</p>
     *
     * <p><b>Le second membre non plus.</b> {@code warnedAt < lastActivity} traite un préavis
     * antérieur au retour de l'utilisateur comme inexistant : celui qui s'était reconnecté puis a
     * disparu de nouveau reçoit un <i>nouveau</i> préavis, il n'hérite pas de l'ancien.</p>
     *
     * <p>Les administrateurs de plateforme sont exclus par principe : leur compte n'est pas un
     * compte d'usage, et le supprimer fermerait le back-office sans moyen de le rouvrir.</p>
     */
    @Query("""
            select u from User u
            where u.role <> com.coachrun.entity.enums.UserRole.PLATFORM_ADMIN
              and coalesce(u.lastSeenAt, u.lastLoginAt, u.createdAt) <= :inactiveSince
              and (u.inactivityWarnedAt is null
                   or u.inactivityWarnedAt < coalesce(u.lastSeenAt, u.lastLoginAt, u.createdAt))
            order by coalesce(u.lastSeenAt, u.lastLoginAt, u.createdAt) asc
            """)
    java.util.List<User> findInactiveToWarn(@Param("inactiveSince") java.time.Instant inactiveSince,
                                            Pageable pageable);

    /**
     * Comptes à supprimer : inactifs au-delà de la durée annoncée, <b>et</b> prévenus — après
     * leur dernière activité, et il y a au moins le délai de préavis.
     *
     * <p>Les trois conditions sur {@code inactivityWarnedAt} portent chacune une promesse du
     * texte publié : « après un e-mail de préavis » (non nul), « le préavis vaut pour cette
     * inactivité-ci » (postérieur à la dernière activité), « on laisse le temps d'y répondre »
     * (assez ancien). Une seule qui manque, et l'on supprime un compte que son propriétaire
     * n'a pas eu l'occasion de sauver.</p>
     */
    @Query("""
            select u from User u
            where u.role <> com.coachrun.entity.enums.UserRole.PLATFORM_ADMIN
              and coalesce(u.lastSeenAt, u.lastLoginAt, u.createdAt) <= :inactiveSince
              and u.inactivityWarnedAt is not null
              and u.inactivityWarnedAt >= coalesce(u.lastSeenAt, u.lastLoginAt, u.createdAt)
              and u.inactivityWarnedAt <= :warnedBefore
            order by coalesce(u.lastSeenAt, u.lastLoginAt, u.createdAt) asc
            """)
    java.util.List<User> findInactiveToPurge(@Param("inactiveSince") java.time.Instant inactiveSince,
                                             @Param("warnedBefore") java.time.Instant warnedBefore,
                                             Pageable pageable);

    /**
     * Reste-t-il, dans ce club, un compte <b>autre</b> que celui-ci ? Sert à ne pas supprimer le
     * dernier encadrant d'un club qui contient encore des données : le club et ses athlètes
     * survivraient sans personne pour y accéder — ni pour exercer les droits de qui que ce soit.
     */
    @Query("""
            select count(u) from User u
            where u.club.id = :clubId and u.id <> :excludedUserId
            """)
    long countOtherMembersOfClub(@Param("clubId") UUID clubId,
                                 @Param("excludedUserId") UUID excludedUserId);
}
