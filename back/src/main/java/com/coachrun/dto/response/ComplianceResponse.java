package com.coachrun.dto.response;

import com.coachrun.engine.ComplianceEngine.SessionCompliance;
import com.coachrun.engine.ComplianceEngine.Verdict;

import java.util.List;
import java.util.UUID;

/**
 * « Séance tenue ? » — le verdict d'une séance et le détail de ses efforts.
 *
 * <p>{@code scorePct} nul signifie « rien n'était jugeable » et jamais « zéro pour cent » :
 * l'interface doit alors se taire, pas afficher un échec.</p>
 */
public record ComplianceResponse(
        Integer scorePct,
        int assessed,
        int onTarget,
        String verdict,
        String detail,
        UUID activityId,
        List<Effort> efforts
) {

    /**
     * @param verdict        ON_TARGET | TOO_FAST | TOO_SLOW | NOT_ASSESSED
     * @param deltaSecPerKm  écart signé à la fourchette (négatif = plus rapide)
     */
    public record Effort(
            String label, Verdict verdict,
            Integer targetFastSecPerKm, Integer targetSlowSecPerKm,
            Integer actualPaceSecPerKm, Integer deltaSecPerKm) {
    }

    public static ComplianceResponse empty() {
        return new ComplianceResponse(null, 0, 0, null, null, null, List.of());
    }

    public static ComplianceResponse from(SessionCompliance c, UUID activityId) {
        return new ComplianceResponse(
                c.scorePct(), c.assessed(), c.onTarget(), c.verdictLabel(), c.detail(), activityId,
                c.efforts().stream()
                        .map(e -> new Effort(e.label(), e.verdict(), e.targetFastSecPerKm(),
                                e.targetSlowSecPerKm(), e.actualPaceSecPerKm(), e.deltaSecPerKm()))
                        .toList());
    }
}
