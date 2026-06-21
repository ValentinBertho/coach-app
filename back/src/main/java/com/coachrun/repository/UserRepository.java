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

    Optional<User> findFirstByClubIdAndRole(UUID clubId, UserRole role);

    long countByRole(UserRole role);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.email) like lower(concat('%', :q, '%'))
                              or lower(u.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<User> searchAdmin(@Param("role") UserRole role,
                           @Param("status") UserStatus status,
                           @Param("q") String q,
                           Pageable pageable);
}
