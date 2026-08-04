package com.coachrun.repository;

import com.coachrun.entity.AlertDigestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertDigestLogRepository extends JpaRepository<AlertDigestLog, UUID> {

    List<AlertDigestLog> findByCoachId(UUID coachId);

    /**
     * Purge des traces devenues inutiles : au-delà du délai de rappel, une alerte non réémise a
     * disparu et sa ligne ne sert plus qu'à faire grossir la table.
     */
    void deleteByLastSentAtBefore(Instant cutoff);
}
