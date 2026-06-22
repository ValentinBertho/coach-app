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

    @DeleteMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam String endpoint) {
        pushService.unsubscribe(endpoint);
    }
}
