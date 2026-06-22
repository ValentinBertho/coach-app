package com.coachrun.service;

import com.coachrun.dto.request.AdminUserCreateRequest;
import com.coachrun.dto.request.AdminUserUpdateRequest;
import com.coachrun.dto.response.AdminUserResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Administration des comptes utilisateurs (PLATFORM_ADMIN). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<AdminUserResponse> list(UserRole role, UserStatus status, String q, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim();
        return PageResponse.from(userRepository.searchAdmin(role, status, query, pageable),
                AdminUserResponse::from);
    }

    public AdminUserResponse get(UUID id) {
        return AdminUserResponse.from(require(id));
    }

    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Un compte existe déjà avec cet email.");
        }
        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        applyClub(user, request.role(), request.clubId());
        return AdminUserResponse.from(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse update(UUID id, AdminUserUpdateRequest request) {
        User user = require(id);
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.clubId() != null) {
            applyClub(user, user.getRole(), request.clubId());
        }
        return AdminUserResponse.from(user);
    }

    @Transactional
    public void delete(UUID id) {
        userRepository.delete(require(id));
    }

    private void applyClub(User user, UserRole role, UUID clubId) {
        if (role == UserRole.HEAD_COACH || role == UserRole.COACH) {
            UUID effective = clubId;
            if (effective == null) {
                throw new ConflictException("Un coach doit être rattaché à un club.");
            }
            user.setClub(clubRepository.findById(effective)
                    .orElseThrow(() -> new NotFoundException("Club introuvable.")));
        } else if (clubId != null) {
            user.setClub(clubRepository.findById(clubId)
                    .orElseThrow(() -> new NotFoundException("Club introuvable.")));
        }
    }

    private User require(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
    }
}
