package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Abonnement WebPush envoyé par le navigateur ({@code PushSubscription.toJSON()}).
 *
 * <p><b>Les clés sont obligatoires, et c'est nouveau.</b> Elles étaient acceptées nulles ici alors
 * que la colonne les refuse : une charge utile sans bloc {@code keys} passait la validation, puis
 * échouait à l'écriture en violation de contrainte. Le gestionnaire d'erreurs en faisait un
 * <b>409 « Cette opération entre en conflit avec des données existantes »</b> — un message qui ne
 * décrit rien de ce qui s'est passé — et l'application affichait « Enregistrement impossible,
 * réessaie dans un instant », c'est-à-dire une invitation à recommencer une manœuvre qui ne peut
 * pas aboutir.</p>
 *
 * <p>Sans ces deux clés, un abonnement est de toute façon inutilisable : {@code p256dh} et
 * {@code auth} sont ce qui permet de chiffrer la charge utile pour cet appareil. Un abonnement
 * qui en manque ne recevrait jamais rien — mieux vaut le refuser tout de suite, avec un 400 qui
 * nomme le champ manquant.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushSubscribeRequest(
        @NotBlank String endpoint,
        @NotNull(message = "Les clés de chiffrement de l'abonnement sont absentes.")
        @Valid Keys keys) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keys(
            @NotBlank(message = "Clé publique (p256dh) absente.") String p256dh,
            @NotBlank(message = "Secret d'authentification (auth) absent.") String auth) {
    }
}
