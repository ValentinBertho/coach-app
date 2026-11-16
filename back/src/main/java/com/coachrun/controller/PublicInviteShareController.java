package com.coachrun.controller;

import com.coachrun.exception.NotFoundException;
import com.coachrun.service.CoachShareService;
import com.coachrun.service.InviteLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Le lien court d'une invitation, {@code /j/<jeton>} : le front le relaie ici pour que les robots de
 * prévisualisation (WhatsApp, SMS, Messenger) lisent un titre et une image, puis la page renvoie
 * vers l'écran « Rejoindre ».
 */
@RestController
@RequiredArgsConstructor
public class PublicInviteShareController {

    private final InviteLinkService invites;
    private final CoachShareService share;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping(value = "/public/share/invite/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable String token) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(share.invitePreviewHtml(invites.preview(token), token));
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, frontendUrl + "/rejoindre/" + token)
                    .build();
        }
    }
}
