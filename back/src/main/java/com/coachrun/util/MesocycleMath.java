package com.coachrun.util;

/** Calculs de périodisation partagés (multiplicateur de charge par semaine d'un bloc). */
public final class MesocycleMath {

    private MesocycleMath() {
    }

    /**
     * Multiplicateur de charge appliqué à une semaine donnée d'un mésocycle : les semaines
     * d'accumulation montent de {@code increasePct} % par palier ; une semaine de décharge
     * (toutes les {@code deloadEvery} semaines) revient à {@code deloadPct} % de la base.
     * Cohérent avec {@code WorkoutService.generateMesocycle}.
     */
    public static double multiplierForWeek(int weekIndex, double increasePct,
                                           int deloadEvery, double deloadPct) {
        int blockLen = Math.max(2, deloadEvery);
        int buildIndex = 0;
        for (int i = 0; i <= weekIndex; i++) {
            boolean deload = (i % blockLen) == blockLen - 1;
            if (i == weekIndex) {
                return deload ? deloadPct / 100.0 : 1.0 + (increasePct / 100.0) * buildIndex;
            }
            if (!deload) {
                buildIndex++;
            }
        }
        return 1.0;
    }
}
