package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** Abonnement WebPush envoyé par le navigateur (PushSubscription.toJSON()). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushSubscribeRequest(@NotBlank String endpoint, Keys keys) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keys(String p256dh, String auth) {
    }
}
