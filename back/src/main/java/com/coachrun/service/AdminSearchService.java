package com.coachrun.service;

import com.coachrun.dto.response.AdminSearchResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recherche globale du back-office : une saisie, trois familles de ressources.
 *
 * <p><b>Le geste qu'elle remplace.</b> Ouvrir « Utilisateurs », chercher, ne rien trouver, ouvrir
 * « Athlètes », chercher à nouveau, se souvenir que le nom saisi est peut-être celui du club.
 * Un ticket de support commence presque toujours par une adresse e-mail — jamais par la famille
 * de ressource à laquelle elle appartient.</p>
 *
 * <p><b>Bornée volontairement.</b> Quelques résultats par famille, plus le total. La recherche
 * globale sert à <i>atteindre</i> une fiche ; parcourir et filtrer restent le travail des écrans
 * dédiés, qui savent le faire avec pagination et tri.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSearchService {

    /** Résultats rendus par famille. Au-delà, on renvoie vers l'écran dédié. */
    private static final int LIMIT = 6;

    /** En deçà, toute recherche ramènerait la moitié de la base : on ne cherche pas, on liste. */
    private static final int MIN_QUERY_LENGTH = 2;

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final AthleteRepository athleteRepository;

    public AdminSearchResponse search(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            return new AdminSearchResponse(q, List.of(), 0, List.of(), 0, List.of(), 0);
        }
        Pageable limit = PageRequest.of(0, LIMIT);

        List<User> users = userRepository.quickSearch(q, limit);
        List<Club> clubs = clubRepository.quickSearch(q, limit);
        List<Athlete> athletes = athleteRepository.quickSearch(q, limit);

        // Les totaux passent par les recherches paginées existantes : une requête de comptage
        // dédiée par famille dirait la même chose pour trois requêtes de plus.
        long usersTotal = userRepository.searchAdmin(null, null, null, null, q, PageRequest.of(0, 1))
                .getTotalElements();
        long clubsTotal = clubRepository.searchAdmin(null, q, PageRequest.of(0, 1))
                .getTotalElements();
        long athletesTotal = athleteRepository.searchAdmin(null, null, q, PageRequest.of(0, 1))
                .getTotalElements();

        return new AdminSearchResponse(q,
                users.stream().map(AdminSearchService::toHit).toList(), usersTotal,
                clubs.stream().map(AdminSearchService::toHit).toList(), clubsTotal,
                athletes.stream().map(AdminSearchService::toHit).toList(), athletesTotal);
    }

    private static AdminSearchResponse.Hit toHit(User u) {
        String club = u.getClub() != null ? u.getClub().getName() : null;
        String subtitle = club != null ? u.getEmail() + " · " + club : u.getEmail();
        return new AdminSearchResponse.Hit(u.getId(), u.getFullName(), subtitle,
                u.getRole().name(), "/admin/users/" + u.getId());
    }

    private static AdminSearchResponse.Hit toHit(Club c) {
        return new AdminSearchResponse.Hit(c.getId(), c.getName(), c.getSlug(),
                c.getStatus().name(), "/admin/clubs/" + c.getId());
    }

    private static AdminSearchResponse.Hit toHit(Athlete a) {
        String club = a.getClub() != null ? a.getClub().getName() : null;
        String subtitle = a.getEmail() != null && club != null ? a.getEmail() + " · " + club
                : a.getEmail() != null ? a.getEmail() : club;
        return new AdminSearchResponse.Hit(a.getId(), a.getFirstName() + " " + a.getLastName(),
                subtitle, a.getStatus().name(), "/admin/athletes/" + a.getId() + "/edit");
    }
}
