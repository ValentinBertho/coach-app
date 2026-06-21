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

    @Query("""
            select a from Athlete a
            where a.club.id = :clubId
              and (:status is null or a.status = :status)
              and (:q is null or lower(a.firstName) like lower(concat('%', :q, '%'))
                              or lower(a.lastName)  like lower(concat('%', :q, '%')))
            """)
    Page<Athlete> search(@Param("clubId") UUID clubId,
                         @Param("status") AthleteStatus status,
                         @Param("q") String q,
                         Pageable pageable);

    // --- Admin (cross-club) ---
    @Query("""
            select a from Athlete a
            where (:clubId is null or a.club.id = :clubId)
              and (:status is null or a.status = :status)
              and (:q is null or lower(a.firstName) like lower(concat('%', :q, '%'))
                              or lower(a.lastName)  like lower(concat('%', :q, '%')))
            """)
    Page<Athlete> searchAdmin(@Param("clubId") UUID clubId,
                              @Param("status") AthleteStatus status,
                              @Param("q") String q,
                              Pageable pageable);

    Page<Athlete> findByInviteTokenIsNotNull(Pageable pageable);

    long countByInviteTokenIsNotNull();
}
