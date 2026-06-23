package com.coachrun.repository;

import com.coachrun.entity.StrengthSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StrengthSessionRepository extends JpaRepository<StrengthSession, UUID> {

    Optional<StrengthSession> findByIdAndClubId(UUID id, UUID clubId);

    @Query("""
            select s from StrengthSession s
            where s.club.id = :clubId and s.archived = false
              and lower(s.name) like lower(concat('%', :q, '%'))
            order by s.name asc
            """)
    Page<StrengthSession> search(@Param("clubId") UUID clubId, @Param("q") String q, Pageable pageable);
}
