package com.coachrun.dto.response;

import java.util.UUID;

/**
 * Un destinataire possible pour « Nouveau message ».
 *
 * <p>La liste est calculée côté serveur et non déduite d'une liste d'athlètes ou de membres :
 * c'est elle qui dit à qui l'on a le droit d'écrire, et l'envoi la revérifie.</p>
 *
 * @param kind COACH (écrire à un coach) ou ATHLETE (ouvrir le fil d'un athlète)
 * @param id   identifiant du coach (utilisateur) ou de l'athlète, selon le genre
 */
public record RecipientResponse(String kind, UUID id, String name, String subtitle) {
}
