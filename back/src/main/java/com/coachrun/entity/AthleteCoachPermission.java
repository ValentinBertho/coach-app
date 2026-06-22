package com.coachrun.entity;

import com.coachrun.entity.enums.PermissionLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Permission accordée à un coach <em>non référent</em> sur un athlète <strong>club</strong>
 * (cf. DARI Lab). Couvre le remplacement temporaire ({@link #expiresAt}), la supervision,
 * le co-coaching et l'intervention ponctuelle. Ne s'applique jamais à un athlète privé.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_coach_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_athlete_coach_perm", columnNames = {"athlete_id", "coach_id"}))
public class AthleteCoachPermission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 16)
    private PermissionLevel permission = PermissionLevel.READ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private User grantedBy;

    /** {@code null} = permanent ; sinon date d'expiration (ex. remplacement temporaire). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** {@code true} si la permission est encore active à l'instant {@code now}. */
    @Transient
    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
