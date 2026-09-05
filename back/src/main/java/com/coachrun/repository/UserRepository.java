package com.coachrun.repository;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * Les administrateurs plateforme actifs — les destinataires du digest de modération.
     *
     * <p>Par rôle plutôt que par une adresse de configuration : `PLATFORM_ADMIN_EMAIL` ne sert
     * qu'à créer le premier compte au démarrage, et le jour où l'équipe compte deux
     * administrateurs, le second doit recevoir la file du matin sans qu'on redéploie.</p>
     */
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

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
}
