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

    /**
     * Commentaire du coach sur une séance réalisée → notifie l'athlète (in-app + push + email).
     * Le corps ne reprend pas le commentaire : il peut contenir des éléments de santé, et le
     * centre de notifications n'est pas le bon canal pour ça — on renvoie vers la séance.
     */
    public void notifyCoachComment(Workout workout) {
        User athleteUser = userRepository.findByAthleteId(workout.getAthlete().getId()).orElse(null);
        if (athleteUser != null) {
            record(athleteUser.getId(), "COACH_COMMENT", "Retour de votre coach",
                    workout.getTitle(), "/athlete/history");
            if (athleteUser.isNotifyPushEnabled()) {
                pushService.sendToUser(athleteUser.getId(), "Retour de votre coach",
                        workout.getTitle(), frontendUrl + "/athlete/history");
            }
        }
        String email = workout.getAthlete().getEmail();
        if (email == null || (athleteUser != null && !athleteUser.isNotifyEmailEnabled())) {
            return;
        }
        String html = "<p>Bonjour " + esc(workout.getAthlete().getFirstName()) + ",</p>"
                + "<p>Votre coach a commenté la séance <strong>" + esc(workout.getTitle())
                + "</strong>.</p>"
                + cta("Lire le retour", frontendUrl + "/athlete/history");
        send(email, "Votre coach a commenté une séance", html);
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

    /**
     * Débriefing de séance : « Ta séance est finie ? », 2 h après l'heure habituelle, avec le
     * RPE en actions rapides.
     *
     * <p>Un retour non rempli est un bug produit, pas une négligence de l'athlète : le rappel
     * J-1 annonce une séance à venir, celui-ci récupère le ressenti pendant qu'il est encore
     * frais. Les trois crans proposés (3 / 6 / 8) couvrent l'essentiel des réponses réelles et
     * mènent à une feuille pré-remplie — l'athlète confirme fatigue et douleur, il ne saisit
     * pas tout depuis zéro.</p>
     *
     * <p>Push uniquement : pas d'e-mail (le canal est trop lent pour un ressenti à chaud) et
     * pas de trace au centre de notifications (le lendemain, elle serait périmée).</p>
     *
     * @param feedbackPath chemin front portant déjà un paramètre de requête (les actions y
     *                     ajoutent {@code &rpe=…}), ex. {@code /athlete/today?feedback=<id>}
     */
    public void notifySessionDebrief(User athleteUser, String sessionTitle, String feedbackPath) {
        if (athleteUser == null || !athleteUser.isNotifyPushEnabled()) {
            return;
        }
        List<PushNotificationService.QuickAction> actions = List.of(
                quickAction(3, "Facile", feedbackPath),
                quickAction(6, "Moyen", feedbackPath),
                quickAction(8, "Dur", feedbackPath));
        pushService.sendToUser(athleteUser.getId(), "Ta séance est finie ?",
                sessionTitle + " — note ton ressenti en un tap.",
                frontendUrl + feedbackPath, actions);
    }

    private PushNotificationService.QuickAction quickAction(int rpe, String label, String feedbackPath) {
        return new PushNotificationService.QuickAction("rpe-" + rpe, label + " (" + rpe + ")",
                frontendUrl + feedbackPath + "&rpe=" + rpe);
    }

    /** Vérification d'e-mail à l'inscription : e-mail avec le lien de confirmation. */
    public void notifyEmailVerification(String email, String fullName, String url) {
        if (email == null) {
            return;
        }
        String html = "<p>Bonjour " + esc(fullName) + ",</p>"
                + "<p>Bienvenue sur Darilab. Confirmez votre adresse e-mail pour sécuriser votre compte.</p>"
                + cta("Confirmer mon e-mail", url)
                + "<p>Ce lien expire dans 7 jours.</p>";
        send(email, "Confirmez votre adresse e-mail Darilab", html);
    }

    /** Réinitialisation de mot de passe : e-mail avec le lien de redéfinition. */
    public void notifyPasswordReset(String email, String fullName, String url) {
        if (email == null) {
            return;
        }
        String html = "<p>Bonjour " + esc(fullName) + ",</p>"
                + "<p>Vous avez demandé à réinitialiser votre mot de passe Darilab.</p>"
                + cta("Choisir un nouveau mot de passe", url)
                + "<p>Ce lien expire dans 2 heures. Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.</p>";
        send(email, "Réinitialisation de votre mot de passe Darilab", html);
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
