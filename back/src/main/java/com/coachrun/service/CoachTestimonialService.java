package com.coachrun.service;

import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.CoachTestimonial;
import com.coachrun.entity.User;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.CoachTestimonialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les témoignages : l'athlète écrit sur son coach actuel, le coach choisit ce qu'il publie.
 *
 * <p>Reprendre son texte le dépublie : le coach a validé des mots, pas une place sur sa page.</p>
 */
@Service
@RequiredArgsConstructor
public class CoachTestimonialService {

    public static final int MAX_LENGTH = 600;

    private final CoachTestimonialRepository repository;
    private final AthleteAccountRepository accountRepository;
    private final CoachingRelationService relations;
    private final NotificationService notifications;

    /** Ce que voit l'athlète : son texte sur son coach actuel, publié ou non. */
    public record Mine(String coachName, String body, boolean published) {
    }

    /** Ce que voit le coach. */
    public record ForCoach(UUID id, String authorName, String body, boolean published, Instant updatedAt) {
    }

    /** Ce que voit le public. */
    public record Published(String authorName, String body, Instant date) {
    }

    @Transactional(readOnly = true)
    public Optional<Mine> mine(UUID userId) {
        CoachAthleteRelation relation = requireCoach(userId);
        AthleteAccount account = requireAccount(userId);
        return repository.findByCoachIdAndAthleteAccountId(relation.getCoach().getId(), account.getId())
                .map(t -> new Mine(relation.getCoach().getFullName(), t.getBody(), t.isPublished()));
    }

    @Transactional
    public Mine write(UUID userId, String body) {
        String text = body == null ? "" : body.trim();
        if (text.length() < 20 || text.length() > MAX_LENGTH) {
            throw new ConflictException("Entre 20 et " + MAX_LENGTH + " caractères : quelques phrases suffisent.");
        }
        CoachAthleteRelation relation = requireCoach(userId);
        AthleteAccount account = requireAccount(userId);
        User coach = relation.getCoach();
        CoachTestimonial t = repository.findByCoachIdAndAthleteAccountId(coach.getId(), account.getId())
                .orElseGet(CoachTestimonial::new);
        t.setCoach(coach);
        t.setAthleteAccount(account);
        t.setAuthorName(author(account));
        t.setBody(text);
        t.setPublished(false);
        repository.save(t);
        notifications.notifyTestimonialWritten(coach, t.getAuthorName());
        return new Mine(coach.getFullName(), text, false);
    }

    @Transactional
    public void withdraw(UUID userId) {
        CoachAthleteRelation relation = requireCoach(userId);
        AthleteAccount account = requireAccount(userId);
        repository.findByCoachIdAndAthleteAccountId(relation.getCoach().getId(), account.getId())
                .ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public List<ForCoach> received(UUID coachId) {
        return repository.findByCoachIdOrderByCreatedAtDesc(coachId).stream()
                .map(t -> new ForCoach(t.getId(), t.getAuthorName(), t.getBody(), t.isPublished(), t.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ForCoach publish(UUID coachId, UUID id, boolean published) {
        CoachTestimonial t = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Témoignage introuvable."));
        if (!t.getCoach().getId().equals(coachId)) {
            throw new ForbiddenException("Ce témoignage ne vous est pas adressé.");
        }
        t.setPublished(published);
        return new ForCoach(t.getId(), t.getAuthorName(), t.getBody(), t.isPublished(), t.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<Published> published(UUID coachId) {
        return repository.findTop6ByCoachIdAndPublishedTrueOrderByUpdatedAtDesc(coachId).stream()
                .map(t -> new Published(t.getAuthorName(), t.getBody(), t.getUpdatedAt()))
                .toList();
    }

    private CoachAthleteRelation requireCoach(UUID userId) {
        return relations.current(userId)
                .orElseThrow(() -> new ConflictException("Un témoignage s'écrit pour le coach qui vous suit."));
    }

    private AthleteAccount requireAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Compte athlète introuvable."));
    }

    static String author(AthleteAccount a) {
        String first = StringUtils.hasText(a.getFirstName()) ? a.getFirstName().trim() : "Un athlète";
        String last = StringUtils.hasText(a.getLastName()) ? " " + a.getLastName().trim().charAt(0) + "." : "";
        return first + last;
    }
}
