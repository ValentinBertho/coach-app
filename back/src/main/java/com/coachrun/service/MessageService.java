package com.coachrun.service;

import com.coachrun.dto.request.MessageRequest;
import com.coachrun.dto.response.MessageResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Message;
import com.coachrun.entity.User;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.MessageRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Messagerie coach ↔ athlète (fil par athlète). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final MessageStreamService streamService;

    // --- Côté coach (scopé club) ---
    public List<MessageResponse> coachThread(UUID clubId, UUID athleteId) {
        return messageRepository.findByClubIdAndAthleteIdOrderByCreatedAtAsc(clubId, athleteId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse coachSend(UUID clubId, UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete, principal, request));
    }

    // --- Côté athlète (scopé athleteId du principal) ---
    public List<MessageResponse> athleteThread(UUID athleteId) {
        return messageRepository.findByAthleteIdOrderByCreatedAtAsc(athleteId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse athleteSend(UUID athleteId, AuthPrincipal principal, MessageRequest request) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return MessageResponse.from(persist(athlete, principal, request));
    }

    private Message persist(Athlete athlete, AuthPrincipal principal, MessageRequest request) {
        User sender = userRepository.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Expéditeur introuvable."));
        Message m = new Message();
        m.setClub(athlete.getClub());
        m.setAthlete(athlete);
        m.setSenderUserId(sender.getId());
        m.setSenderRole(sender.getRole());
        m.setSenderName(sender.getFullName());
        m.setBody(request.body());
        m.setWorkoutId(request.workoutId());
        Message saved = messageRepository.save(m);
        streamService.broadcast(athlete.getId(), MessageResponse.from(saved));
        return saved;
    }
}
