package com.coachrun.dto.response;

/**
 * Résultat d'un envoi d'essai, tel qu'il permet à l'utilisateur de conclure lui-même.
 *
 * <p>Une notification qui n'arrive pas ne dit jamais pourquoi : le serveur peut être sans clés
 * VAPID, le compte peut n'avoir aucun appareil abonné, ou l'appareil peut avoir révoqué son
 * abonnement sans le dire. Les trois se ressemblaient — « rien ne s'affiche » — et le seul moyen
 * de les distinguer était de lire les journaux du serveur. La réponse porte donc l'état réel du
 * canal au moment de l'envoi.</p>
 *
 * @param enabled le serveur sait-il envoyer un push (clés VAPID présentes) ?
 * @param devices nombre d'appareils abonnés au compte, destinataires de cet essai
 * @param channelMuted l'utilisateur a-t-il coupé le canal push dans ses préférences ? L'essai part
 *                     quand même — il a été demandé explicitement — mais le dire évite de conclure
 *                     que tout va bien alors que les notifications de routine, elles, sont muettes.
 * @param delivered    appareils dont le service de push a <b>accepté</b> le message. C'est la seule
 *                     information qui vaille : « envoyé » ne disait que « mis en file », ce qui
 *                     reste vrai quand la signature est refusée et que rien n'arrive jamais.
 * @param failures     une ligne par appareil en échec, en clair (« iPhone · Safari : abonnement
 *                     expiré ») — de quoi savoir quoi corriger sans lire les journaux du serveur.
 */
public record PushTestResponse(boolean enabled, int devices, boolean channelMuted,
                               int delivered, java.util.List<String> failures) {
}
