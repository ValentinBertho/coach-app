package com.coachrun.dto.response;

import java.util.List;

/**
 * Les valeurs de filtre de l'annuaire, <b>avec leur nombre de coachs</b>.
 *
 * <h2>Pourquoi les comptes voyagent avec les valeurs</h2>
 *
 * <p>Un annuaire de dix coachs croisé avec discipline × langue × zone × distanciel rend zéro
 * résultat la plupart du temps. Une liste vide est la pire première impression possible : le
 * visiteur en conclut que la plateforme est vide, pas que son filtre est trop étroit. Le compte
 * permet à l'écran de <b>désactiver</b> ce qui ne rendrait rien, plutôt que de le proposer et de
 * décevoir.</p>
 *
 * <p><b>Limite assumée.</b> Ces comptes portent sur l'ensemble des fiches visibles, pas sur celles
 * qui restent après les autres filtres déjà cochés. Ils empêchent donc de choisir une valeur
 * inutile, pas une <em>combinaison</em> inutile. Le filet de sécurité de la combinaison est
 * ailleurs : la recherche rend son total, et l'écran retombe alors sur les coachs qui prennent des
 * athlètes en disant pourquoi. Conditionner chaque facette aux autres filtres coûterait une
 * requête par facette et par frappe, pour un annuaire qui en compte quelques dizaines.</p>
 */
public record CoachFacetsResponse(
        List<FacetValue> disciplines,
        List<FacetValue> specialties,
        List<FacetValue> languages,
        List<FacetValue> cities,
        /** Nombre total de fiches visibles : ce que rend une recherche sans aucun filtre. */
        long total,
        /** Combien acceptent des demandes aujourd'hui — les autres se consultent seulement. */
        long accepting) {

    /** Une valeur de filtre, son libellé lisible et le nombre de coachs qu'elle rendrait. */
    public record FacetValue(String value, String label, long count) {
    }
}
