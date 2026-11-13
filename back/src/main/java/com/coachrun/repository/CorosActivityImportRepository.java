package com.coachrun.repository;

import com.coachrun.entity.CorosActivityImport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorosActivityImportRepository extends JpaRepository<CorosActivityImport, UUID> {

    Optional<CorosActivityImport> findByAthleteIdAndLabelId(UUID athleteId, String labelId);

    boolean existsByAthleteId(UUID athleteId);

    /** Téléchargements FIT depuis {@code since} : COROS en autorise 50 par 24 h. */
    long countByAthleteIdAndDownloadedAtAfter(UUID athleteId, Instant since);

    @Query("select count(i) from CorosActivityImport i where i.athleteId = :athleteId"
            + " and i.outcome = com.coachrun.entity.CorosActivityImport.Outcome.IMPORTED")
    long countImported(@Param("athleteId") UUID athleteId);

    @Modifying
    @Query("delete from CorosActivityImport i where i.athleteId = :athleteId")
    void deleteByAthleteId(@Param("athleteId") UUID athleteId);
}
