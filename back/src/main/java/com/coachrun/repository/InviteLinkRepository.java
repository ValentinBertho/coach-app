package com.coachrun.repository;

import com.coachrun.entity.InviteLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteLinkRepository extends JpaRepository<InviteLink, UUID> {

    Optional<InviteLink> findByToken(String token);

    Optional<InviteLink> findByIdAndCoachId(UUID id, UUID coachId);

    List<InviteLink> findByCoachIdOrderByCreatedAtDesc(UUID coachId);

    boolean existsByToken(String token);
}
