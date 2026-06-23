package com.coachrun.service;

import com.coachrun.dto.request.SessionCategoryRequest;
import com.coachrun.dto.response.SessionCategoryResponse;
import com.coachrun.entity.SessionCategory;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.SessionCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Arbre de catégories de la bibliothèque de séances course (cf. DARI Lab — « s-library »).
 * CRUD scopé club ; la suppression d'une catégorie détache ses enfants et séances (FK SET NULL).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionCategoryService {

    private final SessionCategoryRepository categoryRepository;
    private final ClubRepository clubRepository;

    public List<SessionCategoryResponse> list(UUID clubId) {
        return categoryRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId).stream()
                .map(SessionCategoryResponse::from)
                .toList();
    }

    @Transactional
    public SessionCategoryResponse create(UUID clubId, SessionCategoryRequest req) {
        SessionCategory c = new SessionCategory();
        c.setClub(clubRepository.getReferenceById(clubId));
        apply(clubId, c, req);
        return SessionCategoryResponse.from(categoryRepository.save(c));
    }

    @Transactional
    public SessionCategoryResponse update(UUID clubId, UUID id, SessionCategoryRequest req) {
        SessionCategory c = require(clubId, id);
        if (req.parentId() != null && req.parentId().equals(id)) {
            throw new ConflictException("Une catégorie ne peut pas être son propre parent.");
        }
        apply(clubId, c, req);
        return SessionCategoryResponse.from(c);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        categoryRepository.delete(require(clubId, id));
    }

    private void apply(UUID clubId, SessionCategory c, SessionCategoryRequest req) {
        c.setName(req.name().trim());
        c.setDiscipline(req.discipline());
        if (req.sortOrder() != null) {
            c.setSortOrder(req.sortOrder());
        }
        if (req.parentId() != null) {
            c.setParent(require(clubId, req.parentId()));
        } else {
            c.setParent(null);
        }
    }

    private SessionCategory require(UUID clubId, UUID id) {
        return categoryRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
    }
}
