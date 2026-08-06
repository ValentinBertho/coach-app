package com.coachrun.dto.response;

import com.coachrun.entity.PushSubscription;

import java.time.Instant;
import java.util.UUID;

/**
 * Un appareil abonné aux notifications, tel qu'il se présente à son propriétaire.
 *
 * <p><b>L'endpoint n'en fait pas partie.</b> C'est une URL secrète : qui la détient peut pousser
 * une notification sur l'appareil. Elle n'a aucune raison de traverser l'API pour être affichée,
 * alors qu'un identifiant opaque suffit à désigner la ligne à retirer.</p>
 */
public record PushDeviceResponse(UUID id, String label, String fingerprint,
                                 Instant createdAt, Instant lastSuccessAt) {

    public static PushDeviceResponse from(PushSubscription sub) {
        return new PushDeviceResponse(sub.getId(), label(sub.getUserAgent()),
                fingerprint(sub.getEndpoint()), sub.getCreatedAt(), sub.getLastSuccessAt());
    }

    /**
     * Empreinte de l'endpoint, pour que le navigateur reconnaisse <b>sa</b> ligne dans la liste
     * sans que l'URL secrète ait à revenir jusqu'à lui.
     *
     * <p>Sans elle, retirer l'appareil courant ne tiendrait pas : le client se ré-enregistre au
     * démarrage dès qu'il constate un abonnement navigateur actif, et la ligne réapparaîtrait
     * toute seule. En se reconnaissant, il sait qu'il doit aussi défaire son propre abonnement.</p>
     *
     * <p>SHA-256 tronqué : on cherche à identifier une ligne parmi trois, pas à résister à une
     * attaque — et l'empreinte ne permet pas de remonter à l'endpoint.</p>
     */
    public static String fingerprint(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * Nom lisible déduit de la chaîne du navigateur — « iPhone · Safari ». Volontairement grossier :
     * il ne sert qu'à ce que quelqu'un reconnaisse son propre téléphone dans une liste de deux ou
     * trois lignes, pas à faire de la statistique d'usage.
     */
    public static String label(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Appareil inconnu";
        }
        String ua = userAgent;
        String platform = ua.contains("iPhone") ? "iPhone"
                : ua.contains("iPad") ? "iPad"
                : ua.contains("Android") ? "Android"
                : ua.contains("Macintosh") || ua.contains("Mac OS") ? "Mac"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Linux") ? "Linux"
                : "Appareil";
        // L'ordre compte : tous les navigateurs se déclarent « Safari », et Edge se déclare
        // « Chrome ». On teste donc du plus spécifique au plus générique.
        String browser = ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") || ua.contains("Opera") ? "Opera"
                : ua.contains("Firefox") || ua.contains("FxiOS") ? "Firefox"
                : ua.contains("CriOS") || ua.contains("Chrome") ? "Chrome"
                : ua.contains("Safari") ? "Safari"
                : null;
        return browser == null ? platform : platform + " · " + browser;
    }
}
