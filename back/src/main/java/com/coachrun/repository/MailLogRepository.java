package com.coachrun.repository;

import com.coachrun.entity.MailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MailLogRepository extends JpaRepository<MailLog, UUID> {

    /** Consommation depuis un instant : c'est la mesure du plafond (jour, mois). */
    long countBySentAtAfterAndSentTrue(Instant since);

    /** Échecs sur la période. Un pic ici précède toujours un « je n'ai rien reçu ». */
    long countBySentAtAfterAndSentFalse(Instant since);

    Page<MailLog> findAllByOrderBySentAtDesc(Pageable pageable);

    /**
     * Les envois d'une fenêtre, pour l'histogramme quotidien.
     *
     * <p>Agrégé en Java plutôt qu'en SQL, délibérément : le regroupement par jour dépend du fuseau
     * de l'application (un envoi de 23 h 30 ne compte pas pour le lendemain), et l'écrire en SQL
     * demanderait du natif — donc une requête qui diverge entre PostgreSQL et le moteur des tests.
     * Le volume ne le justifie pas : tout l'intérêt de cet écran est que ces envois se comptent en
     * milliers par mois, pas en millions.</p>
     */
    List<MailLog> findBySentAtAfterOrderBySentAtAsc(Instant since);

    /** Répartition par nature d'envoi sur la période : ce qui consomme réellement le quota. */
    @Query("""
            select m.kind, count(m)
            from MailLog m
            where m.sentAt >= :since and m.sent = true
            group by m.kind
            order by count(m) desc
            """)
    List<Object[]> volumeByKind(@Param("since") Instant since);

    /** Purge de rétention : l'adresse est une donnée personnelle, elle ne se garde pas sans fin. */
    long deleteBySentAtBefore(Instant cutoff);
}
