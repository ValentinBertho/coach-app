package com.coachrun.dto.response;

/**
 * Issue d'un « rejoindre » : {@code JOINED} (entrée directe, suivi dès maintenant),
 * {@code REQUESTED} (demande envoyée au coach), {@code VERIFY_EMAIL} (adresse à confirmer : le lien
 * est retenu et servira à la confirmation).
 */
public record InviteJoinResponse(String outcome, String coachName) {
}
