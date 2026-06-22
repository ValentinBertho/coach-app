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
}
