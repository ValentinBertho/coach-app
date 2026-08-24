package com.coachrun.entity;

import com.coachrun.entity.enums.GroupVisibility;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Groupe d'entraînement d'un club (ex. « Marathon », « Débutants »). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "training_groups")
public class TrainingGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Qui voit ce groupe. {@code CLUB} par défaut — c'est ce qui était visible hier, et un groupe
     * existant n'a pas de créateur connu à qui le réserver.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private GroupVisibility visibility = GroupVisibility.CLUB;

    /** Créateur du groupe : seul, avec ses invités, à voir un groupe privé. */
    @Column(name = "owner_coach_id")
    private java.util.UUID ownerCoachId;

    /** Coachs conviés à un groupe privé. Le créateur n'y figure pas : il en est propriétaire. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "training_group_coaches",
            joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "coach_user_id", nullable = false)
    private java.util.Set<java.util.UUID> invitedCoachIds = new java.util.LinkedHashSet<>();

    /** Ce coach voit-il ce groupe ? Un groupe de club est visible de tous ses coachs. */
    public boolean isVisibleTo(java.util.UUID coachUserId) {
        if (visibility != GroupVisibility.PRIVATE) {
            return true;
        }
        return coachUserId != null
                && (coachUserId.equals(ownerCoachId) || invitedCoachIds.contains(coachUserId));
    }
}
