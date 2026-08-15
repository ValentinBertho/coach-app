package com.coachrun.service;

import com.coachrun.entity.MailLog;
import com.coachrun.entity.enums.MailKind;
import com.coachrun.integration.MailTemplate.Audience;
import com.coachrun.repository.MailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Écriture du journal des e-mails.
 *
 * <p>Séparé du service de notification pour une raison de transaction : l'envoi part <b>après le
 * commit</b> de l'action métier (pour ne pas retenir une connexion pendant un appel HTTP), donc
 * l'écriture de la trace se fait hors de toute transaction ouverte. {@code REQUIRES_NEW} la rend
 * explicite plutôt que de dépendre du contexte de l'appelant.</p>
 *
 * <p><b>Le journal ne casse jamais un envoi.</b> Une trace perdue est un trou dans une statistique ;
 * une exception ici ferait échouer une réinitialisation de mot de passe déjà partie. L'ordre des
 * priorités ne se discute pas.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailLogService {

    private final MailLogRepository repository;

    /**
     * Trace une tentative d'envoi. {@code error} nul vaut succès.
     *
     * <p>Le sujet et le message d'erreur sont tronqués à la taille des colonnes : un sujet est
     * construit à partir de données saisies (un nom d'athlète, un nom de course) et rien ne
     * garantit sa longueur.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String recipient, String subject, MailKind kind, Audience audience,
                       String error) {
        try {
            MailLog entry = new MailLog();
            entry.setRecipient(truncate(recipient, 255));
            entry.setSubject(truncate(subject, 255));
            entry.setKind(kind == null ? MailKind.OTHER : kind);
            entry.setAudience(audience == null ? null : audience.name());
            entry.setSent(error == null);
            entry.setErrorMessage(truncate(error, 500));
            entry.setSentAt(Instant.now());
            repository.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Journal e-mail non écrit ({} → {}) : {}", kind, recipient, ex.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
