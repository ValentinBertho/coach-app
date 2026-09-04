package com.coachrun.service;

import com.coachrun.dto.response.MyClubResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les espaces de travail d'un coach.
 *
 * <p>Le club principal d'abord — c'est celui où il a été créé, et le défaut naturel — puis ses
 * clubs additionnels, dans l'ordre où ils ont été rejoints. Le rôle accompagne chaque espace :
 * propriétaire chez soi, assistant ailleurs, et l'interface n'a pas à le deviner.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyClubsService {

    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;

    public List<MyClubResponse> myClubs(UUID coachId) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable."));

        // Le rôle par club, en une lecture : l'afficher demandait sinon une requête par espace.
        Map<UUID, ClubRole> roles = new LinkedHashMap<>();
        for (ClubMember m : clubMemberRepository.findByCoachIdAndActiveTrue(coachId)) {
            roles.put(m.getClub().getId(), m.getClubRole());
        }

        // LinkedHashMap : le club principal en tête, et pas de doublon si le coach en est aussi
        // membre déclaré — ce qui est le cas normal depuis le backfill.
        Map<UUID, Club> clubs = new LinkedHashMap<>();
        if (coach.getClub() != null) {
            clubs.put(coach.getClub().getId(), coach.getClub());
        }
        coach.getAdditionalClubs().forEach(c -> clubs.putIfAbsent(c.getId(), c));

        UUID primaryId = coach.getClub() != null ? coach.getClub().getId() : null;
        List<MyClubResponse> result = new ArrayList<>();
        clubs.forEach((id, club) -> {
            ClubRole role = roles.get(id);
            result.add(new MyClubResponse(id, club.getName(), id.equals(primaryId), role,
                    MyClubResponse.label(role), club.isSoloPractice()));
        });
        return result;
    }
}
