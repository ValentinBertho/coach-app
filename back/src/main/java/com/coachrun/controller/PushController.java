package com.coachrun.controller;

import com.coachrun.dto.request.PushSubscribeRequest;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Abonnement aux notifications push (authentifié, tous rôles). */
@RestController
@RequestMapping("/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushService;

    @GetMapping("/public-key")
    public Map<String, Object> publicKey() {
        return Map.of("enabled", pushService.isEnabled(), "publicKey", pushService.publicKey());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal AuthPrincipal principal,
                          @Valid @RequestBody PushSubscribeRequest request,
                          @org.springframework.web.bind.annotation.RequestHeader(
                                  value = "User-Agent", required = false) String userAgent) {
        // Les clés sont désormais exigées par la validation : plus de branche « peut-être nul »
        // qui laissait passer une charge utile que la base refuse ensuite.
        pushService.subscribe(principal.userId(), request.endpoint(),
                request.keys().p256dh(), request.keys().auth(), userAgent);
    }

    /**
     * Appareils abonnés du compte courant. La réponse ne porte jamais l'endpoint : c'est une URL
     * secrète, et un identifiant opaque suffit à désigner la ligne à retirer.
     */
    @GetMapping("/devices")
    public java.util.List<com.coachrun.dto.response.PushDeviceResponse> devices(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return pushService.devices(principal.userId());
    }

    /**
     * Envoi d'essai vers ses propres appareils : le seul moyen, pour celui qui les porte, de
     * savoir si la chaîne de notification fonctionne réellement.
     *
     * <p>La destination dépend du rôle — un athlète qui ouvre la notification doit retomber sur sa
     * journée, un coach sur son centre de notifications. Ouvrir la racine renverrait l'un et
     * l'autre sur la page publique.</p>
     */
    @PostMapping("/test")
    public com.coachrun.dto.response.PushTestResponse test(
            @AuthenticationPrincipal AuthPrincipal principal) {
        String url = principal.role() == com.coachrun.entity.enums.UserRole.ATHLETE
                ? "/athlete/today" : "/app/notifications";
        return pushService.sendTest(principal.userId(), url);
    }

    /** Retire un appareil précis — celui qu'on a perdu, revendu, ou prêté. */
    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@AuthenticationPrincipal AuthPrincipal principal,
                             @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        pushService.removeDevice(principal.userId(), id);
    }

    /**
     * Désabonnement d'un appareil. L'{@code endpoint} est désormais confronté au porteur du
     * jeton : la route acceptait n'importe quelle valeur et supprimait l'abonnement
     * correspondant, quel qu'en soit le propriétaire — le seul endroit du produit où une
     * ressource d'autrui était modifiable sans contrôle de propriété.
     */
    @DeleteMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal AuthPrincipal principal,
                            @RequestParam String endpoint) {
        pushService.unsubscribe(principal.userId(), endpoint);
    }

    /**
     * Désabonne <b>tous</b> les appareils du compte courant. Appelé à la déconnexion : sans ça,
     * un téléphone partagé continue de recevoir les notifications du compte précédent — dont le
     * titre des séances commentées par son coach.
     */
    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribeAll(@AuthenticationPrincipal AuthPrincipal principal) {
        pushService.unsubscribeUser(principal.userId());
    }
}
