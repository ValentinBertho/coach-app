package com.coachrun.dto.response;

import com.coachrun.engine.ComplianceEngine.CardiacDrift;
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
        List<Effort> efforts,
        Drift drift
) {

    /**
     * @param verdict        ON_TARGET | TOO_FAST | TOO_SLOW | NOT_ASSESSED
     * @param deltaSecPerKm  écart signé à la fourchette (négatif = plus rapide)
     * @param actualAvgHrBpm FC moyenne du tour apparié, nulle si la montre n'en donne pas
     */
    public record Effort(
            String label, Verdict verdict,
            Integer targetFastSecPerKm, Integer targetSlowSecPerKm,
            Integer actualPaceSecPerKm, Integer deltaSecPerKm,
            Integer actualAvgHrBpm) {
    }

    /**
     * Dérive du dernier bloc du corps de séance par rapport au premier. {@code null} tant qu'il n'y
     * a pas deux blocs porteurs d'une FC : sans comparaison possible, « 0 % » se lirait comme une
     * séance parfaitement stable.
     *
     * @param paceDeltaSecPerKm écart d'allure : il dit si la dérive a été subie (même allure, cœur
     *                          plus haut) ou compensée (cœur tenu, allure lâchée)
     */
    public record Drift(
            String firstLabel, String lastLabel,
            int hrDeltaBpm, double hrDeltaPct, Integer paceDeltaSecPerKm) {
    }

    public static ComplianceResponse empty() {
        return new ComplianceResponse(null, 0, 0, null, null, null, List.of(), null);
    }

    public static ComplianceResponse from(SessionCompliance c, UUID activityId) {
        return new ComplianceResponse(
                c.scorePct(), c.assessed(), c.onTarget(), c.verdictLabel(), c.detail(), activityId,
                c.efforts().stream()
                        .map(e -> new Effort(e.label(), e.verdict(), e.targetFastSecPerKm(),
                                e.targetSlowSecPerKm(), e.actualPaceSecPerKm(), e.deltaSecPerKm(),
                                e.actualAvgHrBpm()))
                        .toList(),
                driftOf(c.drift()));
    }

    private static Drift driftOf(CardiacDrift d) {
        return d == null ? null
                : new Drift(d.firstLabel(), d.lastLabel(), d.hrDeltaBpm(), d.hrDeltaPct(),
                        d.paceDeltaSecPerKm());
    }
}
