package com.coachrun.dto.response;

/**
 * Bilan d'un import de plan.
 *
 * @param created   séances nouvelles
 * @param updated   séances à faire déjà importées, dont le contenu a changé
 * @param unchanged séances déjà importées et identiques — réimporter le même fichier ne fait rien
 * @param kept      séances déjà réalisées (ou déclarées), laissées telles quelles
 */
public record PlanImportResponse(int created, int updated, int unchanged, int kept) {
}
