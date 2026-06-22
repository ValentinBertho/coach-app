package com.coachrun.repository;

import com.coachrun.entity.ClubMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubMemberRepository extends JpaRepository<ClubMember, UUID> {

    Optional<ClubMember> findByClubIdAndCoachIdAndActiveTrue(UUID clubId, UUID coachId);

    List<ClubMember> findByClubIdAndActiveTrue(UUID clubId);

    List<ClubMember> findByCoachIdAndActiveTrue(UUID coachId);
}
