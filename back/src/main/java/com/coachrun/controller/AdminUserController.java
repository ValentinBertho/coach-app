package com.coachrun.controller;

import com.coachrun.dto.request.AdminUserCreateRequest;
import com.coachrun.dto.request.AdminUserUpdateRequest;
import com.coachrun.dto.response.AdminUserResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Utilisateurs")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) UserRole role,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(required = false) String q,
                                                @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return adminUserService.list(role, status, q, pageable);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return adminUserService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.create(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminUserService.delete(id);
    }

    /** Rattache un club additionnel à un coach (modèle multi-club). */
    @PutMapping("/{id}/clubs/{clubId}")
    public AdminUserResponse addClub(@PathVariable UUID id, @PathVariable UUID clubId) {
        return adminUserService.addClub(id, clubId);
    }

    @DeleteMapping("/{id}/clubs/{clubId}")
    public AdminUserResponse removeClub(@PathVariable UUID id, @PathVariable UUID clubId) {
        return adminUserService.removeClub(id, clubId);
    }
}
