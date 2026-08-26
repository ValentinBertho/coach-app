package com.coachrun.dto.response;

import java.util.List;

/**
 * Résultat de la recherche globale du back-office.
 *
 * <p><b>Le problème qu'elle résout.</b> Un ticket de support arrive avec une adresse e-mail ou un
 * nom, et rien d'autre. Avant, il fallait déjà savoir si « Dupont » était un compte, un athlète ou
 * un club pour choisir le bon onglet — puis recommencer dans le suivant quand on s'était trompé.</p>
 *
 * <p>Chaque famille est bornée à quelques résultats, avec son total : la recherche sert à
 * <b>atteindre</b> une fiche, pas à parcourir un tableau (les écrans dédiés le font mieux).</p>
 */
public record AdminSearchResponse(
        String query,
        List<Hit> users,
        long usersTotal,
        List<Hit> clubs,
        long clubsTotal,
        List<Hit> athletes,
        long athletesTotal) {

    /**
     * Un résultat, uniformisé pour que le front n'ait qu'un gabarit.
     *
     * @param id       identifiant de la ressource
     * @param title    ce qu'on lit en premier (nom, raison sociale)
     * @param subtitle ce qui lève l'ambiguïté (e-mail, club de rattachement)
     * @param badge    statut ou rôle, affiché en pastille
     * @param route    route front de la fiche
     */
    public record Hit(
            java.util.UUID id,
            String title,
            String subtitle,
            String badge,
            String route) {
    }
}
