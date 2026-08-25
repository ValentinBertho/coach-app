package com.coachrun.service;

import com.coachrun.dto.response.ConversationSummaryResponse;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.dto.response.RecipientResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.Conversation;
import com.coachrun.entity.ConversationRead;
import com.coachrun.entity.Message;
import com.coachrun.entity.TrainingGroup;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ConversationKind;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.ConversationReadRepository;
import com.coachrun.repository.ConversationRepository;
import com.coachrun.repository.MessageRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AthleteAccessValidator;
import com.coachrun.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les fils de discussion : qui parle à qui, qui a le droit de lire, et qui a le droit d'écrire.
 *
 * <p><b>Le défaut d'origine.</b> Il n'existait pas de fil, seulement un athlète : tous les messages
 * le concernant tombaient dans le même tas, lisible par n'importe quel coach ayant accès à lui. Un
 * responsable de club a ainsi lu les échanges du propriétaire avec ses athlètes. Le cloisonnement
 * n'était pas troué : il n'existait pas.</p>
 *
 * <p><b>La règle, désormais.</b> Un fil par binôme athlète↔coach — deux coachs qui suivent le même
 * athlète ne se lisent pas. Un fil par paire de coachs. Un fil par groupe. Un fil par club.
 * L'appartenance n'est jamais stockée : elle se <b>déduit</b> à chaque lecture de l'état courant
 * (relation référente, permissions, composition du groupe, membres du club). Une table de
 * participants aurait vieilli — un coach retiré du club serait resté abonné à un fil.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationReadRepository readRepository;
    private final MessageRepository messageRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final TrainingGroupRepository groupRepository;
    private final AthleteAccessValidator accessValidator;

    // --- Ouvrir un fil -------------------------------------------------------------------------

    /**
     * Le fil d'un binôme, créé au besoin.
     *
     * <p>Passe par {@code dedupKey} sous contrainte d'unicité : deux clients qui ouvrent la même
     * conversation au même instant n'en créent qu'une.</p>
     */
    @Transactional
    public Conversation athleteCoach(UUID athleteId, UUID coachUserId) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        String key = Conversation.athleteCoachKey(athleteId, coachUserId);
        return conversationRepository.findByDedupKey(key).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setClub(athlete.getClub());
            c.setKind(ConversationKind.ATHLETE_COACH);
            c.setAthlete(athlete);
            c.setCoachUserId(coachUserId);
            c.setDedupKey(key);
            return conversationRepository.save(c);
        });
    }

    /** Le fil entre deux coachs du même club. */
    @Transactional
    public Conversation coachCoach(UUID clubId, UUID a, UUID b) {
        if (a.equals(b)) {
            throw new ConflictException("On ne s'écrit pas à soi-même.");
        }
        String key = Conversation.coachCoachKey(a, b);
        return conversationRepository.findByDedupKey(key).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setClub(clubRepository.getReferenceById(clubId));
            c.setKind(ConversationKind.COACH_COACH);
            // Ordre stable : (a,b) et (b,a) sont le même fil, et la clé le dit déjà.
            c.setPeerAUserId(a.compareTo(b) <= 0 ? a : b);
            c.setPeerBUserId(a.compareTo(b) <= 0 ? b : a);
            c.setDedupKey(key);
            return conversationRepository.save(c);
        });
    }

    /** Le fil d'un groupe d'entraînement. */
    @Transactional
    public Conversation group(UUID groupId) {
        TrainingGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        String key = Conversation.groupKey(groupId);
        return conversationRepository.findByDedupKey(key).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setClub(group.getClub());
            c.setKind(ConversationKind.GROUP);
            c.setGroup(group);
            c.setDedupKey(key);
            return conversationRepository.save(c);
        });
    }

    /** Le fil du club (annonces). */
    @Transactional
    public Conversation club(UUID clubId) {
        String key = Conversation.clubKey(clubId);
        return conversationRepository.findByDedupKey(key).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setClub(clubRepository.getReferenceById(clubId));
            c.setKind(ConversationKind.CLUB);
            c.setDedupKey(key);
            return conversationRepository.save(c);
        });
    }

    // --- Qui lit, qui écrit --------------------------------------------------------------------

    /**
     * Cette personne participe-t-elle à ce fil ?
     *
     * <p>Réévalué à chaque appel sur l'état courant : un coach qui perd l'accès à un athlète perd
     * l'accès au fil du binôme le jour même, sans qu'aucune table d'abonnés n'ait à être tenue.</p>
     */
    public boolean canRead(AuthPrincipal principal, Conversation conversation) {
        if (principal == null || conversation == null) {
            return false;
        }
        UUID userId = principal.userId();
        return switch (conversation.getKind()) {
            case ATHLETE_COACH -> isTheAthlete(principal, conversation)
                    || (userId.equals(conversation.getCoachUserId())
                        && accessValidator.effectiveLevel(userId, conversation.getAthlete().getId()).isPresent());
            case COACH_COACH -> userId.equals(conversation.getPeerAUserId())
                    || userId.equals(conversation.getPeerBUserId());
            case GROUP -> {
                TrainingGroup group = conversation.getGroup();
                if (group == null) {
                    yield false;
                }
                yield principal.role() == UserRole.ATHLETE
                        ? athleteBelongsToGroup(principal.athleteId(), group.getId())
                        : isClubMember(principal, group.getClub().getId()) && group.isVisibleTo(userId);
            }
            case CLUB -> inClub(principal, conversation.getClub().getId());
        };
    }

    /**
     * Peut-elle y écrire ?
     *
     * <p>Un seul cas se distingue de la lecture : le fil du <b>club</b> est un canal d'annonces.
     * Tout le club le lit, les coachs y écrivent — sans quoi ce serait un forum de club, ce qui
     * demande une modération que personne n'a le temps d'assurer.</p>
     */
    public boolean canPost(AuthPrincipal principal, Conversation conversation) {
        if (!canRead(principal, conversation)) {
            return false;
        }
        if (conversation.getKind() == ConversationKind.CLUB) {
            return principal.role() != UserRole.ATHLETE;
        }
        return true;
    }

    /** Le fil, si cette personne y a sa place. Sinon, il n'existe pas pour elle. */
    public Conversation requireReadable(AuthPrincipal principal, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation introuvable."));
        if (!canRead(principal, conversation)) {
            // Volontairement « introuvable » et non « interdit » : répondre 403 confirmerait
            // l'existence d'un fil entre deux tiers.
            throw new NotFoundException("Conversation introuvable.");
        }
        return conversation;
    }

    private boolean isTheAthlete(AuthPrincipal principal, Conversation conversation) {
        return principal.athleteId() != null && conversation.getAthlete() != null
                && principal.athleteId().equals(conversation.getAthlete().getId());
    }

    private boolean athleteBelongsToGroup(UUID athleteId, UUID groupId) {
        return athleteId != null && athleteRepository.findById(athleteId)
                .map(a -> a.getGroup() != null && groupId.equals(a.getGroup().getId()))
                .orElse(false);
    }

    private boolean isClubMember(AuthPrincipal principal, UUID clubId) {
        return principal.role() != UserRole.ATHLETE
                && clubMemberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, principal.userId()).isPresent();
    }

    /** Appartenance au club, coach comme athlète — la base du fil d'annonces. */
    private boolean inClub(AuthPrincipal principal, UUID clubId) {
        if (principal.role() == UserRole.ATHLETE) {
            return principal.athleteId() != null && athleteRepository.findById(principal.athleteId())
                    .map(a -> a.getClub() != null && clubId.equals(a.getClub().getId()))
                    .orElse(false);
        }
        return isClubMember(principal, clubId)
                || (principal.clubId() != null && principal.clubId().equals(clubId));
    }

    // --- Boîte de réception --------------------------------------------------------------------

    /**
     * Tous les fils de cette personne, du plus récent au plus ancien.
     *
     * <p>Les fils collectifs (groupe, club) apparaissent même vides : c'est ainsi qu'on découvre
     * qu'on peut y écrire. Les fils de binôme, eux, n'apparaissent qu'une fois entamés — sans quoi
     * un coach de club en verrait cent, tous identiques.</p>
     *
     * <p><b>En écriture, malgré son nom.</b> Lister matérialise les fils collectifs qui n'existent
     * pas encore. Sous la transaction en lecture seule de la classe, Hibernate passe en flush
     * manuel : le fil serait créé en mémoire, recevrait un identifiant, et n'atteindrait jamais la
     * base — l'écran afficherait un fil dont l'identifiant ne désigne rien.</p>
     */
    @Transactional
    public List<ConversationSummaryResponse> inbox(AuthPrincipal principal) {
        List<Conversation> candidates = new ArrayList<>(mine(principal));
        List<ConversationSummaryResponse> out = new ArrayList<>();
        for (Conversation c : candidates) {
            Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId())
                    .orElse(null);
            boolean collective = c.getKind() == ConversationKind.GROUP
                    || c.getKind() == ConversationKind.CLUB;
            if (last == null && !collective) {
                continue;
            }
            out.add(summary(principal, c, last));
        }
        out.sort(Comparator.comparing(
                (ConversationSummaryResponse s) -> s.lastMessageAt() == null ? Instant.EPOCH : s.lastMessageAt())
                .reversed());
        return out;
    }

    /** Somme des non-lus, tous fils confondus : la pastille de la navigation. */
    @Transactional
    public long unreadCount(AuthPrincipal principal) {
        long total = 0;
        for (Conversation c : mine(principal)) {
            total += unreadFor(principal.userId(), c);
        }
        return total;
    }

    /** Les fils auxquels cette personne participe aujourd'hui. */
    private List<Conversation> mine(AuthPrincipal principal) {
        Map<UUID, Conversation> found = new LinkedHashMap<>();
        if (principal.role() == UserRole.ATHLETE) {
            if (principal.athleteId() == null) {
                return List.of();
            }
            conversationRepository.findByAthleteId(principal.athleteId())
                    .forEach(c -> found.put(c.getId(), c));
            athleteRepository.findById(principal.athleteId()).ifPresent(a -> {
                if (a.getGroup() != null) {
                    found.put(group(a.getGroup().getId()).getId(), group(a.getGroup().getId()));
                }
                if (a.getClub() != null) {
                    Conversation club = club(a.getClub().getId());
                    found.put(club.getId(), club);
                }
            });
        } else {
            conversationRepository.findByCoachUserId(principal.userId())
                    .forEach(c -> found.put(c.getId(), c));
            conversationRepository.findByPeerAUserIdOrPeerBUserId(principal.userId(), principal.userId())
                    .forEach(c -> found.put(c.getId(), c));
            for (UUID clubId : coachClubs(principal)) {
                for (TrainingGroup g : groupRepository.findByClubIdOrderByNameAsc(clubId)) {
                    if (g.isVisibleTo(principal.userId())) {
                        Conversation conv = group(g.getId());
                        found.put(conv.getId(), conv);
                    }
                }
                Conversation club = club(clubId);
                found.put(club.getId(), club);
            }
        }
        // Dernier filtre : la règle d'accès a le dernier mot, y compris sur d'anciens fils.
        return found.values().stream().filter(c -> canRead(principal, c)).toList();
    }

    /** Clubs d'un coach : son club principal et ceux dont il est membre. */
    private List<UUID> coachClubs(AuthPrincipal principal) {
        Map<UUID, Boolean> clubs = new LinkedHashMap<>();
        if (principal.clubId() != null) {
            clubs.put(principal.clubId(), true);
        }
        clubMemberRepository.findByCoachIdAndActiveTrue(principal.userId())
                .forEach(m -> clubs.put(m.getClub().getId(), true));
        return List.copyOf(clubs.keySet());
    }

    /**
     * Le résumé d'un fil, pour qui y participe.
     *
     * <p>Rendu tel quel par l'ouverture d'une conversation. Le relire dans la boîte de réception
     * ne marchait pas : elle masque volontairement les fils de binôme vides — sans quoi un coach
     * de club en verrait cent, tous identiques — donc un fil qu'on venait de créer n'y figurait
     * pas, et « Nouveau message » répondait « introuvable » à tous les coups.</p>
     */
    public ConversationSummaryResponse summaryOf(AuthPrincipal principal, UUID conversationId) {
        Conversation conversation = requireReadable(principal, conversationId);
        return summary(principal, conversation,
                messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId)
                        .orElse(null));
    }

    private ConversationSummaryResponse summary(AuthPrincipal principal, Conversation c, Message last) {
        String title;
        String subtitle = null;
        switch (c.getKind()) {
            case ATHLETE_COACH -> {
                if (principal.role() == UserRole.ATHLETE) {
                    User coach = userRepository.findById(c.getCoachUserId()).orElse(null);
                    title = coach == null ? "Coach" : coach.getFullName();
                    subtitle = "Coach";
                } else {
                    Athlete a = c.getAthlete();
                    title = a.getFirstName() + " " + a.getLastName();
                    subtitle = "Athlète";
                }
            }
            case COACH_COACH -> {
                UUID other = principal.userId().equals(c.getPeerAUserId())
                        ? c.getPeerBUserId() : c.getPeerAUserId();
                title = userRepository.findById(other).map(User::getFullName).orElse("Coach");
                subtitle = "Coach";
            }
            case GROUP -> {
                title = c.getGroup() == null ? "Groupe" : c.getGroup().getName();
                subtitle = "Groupe";
            }
            case CLUB -> {
                title = c.getClub().getName();
                subtitle = "Annonces du club";
            }
            default -> title = "Conversation";
        }
        return new ConversationSummaryResponse(
                c.getId(), c.getKind(), title, subtitle,
                c.getAthlete() == null ? null : c.getAthlete().getId(),
                c.getGroup() == null ? null : c.getGroup().getId(),
                last == null ? null : last.getBody(),
                last == null ? null : last.getSenderName(),
                last == null ? null : last.getCreatedAt(),
                unreadFor(principal.userId(), c),
                canPost(principal, c));
    }

    /** Ce qui est arrivé dans ce fil depuis le dernier passage de cette personne, sans compter le sien. */
    private long unreadFor(UUID userId, Conversation c) {
        Instant since = readRepository.findByConversationIdAndUserId(c.getId(), userId)
                .map(ConversationRead::getLastReadAt)
                .orElse(null);
        return since == null
                ? messageRepository.countByConversationIdAndSenderUserIdNot(c.getId(), userId)
                : messageRepository.countByConversationIdAndSenderUserIdNotAndCreatedAtAfter(
                        c.getId(), userId, since);
    }

    // --- Lire et écrire ------------------------------------------------------------------------

    public List<MessageResponse> messages(AuthPrincipal principal, UUID conversationId, int limit) {
        Conversation conversation = requireReadable(principal, conversationId);
        List<Message> page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversation.getId(), PageRequest.of(0, MessageService.threadLimit(limit)));
        List<MessageResponse> out = new ArrayList<>(page.size());
        for (int i = page.size() - 1; i >= 0; i--) {
            out.add(MessageResponse.from(page.get(i)));
        }
        return out;
    }

    /** Accusé de lecture : cette personne a ouvert ce fil, à cet instant. */
    @Transactional
    public void markRead(AuthPrincipal principal, UUID conversationId) {
        Conversation conversation = requireReadable(principal, conversationId);
        ConversationRead read = readRepository
                .findByConversationIdAndUserId(conversation.getId(), principal.userId())
                .orElseGet(() -> {
                    ConversationRead r = new ConversationRead();
                    r.setConversation(conversation);
                    r.setUserId(principal.userId());
                    return r;
                });
        read.setLastReadAt(Instant.now());
        readRepository.save(read);
    }

    /** Le fil doit être ouvert en écriture, et pas seulement lisible. */
    public Conversation requireWritable(AuthPrincipal principal, UUID conversationId) {
        Conversation conversation = requireReadable(principal, conversationId);
        if (!canPost(principal, conversation)) {
            throw new ConflictException("Ce fil est en lecture seule pour vous.");
        }
        return conversation;
    }

    // --- « Nouveau message » : à qui ? ----------------------------------------------------------

    /**
     * Les destinataires possibles.
     *
     * <p>Calculée ici et non déduite côté client d'une liste d'athlètes ou de membres : c'est cette
     * méthode qui dit à qui l'on a le droit d'écrire, et l'envoi revérifie la même règle.</p>
     *
     * <p>Un coach écrit à tout coach de son club, et aux athlètes de son périmètre. Un athlète
     * écrit aux coachs qui ont accès à lui — pas à tout le club : quelqu'un qui ne le suit pas n'a
     * pas de raison de recevoir ses questions d'entraînement.</p>
     */
    public List<RecipientResponse> recipients(AuthPrincipal principal) {
        List<RecipientResponse> out = new ArrayList<>();
        if (principal.role() == UserRole.ATHLETE) {
            if (principal.athleteId() == null) {
                return out;
            }
            Athlete me = athleteRepository.findById(principal.athleteId()).orElse(null);
            if (me == null || me.getClub() == null) {
                return out;
            }
            for (ClubMember m : clubMemberRepository.findByClubIdAndActiveTrue(me.getClub().getId())) {
                User coach = m.getCoach();
                if (accessValidator.effectiveLevel(coach.getId(), me.getId()).isPresent()) {
                    out.add(new RecipientResponse("COACH", coach.getId(), coach.getFullName(),
                            roleLabel(m)));
                }
            }
            return out;
        }

        for (UUID clubId : coachClubs(principal)) {
            for (ClubMember m : clubMemberRepository.findByClubIdAndActiveTrue(clubId)) {
                if (!m.getCoach().getId().equals(principal.userId())) {
                    out.add(new RecipientResponse("COACH", m.getCoach().getId(),
                            m.getCoach().getFullName(), roleLabel(m)));
                }
            }
            for (Athlete a : athleteRepository.findByClubIdOrderByLastNameAsc(clubId)) {
                if (a.getStatus() == AthleteStatus.ARCHIVED) {
                    continue;
                }
                if (accessValidator.effectiveLevel(principal.userId(), a.getId()).isPresent()) {
                    out.add(new RecipientResponse("ATHLETE", a.getId(),
                            a.getFirstName() + " " + a.getLastName(), "Athlète"));
                }
            }
        }
        return out;
    }

    private static String roleLabel(ClubMember m) {
        return switch (m.getClubRole()) {
            case OWNER -> "Propriétaire du club";
            case COACH_PRINCIPAL -> "Coach principal";
            case COACH_ASSISTANT -> "Coach assistant";
        };
    }

    /**
     * Ouvre — ou retrouve — le fil vers un destinataire choisi dans « Nouveau message ».
     *
     * <p>La liste des destinataires est revérifiée : un identifiant deviné dans la requête ne doit
     * pas ouvrir un fil que l'écran n'aurait pas proposé.</p>
     */
    /**
     * Ouvre — ou retrouve — le fil désigné, quelle qu'en soit la nature, et le rend prêt à
     * l'emploi.
     *
     * <p>Les droits sont vérifiés <b>avant</b> toute création : un groupe privé qu'on ne voit pas
     * ne doit pas laisser de fil derrière la tentative.</p>
     */
    @Transactional
    public ConversationSummaryResponse openFor(AuthPrincipal principal, String kind, UUID targetId) {
        Conversation conversation = switch (kind) {
            case "GROUP" -> {
                requireGroupAccess(principal, targetId);
                yield group(targetId);
            }
            case "CLUB" -> {
                if (!inClub(principal, targetId)) {
                    throw new NotFoundException("Club introuvable.");
                }
                yield club(targetId);
            }
            default -> open(principal, kind, targetId);
        };
        return summaryOf(principal, conversation.getId());
    }

    /** Ce groupe existe-t-il pour cette personne ? Un groupe privé n'existe pas pour les autres. */
    private void requireGroupAccess(AuthPrincipal principal, UUID groupId) {
        TrainingGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        boolean allowed = principal.role() == UserRole.ATHLETE
                ? athleteBelongsToGroup(principal.athleteId(), groupId)
                : isClubMember(principal, group.getClub().getId())
                        && group.isVisibleTo(principal.userId());
        if (!allowed) {
            throw new NotFoundException("Groupe introuvable.");
        }
    }

    @Transactional
    public Conversation open(AuthPrincipal principal, String kind, UUID targetId) {
        boolean allowed = recipients(principal).stream()
                .anyMatch(r -> r.kind().equals(kind) && r.id().equals(targetId));
        if (!allowed) {
            throw new NotFoundException("Destinataire introuvable.");
        }
        if ("ATHLETE".equals(kind)) {
            return athleteCoach(targetId, principal.userId());
        }
        if (principal.role() == UserRole.ATHLETE) {
            // Côté athlète, « écrire à un coach » ouvre le fil du binôme, pas un fil coach↔coach.
            return athleteCoach(principal.athleteId(), targetId);
        }
        UUID clubId = principal.clubId() != null ? principal.clubId()
                : coachClubs(principal).stream().findFirst().orElseThrow(
                        () -> new NotFoundException("Aucun club."));
        return coachCoach(clubId, principal.userId(), targetId);
    }

    // --- Qui prévenir d'un nouveau message ------------------------------------------------------

    /**
     * Les personnes à prévenir, l'expéditeur exclu.
     *
     * <p>Même déduction que pour la lecture, et c'est voulu : une notification part exactement à
     * ceux qui auraient pu ouvrir le fil. Une liste d'abonnés tenue à part aurait fini par
     * prévenir un coach qui n'a plus accès à l'athlète.</p>
     */
    public List<User> participantsToNotify(Conversation conversation, UUID senderUserId) {
        List<User> out = new ArrayList<>();
        switch (conversation.getKind()) {
            case ATHLETE_COACH -> {
                userRepository.findById(conversation.getCoachUserId()).ifPresent(out::add);
                athleteUser(conversation.getAthlete()).ifPresent(out::add);
            }
            case COACH_COACH -> {
                userRepository.findById(conversation.getPeerAUserId()).ifPresent(out::add);
                userRepository.findById(conversation.getPeerBUserId()).ifPresent(out::add);
            }
            case GROUP -> {
                TrainingGroup group = conversation.getGroup();
                if (group == null) {
                    break;
                }
                for (Athlete a : athleteRepository.findActiveByGroup(
                        group.getId(), group.getClub().getId(), AthleteStatus.ACTIVE)) {
                    athleteUser(a).ifPresent(out::add);
                }
                for (ClubMember m : clubMemberRepository.findByClubIdAndActiveTrue(group.getClub().getId())) {
                    if (group.isVisibleTo(m.getCoach().getId())) {
                        out.add(m.getCoach());
                    }
                }
            }
            case CLUB -> {
                UUID clubId = conversation.getClub().getId();
                for (Athlete a : athleteRepository.findByClubIdOrderByLastNameAsc(clubId)) {
                    if (a.getStatus() == AthleteStatus.ACTIVE) {
                        athleteUser(a).ifPresent(out::add);
                    }
                }
                clubMemberRepository.findByClubIdAndActiveTrue(clubId)
                        .forEach(m -> out.add(m.getCoach()));
            }
        }
        Map<UUID, User> unique = new LinkedHashMap<>();
        for (User u : out) {
            if (u != null && !u.getId().equals(senderUserId)) {
                unique.put(u.getId(), u);
            }
        }
        return List.copyOf(unique.values());
    }

    private Optional<User> athleteUser(Athlete athlete) {
        return athlete == null ? Optional.empty() : userRepository.findByAthleteId(athlete.getId());
    }

    /** Le fil qu'ouvre un athlète par défaut : celui de son coach référent. */
    public Optional<Conversation> defaultAthleteConversation(UUID athleteId) {
        return conversationRepository.findByAthleteId(athleteId).stream()
                .max(Comparator.comparing(
                        c -> c.getLastMessageAt() == null ? Instant.EPOCH : c.getLastMessageAt()));
    }
}
