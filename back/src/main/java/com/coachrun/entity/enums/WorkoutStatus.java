package com.coachrun.entity.enums;

/**
 * État d'une séance : PLANNED au départ, puis COMPLETED, PARTIAL ou MISSED une fois vécue.
 *
 * <p><b>Un état est une déclaration, et une déclaration se corrige.</b> Ces quatre valeurs
 * portaient une machine à états qui n'autorisait, depuis un état terminal, que le retour à
 * PLANNED : corriger « écourtée » en « réalisée » était refusé (409). Or c'est un geste
 * ordinaire — on se trompe de bouton, on rouvre son débrief le lendemain pour ajuster — et rien
 * ne le rendait dangereux : le service recalcule tout ce qui dépend du statut à chaque écriture,
 * et le même résultat s'obtenait de toute façon en deux temps, en repassant par PLANNED. Une
 * garde que l'on contourne en un clic de plus ne protège rien ; elle ne fait que renvoyer une
 * erreur à quelqu'un qui a raison.</p>
 *
 * <p>Il n'y a donc plus de transition interdite. Les valeurs restent un ensemble fermé — c'est
 * l'enum qui garantit qu'aucun autre état n'existe — et les règles réelles vivent là où elles
 * ont un sens : une séance non faite perd son effort ({@code applyMissed}), une séance menée à
 * son terme perd sa durée écourtée.</p>
 */
public enum WorkoutStatus {
    PLANNED,
    COMPLETED,
    PARTIAL,
    MISSED
}
