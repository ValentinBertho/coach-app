package com.coachrun.service;

import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.Message;
import com.coachrun.entity.Notification;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.NotificationCategory;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.integration.MailTemplate;
import com.coachrun.integration.MailTemplate.Audience;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.repository.AlertDigestLogRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.NotificationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final AlertDigestLogRepository digestLogRepository;
    private final ClockService clock;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Calendrier de l'athlète : destination commune des notifications qui portent sur son programme. */
    private static final String ATHLETE_CALENDAR = "/athlete/calendar";

    /** Dates affichées aux utilisateurs : « samedi 12 juillet », pas « 2026-07-12 ». */
    private static final DateTimeFormatter DAY_FR =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    /**
     * Anti-rafale d'un fil de messagerie. Un échange vif — cinq messages en trois minutes — est
     * une conversation, pas cinq événements : le premier message fait sonner le téléphone, les
     * suivants se lisent dans le fil qu'il vient d'ouvrir.
     */
    private static final Duration MESSAGE_BURST_WINDOW = Duration.ofMinutes(10);

    /**
     * Anti-rafale des changements de programme. Un coach qui remanie une semaine déplace trois
     * séances et en supprime une en quelques minutes : l'athlète a besoin de savoir que son
     * programme a bougé, pas de recevoir quatre notifications pour un seul remaniement.
     */
    private static final Duration WORKOUT_CHANGE_BURST_WINDOW = Duration.ofMinutes(30);

    // ------------------------------------------------------------------------------------------
    // Point d'entrée unique des notifications de routine
    // ------------------------------------------------------------------------------------------

    /**
     * Dépose une notification de routine : trace au centre de notifications (<b>toujours</b>),
     * puis push si l'utilisateur l'accepte.
     *
     * <p>Ce couple {@code record(...)} + {@code if (isNotifyPushEnabled()) sendToUser(...)} était
     * recopié à l'identique dans chacun des déclencheurs. Toute règle transversale à venir —
     * heures de silence, préférence par catégorie, métrique de remise — aurait dû y être ajoutée
     * autant de fois, et oubliée à la suivante. Elle n'a désormais qu'un seul endroit où vivre.</p>
     */
    private void notifyUser(User target, String type, String title, String body, String link) {
        notifyUser(target, type, title, body, body, link, true);
    }

    /**
     * Variante à corps distincts. Le centre de notifications se lit posément et peut porter le
     * nom complet et la période ; la notification système est lue d'un coup d'œil sur un écran
     * verrouillé, et se contente de l'essentiel.
     */
    private void notifyUser(User target, String type, String title, String body, String pushBody,
                            String link) {
        notifyUser(target, type, title, body, pushBody, link, true);
    }

    /**
     * Forme complète. {@code push} à {@code false} dépose la trace sans interrompre : c'est le
     * réglage des notifications dont l'intérêt est réel mais l'urgence nulle — elles se découvrent
     * à la pastille de la cloche, quand l'utilisateur revient de lui-même.
     */
    private void notifyUser(User target, String type, String title, String body, String pushBody,
                            String link, boolean push) {
        if (target == null) {
            return;
        }
        record(target.getId(), type, title, body, link);
        if (push) {
            pushOnly(target, type, title, pushBody, link);
        }
    }

    /**
     * Trace au centre de notifications, sans jamais faire sonner. Pour ce dont l'intérêt est réel
     * mais l'urgence nulle : l'utilisateur le découvre à la pastille de la cloche, quand il
     * revient de lui-même.
     */
    private void notifyInAppOnly(User target, String type, String title, String body, String link) {
        notifyUser(target, type, title, body, body, link, false);
    }

    /**
     * Push seul, sans trace in-app. Réservé à ce qui est périmé le lendemain : une ligne « Ta
     * séance est finie ? » relue trois jours plus tard n'a plus aucun sens.
     */
    private void pushOnly(User target, String type, String title, String body, String link) {
        if (!pushAllowed(target, type)) {
            return;
        }
        pushService.sendToUser(target.getId(), title, body, frontendUrl + link);
    }

    /**
     * Remise système, sous les trois conditions que l'utilisateur maîtrise : le push activé, la
     * catégorie non coupée, et l'heure hors de sa plage de silence.
     *
     * <p>Les deux derniers filtres ne touchent que ce canal. La trace in-app est déposée avant
     * l'appel et reste intacte : couper une famille ou dormir ne doit jamais faire perdre une
     * information, seulement l'interruption qui l'accompagnait.</p>
     */
    private void pushOnly(User target, String type, String title, String body, String link,
                          List<PushNotificationService.QuickAction> actions) {
        if (!pushAllowed(target, type)) {
            return;
        }
        pushService.sendToUser(target.getId(), title, body, frontendUrl + link, actions);
    }

    /** Préférences de l'utilisateur réunies : canal, catégorie, heure. */
    private boolean pushAllowed(User target, String type) {
        if (target == null || !target.isNotifyPushEnabled()) {
            return false;
        }
        if (target.mutedCategories().contains(NotificationCategory.of(type))) {
            return false;
        }
        // Heure locale du destinataire, pas celle du serveur : une plage de silence calculée à
        // Paris réveille la nuit un athlète installé ailleurs — exactement ce qu'elle évite ici.
        return !inQuietHours(target, java.time.LocalTime.now(clock.clockIn(target)));
    }

    /**
     * L'heure tombe-t-elle dans la plage de silence ? La plage traverse minuit dans le cas
     * courant (22 h → 7 h), d'où la comparaison en deux morceaux ; deux bornes identiques valent
     * « pas de silence », et non « silence permanent ».
     *
     * <p>Prédicat pur, exposé pour être éprouvé directement : le passage de minuit est le genre de
     * détail qu'on croit évident et qu'on écrit à l'envers une fois sur deux.</p>
     */
    public static boolean inQuietHours(User target, LocalTime now) {
        LocalTime start = target.getNotifyQuietStart();
        LocalTime end = target.getNotifyQuietEnd();
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        return start.isBefore(end)
                ? !now.isBefore(start) && now.isBefore(end)
                : !now.isBefore(start) || now.isBefore(end);
    }

    /**
     * Ce compte accepte-t-il ce push <em>maintenant</em>, et un appareil peut-il le recevoir ?
     * Condition du repli e-mail du rappel de séance : un rappel tombant en heures de silence doit
     * partir par e-mail plutôt que de disparaître.
     */
    private boolean pushReaches(User target, String type) {
        return pushAllowed(target, type) && pushService.canReach(target.getId());
    }

    /** Compte utilisateur d'un athlète, ou {@code null} s'il n'a pas encore activé son espace. */
    private User athleteUser(Athlete athlete) {
        return athlete == null ? null
                : userRepository.findByAthleteId(athlete.getId()).orElse(null);
    }

    /**
     * Une notification de même nature et de même destination a-t-elle déjà été déposée dans la
     * fenêtre ? Le couple (type, lien) identifie le sujet : le lien porte le fil de messagerie ou
     * le calendrier de l'athlète, donc deux sujets distincts ne se masquent jamais l'un l'autre.
     *
     * <p>En cas d'indisponibilité du dépôt, on notifie : rater une notification est pire que
     * d'en envoyer une de trop.</p>
     */
    private boolean recentlyNotified(UUID userId, String type, String link, Duration window) {
        try {
            return notificationRepository.existsByUserIdAndTypeAndLinkAndCreatedAtAfter(
                    userId, type, link, Instant.now().minus(window));
        } catch (RuntimeException ex) {
            log.warn("Anti-rafale indisponible ({}) : {}", type, ex.getMessage());
            return false;
        }
    }

    /** Date en toutes lettres, pour un corps de notification lu par un humain. */
    private static String fr(LocalDate date) {
        return date == null ? "" : DAY_FR.format(date);
    }

    private static String fullName(Athlete athlete) {
        return (athlete.getFirstName() + " " + athlete.getLastName()).trim();
    }

    // ------------------------------------------------------------------------------------------
    // Déclencheurs
    // ------------------------------------------------------------------------------------------

    /**
     * Séance planifiée → notifie l'athlète, <strong>in-app + push, sans e-mail</strong>.
     *
     * <p>C'est le geste quotidien du coach : déposer cinq séances depuis la bibliothèque
     * envoyait cinq e-mails à l'athlète dans la minute. La séance apparaît de toute façon dans
     * son agenda et dans « Aujourd'hui » ; l'e-mail n'apportait qu'un doublon, au prix du plus
     * gros poste de consommation du quota d'envoi.</p>
     *
     * <p>Ce déclencheur vaut pour une séance <b>posée à l'unité</b>. La génération en lot
     * (attribution d'un plan) le court-circuite et passe par {@link #notifyPlanAssigned} : voir
     * la javadoc de cette méthode.</p>
     */
    public void notifyWorkoutPlanned(Workout workout) {
        notifyUser(athleteUser(workout.getAthlete()), "WORKOUT_PLANNED", "Nouvelle séance",
                workout.getTitle() + " — " + fr(workout.getScheduledDate()), "/athlete/today");
    }

    /**
     * Attribution d'un plan → <strong>une seule</strong> notification pour tout le programme.
     *
     * <p>L'attribution génère une séance par item : douze semaines à quatre séances en produisent
     * une cinquantaine, chacune notifiée, en une salve. Pour un seul geste du coach, l'athlète
     * recevait cinquante notifications système — le moyen le plus sûr de lui faire couper le
     * canal qu'on cherche précisément à garder ouvert. Et comme l'attribution est
     * idempotente-régénérante, chaque réattribution rejouait la salve entière.</p>
     *
     * <p>Ce qui compte pour l'athlète n'est de toute façon pas la cinquantième séance prise
     * isolément, mais le fait qu'un programme l'attende.</p>
     */
    public void notifyPlanAssigned(Athlete athlete, String planName, int sessions, LocalDate lastDate) {
        if (sessions <= 0) {
            return;
        }
        String body = "« " + planName + " » — " + sessions + (sessions > 1 ? " séances" : " séance")
                + (lastDate == null ? "" : " jusqu'au " + fr(lastDate));
        notifyUser(athleteUser(athlete), "PLAN_ASSIGNED", "Nouveau plan d'entraînement",
                body, ATHLETE_CALENDAR);
    }

    /**
     * Séance modifiée ou déplacée par le coach → notifie l'athlète.
     *
     * <p>Le trou le plus visible du dispositif : un coach qui déplaçait la sortie longue du
     * dimanche au samedi ne prévenait personne. L'athlète découvrait le changement en ouvrant
     * l'application — ou ne le découvrait pas, et manquait sa séance.</p>
     *
     * <p>Dédoublonné par athlète sur {@link #WORKOUT_CHANGE_BURST_WINDOW} : remanier une semaine
     * est un seul geste, et le lien mène au calendrier, où tout le remaniement se lit d'un coup.
     * C'est aussi pourquoi modification, déplacement et annulation partagent un même type — seul
     * le libellé du premier événement de la salve est retenu.</p>
     */
    public void notifyWorkoutChanged(Workout workout, boolean moved) {
        notifyWorkoutChange(workout, moved ? "Séance déplacée" : "Séance modifiée");
    }

    /** Séance supprimée par le coach → notifie l'athlète (même anti-rafale que la modification). */
    public void notifyWorkoutCancelled(Workout workout) {
        notifyWorkoutChange(workout, "Séance annulée");
    }

    /**
     * Séance de renforcement déplacée ou déprogrammée par le coach → notifie l'athlète.
     *
     * <p>La même asymétrie que pour la planification, un cran plus loin : poser une séance de
     * force prévenait déjà, la déplacer ou la retirer ne prévenait personne. L'athlète se
     * présentait en salle pour une séance qui n'existait plus.</p>
     *
     * <p>Même type que la course, donc même anti-rafale : remanier une semaine — footing déplacé,
     * renfo décalé dans la foulée — reste un seul geste, et le lien mène au calendrier où tout se
     * lit d'un coup.</p>
     */
    public void notifyStrengthChanged(Athlete athlete, String sessionTitle, LocalDate date, boolean cancelled) {
        notifyCalendarChange(athleteUser(athlete),
                cancelled ? "Séance de renforcement annulée" : "Séance de renforcement déplacée",
                sessionTitle + " — " + fr(date));
    }

    private void notifyWorkoutChange(Workout workout, String title) {
        notifyCalendarChange(athleteUser(workout.getAthlete()), title,
                workout.getTitle() + " — " + fr(workout.getScheduledDate()));
    }

    /** Remaniement du calendrier par le coach : un seul type, une seule salve, un seul lien. */
    private void notifyCalendarChange(User target, String title, String body) {
        if (target == null
                || recentlyNotified(target.getId(), "WORKOUT_UPDATED", ATHLETE_CALENDAR,
                        WORKOUT_CHANGE_BURST_WINDOW)) {
            return;
        }
        notifyUser(target, "WORKOUT_UPDATED", title, body, ATHLETE_CALENDAR);
    }

    /**
     * Nouveau message dans le fil coach ↔ athlète → notifie l'autre partie.
     *
     * <p>C'était le manque le plus criant du produit : la messagerie n'appelait jamais ce
     * service. Elle diffuse bien le message en temps réel, mais uniquement aux clients qui ont
     * déjà l'écran ouvert — c'est-à-dire exactement le cas où la notification est inutile. Un
     * athlète qui posait une question le soir n'avait aucun moyen de savoir que son coach avait
     * répondu.</p>
     *
     * <p><b>Le corps ne reprend jamais le texte du message</b>, seulement le nom de l'expéditeur.
     * Une conversation coach ↔ athlète parle de blessures et de fatigue : la faire apparaître sur
     * un écran verrouillé, ou la figer au centre de notifications, contreviendrait à l'invariant
     * que respectent déjà tous les autres déclencheurs.</p>
     */
    public void notifyNewMessage(Message message) {
        Athlete athlete = message.getAthlete();
        if (athlete == null) {
            return;
        }
        boolean fromAthlete = message.getSenderRole() == UserRole.ATHLETE;
        String link = fromAthlete
                ? "/app/athletes/" + athlete.getId() + "/messages"
                : "/athlete/messages";
        User target = fromAthlete
                ? referentCoach(athlete.getId(),
                        athlete.getClub() == null ? null : athlete.getClub().getId()).orElse(null)
                : athleteUser(athlete);

        // Un coach qui gère son propre dossier d'athlète ne s'écrit pas à lui-même.
        if (target == null || target.getId().equals(message.getSenderUserId())
                || recentlyNotified(target.getId(), "NEW_MESSAGE", link, MESSAGE_BURST_WINDOW)) {
            return;
        }
        notifyUser(target, "NEW_MESSAGE", "Nouveau message", message.getSenderName(), link);
    }

    /**
     * Séance de renforcement planifiée → notifie l'athlète, comme une séance de course.
     *
     * <p>Le produit traitait ses deux disciplines différemment sans raison : poser un footing
     * prévenait l'athlète, poser une séance de force ne prévenait personne. L'asymétrie était
     * invisible côté coach — il fait le même geste — et déroutante côté athlète, qui découvrait
     * du renforcement dans son calendrier sans l'avoir vu arriver.</p>
     */
    public void notifyStrengthPlanned(Athlete athlete, String sessionTitle, LocalDate date) {
        notifyUser(athleteUser(athlete), "STRENGTH_PLANNED", "Nouvelle séance de renforcement",
                sessionTitle + " — " + fr(date), ATHLETE_CALENDAR);
    }

    /**
     * Retour sur une séance de renforcement → notifie le coach référent, mêmes règles que pour la
     * course : push seulement si le retour appelle une décision, sinon trace au centre.
     *
     * <p>Le pendant manquant de {@link #notifyAthleteFeedback} : le rappel de débriefing relançait
     * bien l'athlète sur ses séances de force, mais sa réponse n'arrivait nulle part.</p>
     */
    public void notifyStrengthFeedback(com.coachrun.entity.ScheduledStrengthSession session) {
        Athlete athlete = session.getAthlete();
        UUID clubId = session.getClub() != null ? session.getClub().getId() : null;
        // Le RPE de force est décimal (7,5 est une valeur courante), d'où la comparaison BigDecimal.
        boolean notable = (session.getSessionPain() != null && session.getSessionPain() >= PAIN_REPORTED)
                || (session.getSessionRpe() != null
                        && session.getSessionRpe().compareTo(java.math.BigDecimal.valueOf(RPE_MAXIMAL)) >= 0);
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> notifyUser(coach,
                "ATHLETE_FEEDBACK", "Renforcement mis à jour",
                fullName(athlete) + " — " + session.getTitle(),
                athlete.getFirstName() + " — renforcement",
                "/app/feedback", notable));
    }

    /**
     * Un athlète invité vient d'activer son espace → prévient le coach qui l'a invité.
     *
     * <p>Le coach envoyait l'invitation puis n'entendait plus rien : il devait rouvrir la fiche
     * de temps en temps pour voir si le statut avait changé. C'est pourtant le moment où il a
     * quelque chose à faire — poser les premières séances.</p>
     */
    public void notifyAthleteJoined(Athlete athlete) {
        UUID clubId = athlete.getClub() != null ? athlete.getClub().getId() : null;
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> {
            String name = fullName(athlete);
            notifyUser(coach, "ATHLETE_JOINED", "Nouvel athlète",
                    name + " a activé son espace.", name + " a rejoint ton groupe.",
                    "/app/athletes/" + athlete.getId());
        });
    }

    /**
     * Sortie remontée de la montre → notifie l'athlète, <strong>avec ses chiffres</strong>.
     *
     * <p>L'import tournait en silence : l'athlète ne savait pas si sa sortie était bien arrivée,
     * et l'apprenait quand son coach lui demandait pourquoi il n'avait rien couru.</p>
     *
     * <p>Le corps porte le nom, la durée et la distance. C'est ce qui distingue une confirmation
     * utile — « voilà ce que j'ai enregistré, ça correspond » — d'un simple accusé de réception :
     * l'athlète vérifie d'un coup d'œil que la bonne sortie est remontée, sans ouvrir l'app.</p>
     *
     * <p>Une seule notification par synchro. Quand plusieurs sorties arrivent ensemble — montre
     * restée sans réseau —, le détail laisse la place au décompte.</p>
     */
    public void notifyActivitiesImported(Athlete athlete,
                                         List<? extends ImportedActivitySummary> imported) {
        if (imported == null || imported.isEmpty()) {
            return;
        }
        String body = imported.size() == 1
                ? describe(imported.get(0))
                : imported.size() + " sorties récupérées depuis ta montre.";
        notifyUser(athleteUser(athlete), "ACTIVITY_IMPORTED", "Nouvelle sortie synchronisée",
                body, "/athlete/activities");
    }

    /**
     * Ce que sait dire une sortie importée. Interface plutôt que type concret : le service
     * d'import garde son propre résumé, sans que le service de notification ait à connaître
     * Strava — ni le prochain fournisseur de montre.
     */
    public interface ImportedActivitySummary {
        String title();

        Integer durationS();

        Integer distanceM();
    }

    /** « Afternoon Run · 1:12:31 · 14,4 km », en n'écrivant que ce qui est connu. */
    private String describe(ImportedActivitySummary a) {
        StringBuilder sb = new StringBuilder(a.title() == null ? "Sortie" : a.title());
        if (a.durationS() != null && a.durationS() > 0) {
            sb.append(" · ").append(duration(a.durationS()));
        }
        if (a.distanceM() != null && a.distanceM() > 0) {
            sb.append(" · ").append(String.format(Locale.FRENCH, "%.1f km", a.distanceM() / 1000.0));
        }
        return sb.toString();
    }

    /** « 1:12:31 », ou « 48:05 » sous l'heure. */
    private static String duration(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /**
     * Record personnel battu → félicite l'athlète, informe son coach.
     *
     * <p>Le produit n'avait <b>aucun signal de réussite</b> : il savait dire une douleur, une
     * séance manquée, une charge inquiétante, un silence — jamais qu'un athlète venait de courir
     * plus vite qu'il ne l'avait jamais fait. C'est la seule notification du lot dont on peut
     * espérer qu'elle fasse plaisir à la recevoir.</p>
     *
     * <p>Côté coach, in-app sans push : ce n'est pas une décision à prendre dans l'heure, mais
     * c'est une information de suivi réelle — un nouveau record recalcule le VDOT, donc toutes les
     * allures prescrites.</p>
     */
    public void notifyPersonalRecord(Athlete athlete, String distanceLabel, String time) {
        String body = distanceLabel + " en " + time;
        notifyUser(athleteUser(athlete), "PERSONAL_RECORD", "Nouveau record personnel",
                body + " — bravo !", "/athlete/progress");

        UUID clubId = athlete.getClub() != null ? athlete.getClub().getId() : null;
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> notifyInAppOnly(coach,
                "PERSONAL_RECORD", "Record personnel",
                fullName(athlete) + " — " + body + " (VDOT et allures recalculés).",
                "/app/athletes/" + athlete.getId()));
    }

    /**
     * Course cible qui approche → prévient l'athlète.
     *
     * <p>Une date d'objectif dormait dans une fiche que personne n'ouvre la veille. J-7 est le
     * moment de l'affûtage, J-1 celui de la logistique : deux rappels, pas un de plus.</p>
     */
    public void notifyRaceApproaching(Athlete athlete, String raceName, int daysLeft) {
        String title = daysLeft <= 1 ? "Ta course, c'est demain" : "Ta course dans " + daysLeft + " jours";
        notifyUser(athleteUser(athlete), "RACE_REMINDER", title, raceName, "/athlete/races");
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
        notifyUser(athleteUser(workout.getAthlete()), "COACH_COMMENT", "Retour de votre coach",
                workout.getTitle(), "/athlete/history");
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
        Athlete athlete = workout.getAthlete();
        String status = statusLabel(workout.getStatus());
        boolean notable = notable(workout);
        coachToNotify(workout).ifPresent(c -> notifyUser(c, "ATHLETE_FEEDBACK", "Séance mise à jour",
                fullName(athlete) + " — " + status,
                athlete.getFirstName() + " — " + status,
                "/app/feedback", notable));
    }

    /**
     * Ce retour mérite-t-il d'interrompre le coach, ou seulement d'attendre dans sa file ?
     *
     * <p>Un coach de vingt athlètes recevait une quinzaine de push par jour pour des séances
     * réalisées comme prévu — l'information la moins urgente du produit, et la première à faire
     * couper le canal. Elle est déjà servie par la file « Retours à traiter », sa pastille, et le
     * digest de 7 h.</p>
     *
     * <p>Reste ce qui appelle une décision le jour même : une douleur déclarée, une séance non
     * faite, un effort au bout du rouleau. Ces trois-là passent en push ; tout le reste se dépose
     * au centre de notifications, où le coach le trouvera quand il viendra.</p>
     */
    private boolean notable(Workout workout) {
        return (workout.getPain() != null && workout.getPain() >= PAIN_REPORTED)
                || workout.getStatus() == WorkoutStatus.MISSED
                || (workout.getRpe() != null && workout.getRpe() >= RPE_MAXIMAL);
    }

    /** Douleur à partir de laquelle un retour de séance mérite d'interrompre le coach. */
    private static final int PAIN_REPORTED = 3;

    /** Douleur à partir de laquelle le coach est prévenu <b>tout de suite</b>, hors digest. */
    private static final int PAIN_SEVERE = 5;

    /** RPE au-delà duquel l'effort mérite un regard le jour même. */
    private static final int RPE_MAXIMAL = 9;

    /** Une alerte de douleur n'est pas répétée à ce coach pour cet athlète avant ce délai. */
    private static final Duration PAIN_ALERT_WINDOW = Duration.ofDays(7);

    /**
     * Douleur élevée déclarée → prévient le coach référent <strong>immédiatement</strong>.
     *
     * <p>Une douleur 8/10 saisie au check-in de 8 h attendait le digest du lendemain matin :
     * vingt-trois heures pendant lesquelles l'athlète pouvait courir la séance qui aggrave la
     * blessure. C'est le seul signal du produit dont le délai coûte quelque chose de physique.</p>
     *
     * <p>Le corps ne porte <b>pas l'intensité</b>, seulement le nom et la catégorie — même
     * arbitrage que pour l'indisponibilité déclarée, dont le motif (« blessure ») est ce dont le
     * coach a besoin pour agir, là où « douleur 8/10 » est une mesure de santé qui n'a rien à
     * faire sur un écran verrouillé.</p>
     *
     * <p>L'envoi est inscrit dans la mémoire du digest : l'alerte n'est donc pas redite le
     * lendemain matin par un second canal, et elle revient d'elle-même après le délai de rappel
     * si la situation dure.</p>
     */
    public void notifyPainAlert(Athlete athlete, Integer pain) {
        if (athlete == null || pain == null || pain < PAIN_SEVERE) {
            return;
        }
        UUID clubId = athlete.getClub() != null ? athlete.getClub().getId() : null;
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> {
            if (painAlreadySignalled(coach.getId(), athlete.getId())) {
                return;
            }
            String name = fullName(athlete);
            notifyUser(coach, "PAIN_ALERT", "Douleur signalée",
                    name + " — à regarder avant sa prochaine séance.",
                    name + " — à regarder.",
                    "/app/athletes/" + athlete.getId());
        });
    }

    /**
     * Blessure <b>nommée</b> au débrief → prévient le coach référent immédiatement.
     *
     * <p>Un curseur de douleur ne franchit pas toujours le seuil d'alerte : une entorse de
     * cheville déclarée avec une douleur à 4 passait donc inaperçue jusqu'au digest, alors que
     * l'athlète venait d'écrire noir sur blanc qu'il s'était blessé. Nommer la blessure est un
     * geste délibéré — il n'a pas de seuil à franchir.</p>
     *
     * <p>Comme pour la douleur, le corps ne porte <b>ni la nature ni la localisation</b> : elles
     * s'afficheraient sur un écran verrouillé. Le coach est invité à ouvrir la fiche, où la
     * déclaration est lisible en entier.</p>
     */
    public void notifyInjuryAlert(Athlete athlete, java.util.List<com.coachrun.dto.InjuryReport> injuries) {
        if (athlete == null || injuries == null || injuries.isEmpty()) {
            return;
        }
        UUID clubId = athlete.getClub() != null ? athlete.getClub().getId() : null;
        referentCoach(athlete.getId(), clubId).ifPresent(coach -> {
            if (alreadySignalled(coach.getId(), athlete.getId(), "INJURY", PAIN_ALERT_WINDOW)) {
                return;
            }
            String name = fullName(athlete);
            notifyUser(coach, "INJURY_ALERT", "Blessure déclarée",
                    name + " a déclaré une blessure sur sa séance — à regarder.",
                    name + " — blessure déclarée.",
                    "/app/athletes/" + athlete.getId());
        });
    }

    /**
     * Cette douleur a-t-elle déjà été signalée à ce coach ? Partage la mémoire du digest quotidien
     * ({@code alert_digest_log}, clé coach × athlète × type) : dire la même chose deux fois par
     * deux canaux est exactement le bruit que le digest avait été conçu pour supprimer.
     */
    private boolean painAlreadySignalled(UUID coachId, UUID athleteId) {
        return alreadySignalled(coachId, athleteId, "PAIN", PAIN_ALERT_WINDOW);
    }

    /**
     * Cette alerte a-t-elle déjà été envoyée à ce coach pour cet athlète dans la fenêtre donnée ?
     *
     * <p>Un type par nature d'alerte : une douleur signalée ne doit pas faire taire une blessure
     * déclarée le lendemain, ce sont deux informations différentes. La mémoire est celle du
     * digest quotidien, pour qu'aucune alerte immédiate ne soit redite le lendemain matin.</p>
     */
    private boolean alreadySignalled(UUID coachId, UUID athleteId, String alertType, Duration window) {
        try {
            Instant now = Instant.now();
            var previous = digestLogRepository.findByCoachId(coachId).stream()
                    .filter(row -> row.getAthleteId().equals(athleteId)
                            && alertType.equals(row.getAlertType()))
                    .findFirst();
            if (previous.isPresent()) {
                if (previous.get().getLastSentAt().isAfter(now.minus(window))) {
                    return true;
                }
                previous.get().setLastSentAt(now);
                return false;
            }
            var row = new com.coachrun.entity.AlertDigestLog();
            row.setCoachId(coachId);
            row.setAthleteId(athleteId);
            row.setAlertType(alertType);
            row.setLastSentAt(now);
            digestLogRepository.save(row);
            return false;
        } catch (RuntimeException ex) {
            log.warn("Mémoire des alertes indisponible : {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Libellé français d'un état de séance.
     *
     * <p>Le code de l'énumération était concaténé tel quel : le coach lisait « Marie Durand —
     * COMPLETED », seul endroit du produit à parler en anglais et en majuscules.</p>
     */
    private String statusLabel(WorkoutStatus status) {
        if (status == null) {
            return "mise à jour";
        }
        return switch (status) {
            case PLANNED -> "à venir";
            case COMPLETED -> "réalisée";
            case PARTIAL -> "partiellement réalisée";
            case MISSED -> "non réalisée";
        };
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

        notifyUser(coach, "COACH_ALERTS", "Alertes à traiter",
                count + (count > 1 ? " athlètes nécessitent votre attention" : " athlète nécessite votre attention"),
                count + (count > 1 ? " athlètes à surveiller" : " athlète à surveiller"),
                "/app");

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
        notifyWorkoutReminder(List.of(workout));
    }

    /**
     * Rappel J-1 groupé : <b>une notification par athlète</b>, quel que soit le nombre de séances
     * prévues le lendemain.
     *
     * <p>Le rappel partait par séance : un athlète qui a footing le matin et renforcement le soir
     * recevait deux notifications à la suite, à 18 h, disant la même chose. La veille au soir,
     * l'information utile est « demain, voilà ce qui t'attend » — une fois.</p>
     */
    public void notifyWorkoutReminder(List<Workout> workouts) {
        if (workouts == null || workouts.isEmpty()) {
            return;
        }
        notifyTomorrowProgram(workouts.get(0).getAthlete(),
                workouts.stream().map(Workout::getTitle).toList());
    }

    /**
     * Rappel J-1 sur le <b>programme complet</b> du lendemain, course et renforcement confondus.
     *
     * <p>Le rappel se construisait à partir des seules séances course, alors qu'il est écrit pour
     * annoncer « footing le matin et renforcement le soir » en une fois : un athlète dont le
     * lendemain ne portait que du renforcement n'était donc pas prévenu — et pire, tombait dans
     * l'annonce de repos. Prendre des intitulés plutôt que des entités permet aux deux natures de
     * séance d'entrer dans le même message.</p>
     *
     * @param titles intitulés des séances de demain, dans l'ordre d'affichage
     */
    public void notifyTomorrowProgram(Athlete athlete, List<String> titles) {
        if (athlete == null || titles == null || titles.isEmpty()) {
            return;
        }
        User athleteUser = athleteUser(athlete);
        int count = titles.size();
        String title = count > 1 ? count + " séances demain" : "Séance demain";
        String body = String.join(" · ", titles);

        if (athleteUser != null) {
            record(athleteUser.getId(), "WORKOUT_REMINDER", title, body, "/athlete/today");
            if (pushReaches(athleteUser, "WORKOUT_REMINDER")) {
                pushOnly(athleteUser, "WORKOUT_REMINDER", title, body, "/athlete/today");
                return;
            }
        }

        String email = athlete.getEmail();
        if (email == null || (athleteUser != null && !athleteUser.isNotifyEmailEnabled())) {
            return;
        }
        StringBuilder items = new StringBuilder();
        titles.forEach(t -> items.append("<li>").append(esc(t)).append("</li>"));
        send(email, count > 1 ? "Rappel : " + count + " séances demain" : "Rappel : séance demain",
                "<p>Bonjour " + esc(athlete.getFirstName()) + ",</p>"
                        + "<p>Rappel — demain :</p><ul>" + items + "</ul>"
                        + cta(count > 1 ? "Voir mes séances" : "Voir ma séance",
                                frontendUrl + "/athlete/today"),
                Audience.ATHLETE);
    }

    /**
     * Point de programme du soir quand <b>rien</b> n'est prévu le lendemain : « Repos demain ».
     *
     * <p>Le rappel du soir ne parlait qu'aux athlètes ayant une séance : ceux qui n'avaient rien
     * n'apprenaient rien, et devaient ouvrir l'application pour savoir que justement il n'y avait
     * rien. Or « demain, repos » est une information d'entraînement à part entière — c'est même
     * celle qui autorise à se coucher tard.</p>
     *
     * <p><b>Push seul, délibérément</b>, comme le rappel de débriefing : ni trace au centre de
     * notifications, ni repli e-mail. Une ligne « Repos demain » relue trois jours plus tard ne
     * veut plus rien dire, trois cent soixante-cinq lignes par an noieraient le centre, et un
     * e-mail quotidien pour annoncer qu'il n'y a rien à faire serait la définition du courrier
     * indésirable. Qui n'a pas le push n'a rien à y gagner.</p>
     */
    public void notifyRestDay(Athlete athlete) {
        User athleteUser = athleteUser(athlete);
        if (athleteUser == null) {
            return;
        }
        pushOnly(athleteUser, "WORKOUT_REMINDER", "Repos demain",
                "Rien de prévu — récupération.", "/athlete/calendar");
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
     * <p>Pas d'e-mail : le canal est trop lent pour un ressenti à chaud. Mais une trace au centre
     * de notifications, désormais — le rappel était en push pur, si bien qu'un athlète ayant
     * refusé les notifications système n'avait <b>aucun</b> chemin vers sa feuille de ressenti,
     * et que celui qui les acceptait perdait le lien dès qu'il balayait la notification. La ligne
     * périme vite ; c'est la purge du centre qui s'en charge, pas son absence.</p>
     *
     * @param feedbackPath chemin front portant déjà un paramètre de requête (les actions y
     *                     ajoutent {@code &rpe=…}), ex. {@code /athlete/today?feedback=<id>}
     */
    public void notifySessionDebrief(User athleteUser, String sessionTitle, String feedbackPath) {
        if (athleteUser == null) {
            return;
        }
        List<PushNotificationService.QuickAction> actions = List.of(
                quickAction(3, "Facile", feedbackPath),
                quickAction(6, "Moyen", feedbackPath),
                quickAction(8, "Dur", feedbackPath));
        record(athleteUser.getId(), "SESSION_DEBRIEF", "Ta séance est finie ?",
                sessionTitle + " — note ton ressenti.", feedbackPath);
        pushOnly(athleteUser, "SESSION_DEBRIEF", "Ta séance est finie ?",
                sessionTitle + " — note ton ressenti en un tap.", feedbackPath, actions);
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
            String athleteName = fullName(athlete);
            String period = fr(unavailability.getStartDate()) + " → " + fr(unavailability.getEndDate());
            String reason = reasonLabel(unavailability.getReason());

            notifyUser(coach, "ATHLETE_UNAVAILABILITY", "Indisponibilité déclarée",
                    athleteName + " — " + reason + " (" + period + ")",
                    athleteName + " — " + reason, "/app/calendar");
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
            String athleteName = fullName(athlete);
            notifyUser(coach, "HEALTH_CONSENT_WITHDRAWN", "Consentement santé retiré",
                    athleteName + " ne partage plus ses données de santé (douleur, lactate).",
                    athleteName + " ne partage plus ses données de santé.",
                    "/app/athletes/" + athlete.getId());
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
