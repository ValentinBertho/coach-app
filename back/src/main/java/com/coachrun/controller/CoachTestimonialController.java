package com.coachrun.controller;

import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachTestimonialService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Témoignages : l'athlète écrit sur son coach, le coach publie. */
@Tag(name = "Témoignages")
@RestController
@RequiredArgsConstructor
public class CoachTestimonialController {

    private final CoachTestimonialService service;

    public record TestimonialBody(String body) {
    }

    @GetMapping("/me/coach/testimonial")
    @PreAuthorize("hasRole('ATHLETE')")
    public ResponseEntity<CoachTestimonialService.Mine> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.mine(principal.userId()).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/me/coach/testimonial")
    @PreAuthorize("hasRole('ATHLETE')")
    public CoachTestimonialService.Mine write(@AuthenticationPrincipal AuthPrincipal principal,
                                              @RequestBody TestimonialBody body) {
        return service.write(principal.userId(), body.body());
    }

    @DeleteMapping("/me/coach/testimonial")
    @PreAuthorize("hasRole('ATHLETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthPrincipal principal) {
        service.withdraw(principal.userId());
    }

    @GetMapping("/me/testimonials")
    @PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
    public List<CoachTestimonialService.ForCoach> received(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.received(principal.userId());
    }

    @PostMapping("/me/testimonials/{id}/publish")
    @PreAuthorize("hasAnyRole('COACH','HEAD_COACH')")
    public CoachTestimonialService.ForCoach publish(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable UUID id,
                                                    @RequestParam(defaultValue = "true") boolean published) {
        return service.publish(principal.userId(), id, published);
    }
}
