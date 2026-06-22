package com.coachrun.entity.enums;

/**
 * Distances de référence d'une performance course (cf. DARI Lab — calcul VDOT).
 * Les distances trail n'ont pas de longueur fixe : elles sont conservées pour l'historique
 * mais exclues du calcul VDOT ({@link #hasFixedDistance()} == false).
 */
public enum RunDistance {
    D800("800m", 800),
    D1500("1500m", 1500),
    D3000("3000m", 3000),
    D5KM("5km", 5000),
    D10KM("10km", 10000),
    D15KM("15km", 15000),
    SEMI("semi", 21097),
    MARATHON("marathon", 42195),
    TRAIL_COURT("trail_court", 0),
    TRAIL_LONG("trail_long", 0);

    private final String code;
    private final int meters;

    RunDistance(String code, int meters) {
        this.code = code;
        this.meters = meters;
    }

    public String code() {
        return code;
    }

    public int meters() {
        return meters;
    }

    /** {@code true} si la distance est fixe (route/piste) et donc éligible au calcul VDOT. */
    public boolean hasFixedDistance() {
        return meters > 0;
    }
}
