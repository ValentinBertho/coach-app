package com.coachrun.dto.response;

/**
 * Le résultat d'une validation de demande.
 *
 * @param request       la demande, désormais validée, avec les identifiants créés
 * @param activationUrl lien à usage unique permettant au coach de poser son mot de passe
 * @param mailSent      vrai si l'e-mail portant ce lien est effectivement parti
 *
 * <p>Le lien est rendu à l'administrateur <b>et</b> envoyé par e-mail. Ce n'est pas une
 * redondance : l'envoi d'e-mails peut être éteint sur l'instance, ou l'adresse peut rebondir, et
 * dans ces deux cas le coach validé resterait devant une porte fermée sans que personne ne le
 * sache. Avec le lien sous la main, l'administrateur le transmet par le canal qu'il veut.</p>
 */
public record ClubRequestApprovalResponse(
        ClubCreationRequestResponse request,
        String activationUrl,
        boolean mailSent) {
}
