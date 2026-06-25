package com.coachrun.service;

import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.Optional;

/**
 * Déclencheur centralisé de notifications email (cf. Techno.md §3). Désactivé par défaut
 * (MAIL_ENABLED=false) → simple log. Les échecs d'envoi n'interrompent jamais le métier.
 * Jamais de donnée de santé dans les emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ResendMailClient mailClient;
    private final UserRepository userRepository;
    private final PushNotificationService pushService;
    private final CoachAthleteRelationRepository relationRepository;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Séance planifiée → notifie l'athlète (email + push). */
    public void notifyWorkoutPlanned(Workout workout) {
        userRepository.findByAthleteId(workout.getAthlete().getId())
                .ifPresent(u -> pushService.sendToUser(u.getId(), "Nouvelle séance",
                        workout.getTitle() + " — " + workout.getScheduledDate(),
                        frontendUrl + "/athlete/today"));
        String email = workout.getAthlete().getEmail();
        if (email == null) {
            return;
        }
        String subject = "Nouvelle séance planifiée";
        String html = "<p>Bonjour " + esc(workout.getAthlete().getFirstName()) + ",</p>"
                + "<p>Votre coach a planifié une nouvelle séance : <strong>" + esc(workout.getTitle())
                + "</strong> le " + workout.getScheduledDate() + ".</p>"
                + cta("Voir ma séance", frontendUrl + "/athlete/today");
        send(email, subject, html);
    }

    /** Feedback athlète → notifie le coach <strong>référent</strong> de l'athlète (repli : head coach). */
    public void notifyAthleteFeedback(Workout workout) {
        Optional<User> coach = coachToNotify(workout);
        coach.ifPresent(c -> pushService.sendToUser(c.getId(),
                "Séance mise à jour",
                workout.getAthlete().getFirstName() + " — " + workout.getStatus(),
                frontendUrl + "/app"));
        coach.map(User::getEmail)
                .ifPresent(coachEmail -> {
                    String athlete = esc(workout.getAthlete().getFirstName() + " "
                            + workout.getAthlete().getLastName());
                    String subject = athlete + " a renseigné une séance";
                    String html = "<p>" + athlete + " a mis à jour la séance <strong>"
                            + esc(workout.getTitle()) + "</strong> (" + workout.getStatus() + ").</p>"
                            + cta("Ouvrir Darilab", frontendUrl + "/app");
                    send(coachEmail, subject, html);
                });
    }

    /**
     * Coach à notifier pour un athlète : son coach <strong>référent</strong> (relation active),
     * sinon repli sur le head coach du club. Évite qu'en multi-coach un retour parte au mauvais coach.
     */
    private Optional<User> coachToNotify(Workout workout) {
        Optional<User> referent = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(workout.getAthlete().getId())
                .map(CoachAthleteRelation::getCoach);
        if (referent.isPresent()) {
            return referent;
        }
        return userRepository.findFirstByClubIdAndRole(workout.getClub().getId(), UserRole.HEAD_COACH);
    }

    /** Rappel J-1 → notifie l'athlète d'une séance prévue le lendemain. */
    public void notifyWorkoutReminder(Workout workout) {
        String email = workout.getAthlete().getEmail();
        if (email == null) {
            return;
        }
        send(email, "Rappel : séance demain",
                "<p>Bonjour " + esc(workout.getAthlete().getFirstName()) + ",</p>"
                        + "<p>Rappel : <strong>" + esc(workout.getTitle()) + "</strong> est prévue demain.</p>"
                        + cta("Voir ma séance", frontendUrl + "/athlete/today"));
    }

    private void send(String to, String subject, String html) {
        if (!enabled) {
            log.info("[mail désactivé] -> {} : {}", to, subject);
            return;
        }
        try {
            mailClient.send(to, subject, html);
        } catch (RuntimeException ex) {
            // Idempotence/robustesse : un échec d'email ne casse pas l'action métier.
            log.warn("Échec d'envoi d'email à {} : {}", to, ex.getMessage());
        }
    }

    private String cta(String label, String url) {
        return "<p><a href=\"" + url + "\">" + esc(label) + "</a></p>";
    }

    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
