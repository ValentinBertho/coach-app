package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequest;
import com.coachrun.dto.response.ClubResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.service.AdminClubService;
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

@Tag(name = "Admin — Clubs")
@RestController
@RequestMapping("/admin/clubs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminClubController {

    private final AdminClubService adminClubService;

    @GetMapping
    public PageResponse<ClubResponse> list(@RequestParam(required = false) String q,
                                           @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return adminClubService.list(q, pageable);
    }

    @GetMapping("/{id}")
    public ClubResponse get(@PathVariable UUID id) {
        return adminClubService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClubResponse create(@Valid @RequestBody ClubRequest request) {
        return adminClubService.create(request);
    }

    @PutMapping("/{id}")
    public ClubResponse update(@PathVariable UUID id, @Valid @RequestBody ClubRequest request) {
        return adminClubService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminClubService.delete(id);
    }
}
