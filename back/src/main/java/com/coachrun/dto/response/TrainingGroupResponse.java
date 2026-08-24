package com.coachrun.dto.response;

import com.coachrun.entity.TrainingGroup;
import com.coachrun.entity.enums.GroupVisibility;

import java.util.List;
import java.util.UUID;

/**
 * Un groupe d'entraînement.
 *
 * @param visibility     CLUB (tous les coachs) ou PRIVATE (son créateur et ses invités)
 * @param ownerCoachId   le créateur, quand il est connu
 * @param invitedCoachIds les coachs conviés à un groupe privé
 * @param canManage      cette personne peut-elle le renommer, l'ouvrir ou le fermer ?
 */
public record TrainingGroupResponse(
        UUID id,
        String name,
        long athleteCount,
        GroupVisibility visibility,
        UUID ownerCoachId,
        List<UUID> invitedCoachIds,
        boolean canManage) {

    public static TrainingGroupResponse of(TrainingGroup g, long athleteCount, UUID viewerId) {
        return new TrainingGroupResponse(
                g.getId(), g.getName(), athleteCount, g.getVisibility(), g.getOwnerCoachId(),
                List.copyOf(g.getInvitedCoachIds()),
                // Un groupe sans créateur connu (antérieur à la visibilité) appartient au club :
                // le fermer sur personne serait le rendre inutilisable.
                g.getOwnerCoachId() == null || g.getOwnerCoachId().equals(viewerId));
    }
}
