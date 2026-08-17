package com.coachrun.dto.response;

import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.enums.ProposalStatus;
import com.coachrun.entity.enums.ProposalType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une proposition, telle qu'elle s'affiche. {@code title} et {@code reason} sont écrits par le
 * détecteur et rendus tels quels : deux écrans ne doivent pas raconter différemment la même
 * suggestion.
 *
 * @param athleteName rempli côté coach (file multi-athlètes), nul dans le portail athlète où
 *                    l'identité est évidente
 */
public record ProposalResponse(
        UUID id,
        UUID athleteId,
        String athleteName,
        ProposalType type,
        ProposalStatus status,
        UUID targetId,
        LocalDate targetDate,
        String title,
        String reason,
        boolean requestedByAthlete,
        String payloadJson
) {

    public static ProposalResponse from(AthleteProposal p, String athleteName) {
        return new ProposalResponse(
                p.getId(), p.getAthlete().getId(), athleteName,
                p.getType(), p.getStatus(), p.getTargetId(), p.getTargetDate(),
                p.getTitle(), p.getReason(), p.isRequestedByAthlete(), p.getPayloadJson());
    }
}
