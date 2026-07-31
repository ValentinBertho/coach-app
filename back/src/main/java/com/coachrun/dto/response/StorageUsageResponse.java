package com.coachrun.dto.response;

/**
 * Espace de stockage consommé par les pièces jointes d'un club.
 *
 * @param usedBytes  octets déjà stockés
 * @param quotaBytes plafond du club
 */
public record StorageUsageResponse(long usedBytes, long quotaBytes) {

    /** Part du quota consommée, en pourcentage arrondi. */
    public int usedPct() {
        return quotaBytes <= 0 ? 0 : (int) Math.min(100, Math.round(usedBytes * 100.0 / quotaBytes));
    }
}
