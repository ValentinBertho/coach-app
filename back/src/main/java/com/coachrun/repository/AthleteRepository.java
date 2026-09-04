package com.coachrun.repository;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AthleteRepository extends JpaRepository<Athlete, UUID> {

    /** Scoping tenant systématique (anti-IDOR) : jamais de findById nu. Club principal uniquement. */
    Optional<Athlete> findByIdAndClubId(UUID id, UUID clubId);

    /** Athlètes rattachés à un modèle de zones (le jeu porte déjà son club : scoping assuré). */
    java.util.List<Athlete> findByZoneSetId(UUID zoneSetId);

    /**
     * Scoping tenant aligné sur le modèle multi-club : l'athlète appartient au club soit comme
     * club principal, soit comme club additionnel — cohérent avec {@link #search}. À utiliser pour
     * tout accès athlète scopé par club (l'autorisation fine reste portée par
     * {@code @athleteAccessValidator}). Évite les faux 404 sur les athlètes multi-clubs.
     */
    @Query("""
            select distinct a from Athlete a
            left join a.additionalClubs ac
            where a.id = :athleteId and (a.club.id = :clubId or ac.id = :clubId)
            """)
    Optional<Athlete> findByIdAndClubMembership(@Param("athleteId") UUID athleteId,
                                                @Param("clubId") UUID clubId);

    Optional<Athlete> findByInviteToken(String inviteToken);

    /** Vrai si le coach est explicitement assigné à l'athlète (ManyToMany athlete_coaches). */
    boolean existsByIdAndCoaches_Id(UUID athleteId, UUID coachId);

    @Query(value = """
            select distinct a from Athlete a
            left join a.additionalClubs ac
            where (a.club.id = :clubId or ac.id = :clubId)
              and (:status is null or a.status = :status)
              and (:groupId is null or a.group.id = :groupId)
              and (lower(a.firstName) like lower(concat('%', :q, '%'))
                   or lower(a.lastName) like lower(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(distinct a) from Athlete a
            left join a.additionalClubs ac
            where (a.club.id = :clubId or ac.id = :clubId)
              and (:status is null or a.status = :status)
              and (:groupId is null or a.group.id = :groupId)
              and (lower(a.firstName) like lower(concat('%', :q, '%'))
                   or lower(a.lastName) like lower(concat('%', :q, '%')))
            """)
    Page<Athlete> search(@Param("clubId") UUID clubId,
                         @Param("status") AthleteStatus status,
                         @Param("groupId") UUID groupId,
                         @Param("q") String q,
                         Pageable pageable);

    /** Athlètes rattachés à un coach (modèle multi-club : « mes athlètes » transverse aux clubs). */
    @Query("""
            select distinct a from Athlete a join a.coaches co
            where co.id = :coachId
              and (lower(a.firstName) like lower(concat('%', :q, '%'))
                   or lower(a.lastName) like lower(concat('%', :q, '%')))
            """)
    Page<Athlete> searchByCoach(@Param("coachId") UUID coachId,
                                @Param("q") String q,
                                Pageable pageable);

    long countByGroupId(UUID groupId);

    /** Athlètes actifs d'un groupe (scopé club) — pour l'application en masse d'un plan/mésocycle. */
    @Query("""
            select a from Athlete a
            where a.group.id = :groupId and a.club.id = :clubId and a.status = :status
            order by a.lastName asc
            """)
    java.util.List<Athlete> findActiveByGroup(@Param("groupId") UUID groupId,
                                              @Param("clubId") UUID clubId,
                                              @Param("status") AthleteStatus status);

    java.util.List<Athlete> findByClubIdOrderByLastNameAsc(UUID clubId);

    /**
     * Les athlètes d'un club, <b>club additionnel compris</b>.
     *
     * <p>Contrepartie de {@link #findByIdAndClubMembership} et de la recherche paginée, qui
     * acceptent toutes deux le rattachement additionnel. {@code findByClubIdOrderByLastNameAsc}, lui,
     * ne regarde que le club principal — si bien qu'un athlète rattaché à un second club apparaissait
     * dans la <b>liste</b> de ce club mais pas dans son <b>tableau de bord</b>, ni dans ses alertes,
     * ni dans son bilan hebdomadaire. Le défaut dormait tant que rien ne permettait de créer cette
     * situation depuis l'interface ; le sélecteur de club la rend atteignable.</p>
     */
    @Query("""
            select distinct a from Athlete a
            left join a.additionalClubs ac
            where a.club.id = :clubId or ac.id = :clubId
            order by a.lastName asc
            """)
    java.util.List<Athlete> findByClubMembershipOrderByLastNameAsc(@Param("clubId") UUID clubId);

    // --- Admin (cross-club) ---
    /**
     * L'adresse entre dans la recherche : un ticket de support arrive presque toujours avec une
     * adresse e-mail, jamais avec l'orthographe exacte du nom.
     */
    @Query("""
            select a from Athlete a
            where (:clubId is null or a.club.id = :clubId)
              and (:status is null or a.status = :status)
              and (lower(a.firstName) like lower(concat('%', :q, '%'))
                   or lower(a.lastName) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.email, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Athlete> searchAdmin(@Param("clubId") UUID clubId,
                              @Param("status") AthleteStatus status,
                              @Param("q") String q,
                              Pageable pageable);

    Page<Athlete> findByInviteTokenIsNotNull(Pageable pageable);

    long countByInviteTokenIsNotNull();

    long countByClubIdAndStatus(UUID clubId, AthleteStatus status);

    long countByClubIdAndInviteTokenIsNotNull(UUID clubId);

    long countByStatus(AthleteStatus status);

    long countByCreatedAtAfter(java.time.Instant since);

    /** Invitations athlète encore valides mais qui expirent bientôt : signal du pilotage. */
    @Query("""
            select count(a) from Athlete a
            where a.inviteToken is not null
              and a.inviteExpiresAt is not null
              and a.inviteExpiresAt between :now and :horizon
            """)
    long countInvitationsExpiringBefore(@Param("now") java.time.Instant now,
                                        @Param("horizon") java.time.Instant horizon);

    /** Invitations déjà périmées : elles ne mènent plus nulle part et doivent être renvoyées. */
    @Query("""
            select count(a) from Athlete a
            where a.inviteToken is not null
              and a.inviteExpiresAt is not null
              and a.inviteExpiresAt < :now
            """)
    long countExpiredInvitations(@Param("now") java.time.Instant now);

    /** Recherche libre bornée, pour la recherche globale du back-office. */
    @Query("""
            select a from Athlete a
            where lower(a.firstName) like lower(concat('%', :q, '%'))
               or lower(a.lastName) like lower(concat('%', :q, '%'))
               or lower(coalesce(a.email, '')) like lower(concat('%', :q, '%'))
            order by a.lastName, a.firstName
            """)
    java.util.List<Athlete> quickSearch(@Param("q") String q, Pageable pageable);

    long countByClubId(UUID clubId);

    /** Athlètes suivis par un coach : « combien de personnes ce compte encadre-t-il ? ». */
    @Query("select count(a) from Athlete a join a.coaches c where c.id = :coachId")
    long countByCoachId(@Param("coachId") UUID coachId);
}
