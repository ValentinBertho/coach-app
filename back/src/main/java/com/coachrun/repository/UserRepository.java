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

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByAthleteId(UUID athleteId);

    Optional<User> findByInviteToken(String inviteToken);

    Optional<User> findFirstByClubIdAndRole(UUID clubId, UserRole role);

    long countByRole(UserRole role);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<User> searchAdmin(@Param("role") UserRole role,
                           @Param("status") UserStatus status,
                           @Param("q") String q,
                           Pageable pageable);

    /** Vrai si le club appartient aux clubs additionnels de l'utilisateur (modèle multi-club). */
    @Query("""
            select case when count(c) > 0 then true else false end
            from User u join u.additionalClubs c
            where u.id = :userId and c.id = :clubId
            """)
    boolean hasClubAccess(@Param("userId") UUID userId, @Param("clubId") UUID clubId);

    /** Coachs (HEAD_COACH/COACH) actifs d'un club, pour rattachement à un athlète. */
    @Query("""
            select u from User u
            where u.club.id = :clubId and u.role in (com.coachrun.entity.enums.UserRole.HEAD_COACH,
                                                     com.coachrun.entity.enums.UserRole.COACH)
            order by u.fullName
            """)
    java.util.List<User> findCoachesByClub(@Param("clubId") UUID clubId);
}
