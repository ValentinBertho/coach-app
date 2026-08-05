package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Abonnement WebPush d'un utilisateur (clés du navigateur). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "push_subscriptions")
public class PushSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, length = 1000, unique = true)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    /**
     * Chaîne du navigateur au moment de l'abonnement. Sert uniquement à nommer l'appareil dans
     * l'écran de réglages — « iPhone · Safari » se reconnaît, une URL d'endpoint non.
     */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /**
     * Dernière remise acceptée par cet endpoint. Écrite au plus une fois par heure : la question
     * à laquelle elle répond — « cet appareil répond-il encore ? » — se lit à l'heure près.
     */
    @Column(name = "last_success_at")
    private java.time.Instant lastSuccessAt;
}
