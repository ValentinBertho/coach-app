package com.coachrun.service;

import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.Notification;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.NotificationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final NotificationRepository notificationRepository;
    private final NotificationStreamService streamService;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Séance planifiée → notifie l'athlète (in-app + email + push). */
    public void notifyWorkoutPlanned(Workout workout) {
        User athleteUser = userRepository.findByAthleteId(workout.getAthlete().getId()).orElse(null);
        if (athleteUser != null) {
            record(athleteUser.getId(), "WORKOUT_PLANNED", "Nouvelle séance",
                    workout.getTitle() + " — " + workout.getScheduledDate(), "/athlete/today");
            if (athleteUser.isNotifyPushEnabled()) {
                pushService.sendToUser(athleteUser.getId(), "Nouvelle séance",
                        workout.getTitle() + " — " + workout.getScheduledDate(),
                        frontendUrl + "/athlete/today");
            }
        }
        String email = workout.getAthlete().getEmail();
        if (email == null || (athleteUser != null && !athleteUser.isNotifyEmailEnabled())) {
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
        coachToNotify(workout).ifPresent(c -> {
            record(c.getId(), "ATHLETE_FEEDBACK", "Séance mise à jour",
                    workout.getAthlete().getFirstName() + " " + workout.getAthlete().getLastName()
                            + " — " + workout.getStatus(), "/app");
            if (c.isNotifyPushEnabled()) {
                pushService.sendToUser(c.getId(), "Séance mise à jour",
                        workout.getAthlete().getFirstName() + " — " + workout.getStatus(),
                        frontendUrl + "/app");
            }
            if (c.getEmail() != null && c.isNotifyEmailEnabled()) {
                String athlete = esc(workout.getAthlete().getFirstName() + " "
                        + workout.getAthlete().getLastName());
                String subject = athlete + " a renseigné une séance";
                String html = "<p>" + athlete + " a mis à jour la séance <strong>"
                        + esc(workout.getTitle()) + "</strong> (" + workout.getStatus() + ").</p>"
                        + cta("Ouvrir Darilab", frontendUrl + "/app");
                send(c.getEmail(), subject, html);
            }
        });
    }

    private Optional<User> coachToNotify(Workout workout) {
        return referentCoach(workout.getAthlete().getId(), workout.getClub().getId());
    }

    /**
     * Coach responsable d'un athlète : son coach <strong>référent</strong> (relation active),
     * sinon repli sur le head coach du club. Évite qu'en multi-coach une notif parte au mauvais coach.
     */
    /**
     * Persiste une notification in-app pour un utilisateur (centre de notifications). Best-effort :
     * un échec n'interrompt jamais l'action métier. {@code body} ne contient aucune donnée de santé.
     */
    public void record(UUID userId, String type, String title, String body, String link) {
        if (userId == null) {
            return;
        }
        try {
            User u = userRepository.findById(userId).orElse(null);
            if (u == null) {
                return;
            }
            Notification n = new Notification();
            n.setUser(u);
            n.setType(type);
            n.setTitle(title);
            n.setBody(body);
            n.setLink(link);
            notificationRepository.save(n);
            streamService.publishUnread(userId, notificationRepository.countByUserIdAndReadAtIsNull(userId));
        } catch (RuntimeException ex) {
            log.warn("Échec d'enregistrement d'une notification in-app ({}): {}", type, ex.getMessage());
        }
    }

    public Optional<User> referentCoach(UUID athleteId, UUID clubId) {
        Optional<User> referent = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athleteId)
                .map(CoachAthleteRelation::getCoach);
        if (referent.isPresent()) {
            return referent;
        }
        return clubId == null ? Optional.empty()
                : userRepository.findFirstByClubIdAndRole(clubId, UserRole.HEAD_COACH);
    }

    /**
     * Digest d'alertes pour un coach (push + email). Ne contient <strong>aucune donnée de santé</strong> :
     * uniquement le nom de l'athlète et une catégorie générique, avec un lien vers le tableau de bord.
     */
    public void notifyCoachAlertDigest(User coach, List<CoachAlertResponse> alerts) {
        if (coach == null || alerts == null || alerts.isEmpty()) {
            return;
        }
        // Une ligne par athlète (la plus grave d'abord, déjà triée).
        Map<UUID, CoachAlertResponse> perAthlete = new LinkedHashMap<>();
        for (CoachAlertResponse a : alerts) {
            perAthlete.putIfAbsent(a.athleteId(), a);
        }
        int count = perAthlete.size();

        record(coach.getId(), "COACH_ALERTS", "Alertes à traiter",
                count + (count > 1 ? " athlètes nécessitent votre attention" : " athlète nécessite votre attention"),
                "/app");
        if (coach.isNotifyPushEnabled()) {
            pushService.sendToUser(coach.getId(), "Alertes à traiter",
                    count + (count > 1 ? " athlètes à surveiller" : " athlète à surveiller"),
                    frontendUrl + "/app");
        }

        if (coach.getEmail() == null || !coach.isNotifyEmailEnabled()) {
            return;
        }
        StringBuilder items = new StringBuilder();
        perAthlete.values().stream().limit(15).forEach(a -> items
                .append("<li>").append(esc(a.athleteName())).append(" — ")
                .append(esc(category(a.type()))).append("</li>"));
        String subject = count + (count > 1 ? " alertes à traiter" : " alerte à traiter") + " sur Darilab";
        String html = "<p>Bonjour " + esc(coach.getFullName()) + ",</p>"
                + "<p>" + count + (count > 1 ? " athlètes nécessitent" : " athlète nécessite")
                + " votre attention :</p><ul>" + items + "</ul>"
                + cta("Ouvrir le tableau de bord", frontendUrl + "/app");
        send(coach.getEmail(), subject, html);
    }

    /** Catégorie générique (sans détail de santé) d'une alerte, pour l'email. */
    private String category(String type) {
        return switch (type) {
            case "PAIN" -> "à surveiller";
            case "ACWR_HIGH", "ACWR_LOW", "MONOTONY" -> "charge à surveiller";
            case "MISSED" -> "séances manquées";
            case "SILENCE" -> "sans retour récent";
            default -> "à surveiller";
        };
    }

    /** Rappel J-1 → notifie l'athlète d'une séance prévue le lendemain. */
    public void notifyWorkoutReminder(Workout workout) {
        String email = workout.getAthlete().getEmail();
        if (email == null) {
            return;
        }
        boolean emailMuted = userRepository.findByAthleteId(workout.getAthlete().getId())
                .map(u -> !u.isNotifyEmailEnabled()).orElse(false);
        if (emailMuted) {
            return;
        }
        send(email, "Rappel : séance demain",
                "<p>Bonjour " + esc(workout.getAthlete().getFirstName()) + ",</p>"
                        + "<p>Rappel : <strong>" + esc(workout.getTitle()) + "</strong> est prévue demain.</p>"
                        + cta("Voir ma séance", frontendUrl + "/athlete/today"));
    }

    /** Invitation d'un coach au club : e-mail avec le lien d'acceptation (création de compte). */
    public void notifyCoachInvitation(String email, String fullName, String clubName, String url) {
        if (email == null) {
            return;
        }
        String subject = "Invitation à rejoindre " + clubName + " sur Darilab";
        String html = "<p>Bonjour " + esc(fullName) + ",</p>"
                + "<p>Vous êtes invité·e à rejoindre le club <strong>" + esc(clubName)
                + "</strong> en tant que coach sur Darilab.</p>"
                + cta("Accepter l'invitation et créer mon mot de passe", url)
                + "<p>Ce lien expire dans 14 jours.</p>";
        send(email, subject, html);
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
