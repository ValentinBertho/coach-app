package com.coachrun.service;

import com.coachrun.dto.request.ClubRequest;
import com.coachrun.dto.response.ClubResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/** Administration des clubs (PLATFORM_ADMIN). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminClubService {

    private final ClubRepository clubRepository;

    public PageResponse<ClubResponse> list(String q, Pageable pageable) {
        var page = StringUtils.hasText(q)
                ? clubRepository.findByNameContainingIgnoreCase(q.trim(), pageable)
                : clubRepository.findAll(pageable);
        return PageResponse.from(page, ClubResponse::from);
    }

    public ClubResponse get(UUID id) {
        return ClubResponse.from(require(id));
    }

    @Transactional
    public ClubResponse create(ClubRequest request) {
        Club club = new Club();
        club.setName(request.name());
        club.setSlug(uniqueSlug(request.name()));
        club.setStatus(request.status() != null ? request.status() : ClubStatus.ACTIVE);
        return ClubResponse.from(clubRepository.save(club));
    }

    @Transactional
    public ClubResponse update(UUID id, ClubRequest request) {
        Club club = require(id);
        club.setName(request.name());
        if (request.status() != null) {
            club.setStatus(request.status());
        }
        return ClubResponse.from(club);
    }

    @Transactional
    public void delete(UUID id) {
        clubRepository.delete(require(id));
    }

    private Club require(UUID id) {
        return clubRepository.findById(id).orElseThrow(() -> new NotFoundException("Club introuvable."));
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.slugify(name);
        String slug = base;
        int i = 1;
        while (clubRepository.existsBySlug(slug)) {
            slug = base + "-" + (++i);
        }
        return slug;
    }
}
