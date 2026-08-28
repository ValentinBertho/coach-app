package com.coachrun.dto.response;

import com.coachrun.entity.ActivityExclusion;
import com.coachrun.entity.enums.ActivitySource;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une sortie écartée pour de bon, telle qu'on la relit dans « sorties masquées ».
 *
 * <p>Le titre et la date sont ceux recopiés à la suppression : la sortie n'existe plus, et sans
 * eux l'écran n'aurait qu'un identifiant Strava à proposer — de quoi renoncer à annuler un
 * masquage faute de reconnaître ce qu'on annule.</p>
 */
public record ActivityExclusionResponse(
        UUID id,
        ActivitySource source,
        String title,
        LocalDate activityDate
) {

    public static ActivityExclusionResponse from(ActivityExclusion e) {
        return new ActivityExclusionResponse(e.getId(), e.getSource(), e.getTitle(),
                e.getActivityDate());
    }
}
