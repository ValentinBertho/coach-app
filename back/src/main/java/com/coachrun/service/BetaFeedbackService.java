package com.coachrun.service;

import com.coachrun.dto.request.BetaFeedbackRequest;
import com.coachrun.dto.response.BetaFeedbackResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.BetaFeedback;
import com.coachrun.entity.enums.FeedbackKind;
import com.coachrun.entity.enums.FeedbackStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.BetaFeedbackRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Retours de bêta envoyés depuis l'application.
 *
 * <p>Le canal existant — un lien {@code mailto:} pré-rempli dans la navigation — supposait un
 * client mail configuré (rarement le cas sur une PWA mobile) et ne laissait aucune trace : pas de
 * file à traiter, pas de statut, pas de moyen de recouper un retour avec les erreurs remontées
 * par Sentry. Une bêta dont les retours arrivent en vrac dans une boîte de réception ne produit
 * pas la décision qu'elle est censée produire.</p>
 *
 * <p>Le contexte technique (page, version, navigateur, identifiant de corrélation) est enregistré
 * avec le message. C'est ce qu'on redemande sinon systématiquement au premier échange, et le
 * moment où l'utilisateur écrit est le seul où on l'a sous la main.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BetaFeedbackService {

    /** Garde-fou de longueur, aligné sur la colonne : on tronque plutôt que de refuser. */
    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_PAGE = 512;
    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_CORRELATION = 64;
    private static final int MAX_VERSION = 64;

    private final BetaFeedbackRepository repository;
    private final UserRepository userRepository;

    /**
     * Enregistre un retour. Le {@code userAgent} vient de l'en-tête de la requête et non du
     * corps : le client n'a pas à le fournir, et c'est une donnée que le serveur a déjà.
     */
    @Transactional
    public void submit(UUID userId, BetaFeedbackRequest request, String userAgent) {
        BetaFeedback feedback = new BetaFeedback();
        feedback.setUserId(userId);
        feedback.setKind(request.kind() != null ? request.kind() : FeedbackKind.BUG);
        feedback.setMessage(truncate(request.message(), MAX_MESSAGE));
        feedback.setPage(truncate(request.page(), MAX_PAGE));
        feedback.setAppVersion(truncate(request.appVersion(), MAX_VERSION));
        feedback.setUserAgent(truncate(userAgent, MAX_USER_AGENT));
        feedback.setCorrelationId(truncate(request.correlationId(), MAX_CORRELATION));
        feedback.setStatus(FeedbackStatus.NEW);
        repository.save(feedback);

        // Journalisé au niveau INFO sans le corps du message : le corps est libre et peut contenir
        // ce que l'utilisateur veut, y compris une donnée personnelle — il n'a rien à faire dans
        // des journaux dont la rétention n'est pas maîtrisée. Il se lit dans le back-office.
        log.info("[bêta] Retour reçu ({}) depuis {} — version {}",
                feedback.getKind(), feedback.getPage(), feedback.getAppVersion());
    }

    /** File de traitement du back-office plateforme. */
    public PageResponse<BetaFeedbackResponse> list(FeedbackStatus status, Pageable pageable) {
        var page = status == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        Map<UUID, String> authors = resolveAuthors(page.getContent());
        return PageResponse.from(page, f -> BetaFeedbackResponse.from(f, authors.get(f.getUserId())));
    }

    /** Compteur de la pastille « retours à lire » du tableau de bord admin. */
    public long countNew() {
        return repository.countByStatus(FeedbackStatus.NEW);
    }

    @Transactional
    public void updateStatus(UUID feedbackId, FeedbackStatus status) {
        BetaFeedback feedback = repository.findById(feedbackId)
                .orElseThrow(() -> new NotFoundException("Retour introuvable."));
        feedback.setStatus(status);
    }

    /**
     * Nom des auteurs, en une requête pour la page entière plutôt qu'une par ligne. Un auteur
     * dont le compte a été supprimé reste absent : c'est voulu, le retour lui survit.
     */
    private Map<UUID, String> resolveAuthors(java.util.List<BetaFeedback> feedbacks) {
        var ids = feedbacks.stream()
                .map(BetaFeedback::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.coachrun.entity.User::getId,
                        com.coachrun.entity.User::getFullName));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
