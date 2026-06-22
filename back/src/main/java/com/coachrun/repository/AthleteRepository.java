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
