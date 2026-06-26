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

    /** Scoping tenant systématique (anti-IDOR) : jamais de findById nu. */
    Optional<Athlete> findByIdAndClubId(UUID id, UUID clubId);

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

    // --- Admin (cross-club) ---
    @Query("""
            select a from Athlete a
            where (:clubId is null or a.club.id = :clubId)
              and (:status is null or a.status = :status)
              and (lower(a.firstName) like lower(concat('%', :q, '%'))
                   or lower(a.lastName) like lower(concat('%', :q, '%')))
            """)
    Page<Athlete> searchAdmin(@Param("clubId") UUID clubId,
                              @Param("status") AthleteStatus status,
                              @Param("q") String q,
                              Pageable pageable);

    Page<Athlete> findByInviteTokenIsNotNull(Pageable pageable);

    long countByInviteTokenIsNotNull();

    long countByClubIdAndStatus(UUID clubId, AthleteStatus status);

    long countByClubIdAndInviteTokenIsNotNull(UUID clubId);
}
