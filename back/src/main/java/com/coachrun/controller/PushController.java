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
                          @Valid @RequestBody PushSubscribeRequest request) {
        pushService.subscribe(principal.userId(), request.endpoint(),
                request.keys() != null ? request.keys().p256dh() : null,
                request.keys() != null ? request.keys().auth() : null);
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
