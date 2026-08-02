package com.coachrun.service;

import com.coachrun.dto.request.SessionCategoryRequest;
import com.coachrun.dto.response.SessionCategoryResponse;
import com.coachrun.entity.SessionCategory;
import com.coachrun.entity.enums.CategoryDomain;
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
 * Arbre de catégories unifié des bibliothèques (course · prépa physique · éducatifs), discriminé
 * par {@link CategoryDomain}. CRUD scopé club ; la suppression d'une catégorie détache ses enfants
 * et les items rattachés (FK SET NULL).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionCategoryService {

    private final SessionCategoryRepository categoryRepository;
    private final ClubRepository clubRepository;

    public List<SessionCategoryResponse> list(UUID clubId, CategoryDomain domain) {
        return categoryRepository.findByClubIdAndDomainOrderBySortOrderAscNameAsc(clubId, domain).stream()
                .map(SessionCategoryResponse::from)
                .toList();
    }

    @Transactional
    public SessionCategoryResponse create(UUID clubId, CategoryDomain domain, SessionCategoryRequest req) {
        SessionCategory c = new SessionCategory();
        c.setClub(clubRepository.getReferenceById(clubId));
        c.setDomain(domain);
        apply(clubId, c, req);
        return SessionCategoryResponse.from(categoryRepository.save(c));
    }

    @Transactional
    public SessionCategoryResponse update(UUID clubId, UUID id, SessionCategoryRequest req) {
        SessionCategory c = require(clubId, id);
        if (req.parentId() != null && req.parentId().equals(id)) {
            throw new ConflictException("Une catégorie ne peut pas être son propre parent.");
        }
        // Ranger une catégorie sous l'une de ses propres descendantes détacherait la branche de
        // l'arbre : elle deviendrait invisible partout, sans moyen d'y revenir.
        if (req.parentId() != null && isDescendant(clubId, req.parentId(), id)) {
            throw new ConflictException("Une catégorie ne peut pas être rangée sous l'une de ses sous-catégories.");
        }
        apply(clubId, c, req);
        return SessionCategoryResponse.from(c);
    }

    @Transactional
    public void delete(UUID clubId, UUID id) {
        categoryRepository.delete(require(clubId, id));
    }

    /** Catégorie d'un domaine donné (validation d'assignation à un exercice / éducatif). */
    public SessionCategory requireForDomain(UUID clubId, UUID id, CategoryDomain domain) {
        return categoryRepository.findByIdAndClubIdAndDomain(id, clubId, domain)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
    }

    private void apply(UUID clubId, SessionCategory c, SessionCategoryRequest req) {
        c.setName(req.name().trim());
        c.setDiscipline(req.discipline());
        if (req.sortOrder() != null) {
            c.setSortOrder(req.sortOrder());
        }
        if (req.parentId() != null) {
            // Les arbres sont listés par domaine : ranger une catégorie course sous une catégorie
            // de prépa physique la ferait disparaître des deux écrans à la fois.
            SessionCategory parent = require(clubId, req.parentId());
            if (parent.getDomain() != c.getDomain()) {
                throw new ConflictException("La catégorie parente appartient à une autre bibliothèque.");
            }
            c.setParent(parent);
        } else {
            c.setParent(null);
        }
    }

    /** {@code candidate} descend-il de {@code ancestor} ? (remontée des parents, bornée). */
    private boolean isDescendant(UUID clubId, UUID candidate, UUID ancestor) {
        SessionCategory cur = require(clubId, candidate);
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        while (cur != null && seen.add(cur.getId())) {
            if (cur.getId().equals(ancestor)) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private SessionCategory require(UUID clubId, UUID id) {
        return categoryRepository.findByIdAndClubId(id, clubId)
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable."));
    }
}
