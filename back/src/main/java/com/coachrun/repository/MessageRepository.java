package com.coachrun.repository;

import com.coachrun.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByClubIdAndAthleteIdOrderByCreatedAtAsc(UUID clubId, UUID athleteId);

    List<Message> findByAthleteIdOrderByCreatedAtAsc(UUID athleteId);
}
