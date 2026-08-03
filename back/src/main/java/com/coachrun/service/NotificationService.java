package com.coachrun.service;

import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.Notification;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.integration.MailTemplate;
import com.coachrun.integration.MailTemplate.Audience;
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
 * Déclencheur centralisé de notifications (cf. Techno.md §3). Les échecs d'envoi n'interrompent
 * jamais le métier, et aucune donnée de santé ne sort par e-mail.
 *
 * <h2>Répartition des canaux</h2>
 * Trois canaux, et un seul critère pour choisir : <strong>l'e-mail est réservé à ce qui ne peut
 * pas passer ailleurs</strong> — parce que le destinataire n'est pas encore connecté, ou parce
 * que le message porte un lien à usage unique.
 *
 * <table>
 *   <tr><th>Nature</th><th>Canal</th><th>Exemples</th></tr>
 *   <tr><td><b>Transactionnel</b> (le compte n'existe pas encore, ou le lien est la seule voie)</td>
 *       <td>e-mail</td>
 *       <td>vérification d'adresse, réinitialisation de mot de passe, invitation athlète,
 *           invitation coach</td></tr>
 *   <tr><td><b>Routine</b> (l'utilisateur a déjà un compte et l'app installée)</td>
 *       <td>in-app + push</td>
 *       <td>séance planifiée, commentaire du coach, retour d'un athlète, rappel de séance</td></tr>
 *   <tr><td><b>Récapitulatif</b> (une fois par jour, jamais par événement)</td>
 *       <td>push + e-mail</td>
 *       <td>digest d'alertes du coach (7 h), indisponibilité déclarée</td></tr>
 * </table>
 *
 * <p><strong>Pourquoi.</strong> Chaque séance planifiée, chaque rappel J-1 et chaque retour
 * d'athlète partaient auparavant en e-mail, un par événement. Pour 30 coachs et 240 athlètes,
 * cela représente de l'ordre de 11 000 e-mails par mois, contre 3 000/mois et 100/jour sur le
 * plan Resend utilisé : le plafond journalier tombe dès le premier jour — et il emporte avec lui
 * les réinitialisations de mot de passe et les invitations, c'est-à-dire précisément les envois
 * qu'on ne peut pas perdre. Basculer la routine en push ramène le volume à quelques centaines
 * par mois, et gagne en réactivité au passage.</p>
 *
 * <p><strong>Repli.</strong> Tout le monde n'accepte pas les notifications système. Le rappel de
 * séance — le seul dont l'absence se remarque vraiment — retombe sur l'e-mail quand l'athlète
 * n'a aucun appareil abonné ({@link PushNotificationService#canReach}). Les autres notifications
 * de routine restent consultables dans le centre de notifications, qui est toujours actif.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ResendMailClient mailClient;
    private final MailTemplate mailTemplate;
    private final UserRepository userRepository;
    private final PushNotificationService pushService;
    private final CoachAthleteRelationRepository relationRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationStreamService streamService;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Séance planifiée → notifie l'athlète, <strong>in-app + push, sans e-mail</strong>.
     *
     * <p>C'est le geste quotidien du coach : déposer cinq séances depuis la bibliothèque
     * envoyait cinq e-mails à l'athlète dans la minute. La séance apparaît de toute façon dans
     * son agenda et dans « Aujourd'hui » ; l'e-mail n'apportait qu'un doublon, au prix du plus
     * gros poste de consommation du quota d'envoi.</p>
     */
    public void notifyWorkoutPlanned(Workout workout) {
        User athleteUser = userRepository.findByAthleteId(workout.getAthlete().getId()).orElse(null);
        if (athleteUser == null) {
            return;
        }
        record(athleteUser.getId(), "WORKOUT_PLANNED", "Nouvelle séance",
                workout.getTitle() + " — " + workout.getScheduledDate(), "/athlete/today");
        if (athleteUser.isNotifyPushEnabled()) {
            pushService.sendToUser(athleteUser.getId(), "Nouvelle séance",
                    workout.getTitle() + " — " + workout.getScheduledDate(),
                    frontendUrl + "/athlete/today");
        }
    }

    /**
     * Commentaire du coach sur une séance réalisée → notifie l'athlète,
     * <strong>in-app + push, sans e-mail</strong>.
     *
     * <p>Le corps ne reprend jamais le commentaire : il peut contenir des éléments de santé, et
     * ni le centre de notifications ni une notification système ne sont le bon canal pour ça —
     * on renvoie vers la séance.</p>
     */
    public void notifyCoachComment(Workout workout) {
        User athleteUser = userRepository.findByAthleteId(workout.getAthlete().getId()).orElse(null);
        if (athleteUser == null) {
            return;
        }
        record(athleteUser.getId(), "COACH_COMMENT", "Retour de votre coach",
                workout.getTitle(), "/athlete/history");
        if (athleteUser.isNotifyPushEnabled()) {
            pushService.sendToUser(athleteUser.getId(), "Retour de votre coach",
                    workout.getTitle(), frontendUrl + "/athlete/history");
        }
    }

    /**
     * Retour d'un athlète → notifie son coach <strong>référent</strong> (repli : head coach),
     * <strong>in-app + push, sans e-mail</strong>.
     *
     * <p>C'était le flux le plus volumineux côté coach : un e-mail par séance renseignée, soit
     * plusieurs dizaines par semaine pour un coach de club. Le besoin réel — « qu'est-ce qui
     * m'attend ce matin ? » — est déjà servi par la file « Retours à traiter », sa pastille de
     * navigation, et le digest quotidien de 7 h.</p>
     */
    public void notifyAthleteFeedback(Workout workout) {
        coachToNotify(workout).ifPresent(c -> {
            record(c.getId(), "ATHLETE_FEEDBACK", "Séance mise à jour",
                    workout.getAthlete().getFirstName() + " " + workout.getAthlete().getLastName()
                            + " — " + workout.getStatus(), "/app/feedback");
            if (c.isNotifyPushEnabled()) {
                pushService.sendToUser(c.getId(), "Séance mise à jour",
                        workout.getAthlete().getFirstName() + " — " + workout.getStatus(),
                        frontendUrl + "/app/feedback");
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
        send(coach.getEmail(), subject, html, Audience.COACH);
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

    /**
     * Rappel J-1 → notifie l'athlète d'une séance prévue le lendemain.
     *
     * <p><strong>Push d'abord, e-mail en repli.</strong> Ce rappel partait uniquement par e-mail,
     * un par séance et par jour, alors que c'est le message le plus typiquement « notification »
     * du produit : court, daté, sans pièce jointe, à lire sur un téléphone. C'était aussi le seul
     * flux à ne pas utiliser le push, pourtant câblé partout ailleurs. On ne retombe sur l'e-mail
     * que si l'athlète n'a aucun appareil abonné — sinon un athlète qui refuse les notifications
     * système ne serait plus prévenu du tout.</p>
     */
    public void notifyWorkoutReminder(Workout workout) {
        User athleteUser = userRepository.findByAthleteId(workout.getAthlete().getId()).orElse(null);
        String title = "Séance demain";
        String body = workout.getTitle();

        if (athleteUser != null) {
            record(athleteUser.getId(), "WORKOUT_REMINDER", title, body, "/athlete/today");
            if (athleteUser.isNotifyPushEnabled() && pushService.canReach(athleteUser.getId())) {
                pushService.sendToUser(athleteUser.getId(), title, body,
                        frontendUrl + "/athlete/today");
                return;
            }
        }

        String email = workout.getAthlete().getEmail();
        if (email == null || (athleteUser != null && !athleteUser.isNotifyEmailEnabled())) {
            return;
        }
        send(email, "Rappel : séance demain",
                "<p>Bonjour " + esc(workout.getAthlete().getFirstName()) + ",</p>"
                        + "<p>Rappel : <strong>" + esc(workout.getTitle()) + "</strong> est prévue demain.</p>"
                        + cta("Voir ma séance", frontendUrl + "/athlete/today"),
                Audience.ATHLETE);
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
        send(email, "Confirmez votre adresse e-mail Darilab", html, Audience.COACH);
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
        send(email, "Réinitialisation de votre mot de passe Darilab", html, Audience.COACH);
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
        send(email, subject, html, Audience.COACH);
    }

    /**
     * Invitation d'un athlète par son coach : e-mail avec le lien magique d'onboarding. Sans e-mail
     * (athlète sans adresse connue), rien n'est envoyé — le coach transmet l'URL renvoyée par l'API.
     */
    public void notifyAthleteInvitation(String email, String firstName, String clubName, String url) {
        if (email == null) {
            return;
        }
        String subject = "Votre coach vous invite sur Darilab";
        String html = "<p>Bonjour " + esc(firstName) + ",</p>"
                + "<p>Votre coach vous invite à rejoindre <strong>" + esc(clubName)
                + "</strong> sur Darilab pour suivre vos séances et partager vos ressentis.</p>"
                + cta("Activer mon espace athlète", url)
                + "<p>Ce lien expire dans 14 jours.</p>";
        send(email, subject, html, Audience.ATHLETE);
    }

    /**
     * L'athlète déclare une indisponibilité → notifie son coach référent (in-app + push + email).
     * Le motif est une catégorie fermée (blessure, maladie, vacances, personnel) : ce n'est pas
     * une donnée de santé détaillée, et c'est précisément ce dont le coach a besoin pour
     * replanifier. Le commentaire libre, lui, reste dans l'application.
     */
    public void notifyAthleteUnavailability(com.coachrun.entity.Athlete athlete,
                                            com.coachrun.entity.AthleteUnavailability unavailability) {
        UUID clubId = athlete.getClub() != null ? athlete.getClub().getId() : null;
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> {
            String athleteName = (athlete.getFirstName() + " " + athlete.getLastName()).trim();
            String period = unavailability.getStartDate() + " → " + unavailability.getEndDate();
            String reason = reasonLabel(unavailability.getReason());

            record(coach.getId(), "ATHLETE_UNAVAILABILITY", "Indisponibilité déclarée",
                    athleteName + " — " + reason + " (" + period + ")", "/app/calendar");
            if (coach.isNotifyPushEnabled()) {
                pushService.sendToUser(coach.getId(), "Indisponibilité déclarée",
                        athleteName + " — " + reason, frontendUrl + "/app/calendar");
            }
            if (coach.getEmail() == null || !coach.isNotifyEmailEnabled()) {
                return;
            }
            String html = "<p>Bonjour " + esc(coach.getFullName()) + ",</p>"
                    + "<p><strong>" + esc(athleteName) + "</strong> a déclaré une indisponibilité : "
                    + esc(reason) + ", du " + unavailability.getStartDate()
                    + " au " + unavailability.getEndDate() + ".</p>"
                    + cta("Ouvrir le calendrier", frontendUrl + "/app/calendar");
            send(coach.getEmail(), athleteName + " est indisponible", html, Audience.COACH);
        });
    }

    /**
     * L'athlète a retiré son consentement au traitement de ses données de santé → prévient son
     * coach référent (in-app + push, jamais d'e-mail).
     *
     * <p>Sans ce signal, le coach découvre une fiche qui s'est vidée — plus de tests de lactate,
     * plus de douleur déclarée — et ses prochaines saisies sont refusées, sans qu'il comprenne
     * pourquoi. Le message ne dit rien de l'état de santé de l'athlète : il annonce un changement
     * de droits, ce qui est précisément l'information dont le coach a besoin.</p>
     */
    public void notifyHealthConsentWithdrawn(com.coachrun.entity.Athlete athlete) {
        UUID clubId = athlete.getClub() != null ? athlete.getClub().getId() : null;
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> {
            String athleteName = (athlete.getFirstName() + " " + athlete.getLastName()).trim();
            record(coach.getId(), "HEALTH_CONSENT_WITHDRAWN", "Consentement santé retiré",
                    athleteName + " ne partage plus ses données de santé (douleur, lactate).",
                    "/app/athletes/" + athlete.getId());
            if (coach.isNotifyPushEnabled()) {
                pushService.sendToUser(coach.getId(), "Consentement santé retiré",
                        athleteName + " ne partage plus ses données de santé.",
                        frontendUrl + "/app/athletes/" + athlete.getId());
            }
        });
    }

    /** Libellé français d'un motif d'indisponibilité. */
    private String reasonLabel(com.coachrun.entity.enums.UnavailabilityReason reason) {
        if (reason == null) {
            return "indisponible";
        }
        return switch (reason) {
            case INJURY -> "blessure";
            case ILLNESS -> "maladie";
            case VACATION -> "vacances";
            case PERSONAL -> "raison personnelle";
            case OTHER -> "autre motif";
        };
    }

    /**
     * Enveloppe le fragment dans le gabarit transactionnel puis envoie HTML + texte, avec
     * {@code reply_to} et {@code List-Unsubscribe}. Un échec d'envoi ne casse jamais le métier.
     */
    private void send(String to, String subject, String bodyHtml, Audience audience) {
        if (!enabled) {
            log.info("[mail désactivé] -> {} : {}", to, subject);
            return;
        }
        // Envoi APRÈS commit quand on est dans une transaction. Un appel HTTP fait au milieu d'une
        // transaction retient une connexion Hikari (pool de 10) pendant toute sa durée : quelques
        // envois lents suffisaient à assécher le pool et à figer l'API. Bénéfice secondaire : une
        // transaction qui échoue n'envoie plus d'e-mail annonçant une action qui n'a pas eu lieu.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatch(to, subject, bodyHtml, audience);
                        }
                    });
            return;
        }
        dispatch(to, subject, bodyHtml, audience);
    }

    /** Envoi effectif (hors transaction) : rendu du gabarit puis appel au client Resend. */
    private void dispatch(String to, String subject, String bodyHtml, Audience audience) {
        try {
            MailTemplate.Rendered mail = mailTemplate.render(subject, bodyHtml, audience);
            mailClient.send(to, subject, mail.html(), mail.text(),
                    mailTemplate.replyTo(), mailTemplate.listUnsubscribe(audience));
        } catch (RuntimeException ex) {
            // Idempotence/robustesse : un échec d'envoi ne casse pas l'action métier. Mais il ne
            // doit plus être silencieux : un quota Resend épuisé fait échouer *tous* les envois,
            // y compris les réinitialisations de mot de passe et les invitations. Sans remontée,
            // le coach voit « e-mail envoyé », rien n'arrive, et personne n'est prévenu.
            log.error("Échec d'envoi d'e-mail à {} (sujet « {} ») : {}", to, subject, ex.getMessage());
            io.sentry.Sentry.captureException(ex);
        }
    }

    /** Bouton d'action du gabarit (table stylée inline, cible ≥ 44 px). */
    private String cta(String label, String url) {
        return mailTemplate.button(label, url);
    }

    /** Échappe le HTML en gardant les accents littéraux (le gabarit est déclaré en UTF-8). */
    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }
}
