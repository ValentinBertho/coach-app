package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/** L'athlète rejoint par un lien ; le mot est facultatif. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InviteJoinRequest(@Size(max = 2000) String message,
                                /** Déjà suivi : l'athlète change de coach. */
                                boolean switchCoach,
                                /** Transmettre l'historique de ses sorties ; absent = non. */
                                Boolean shareHistory) {

    public boolean sharesHistory() {
        return Boolean.TRUE.equals(shareHistory);
    }
}
