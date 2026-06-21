package com.coachrun.controller;

import com.coachrun.dto.response.PingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publics (non authentifiés). Sert pour l'instant uniquement de preuve
 * de bout-en-bout que le front communique avec le back.
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    private final String version;

    public PublicController(@Value("${app.version:dev}") String version) {
        this.version = version;
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("ok", version);
    }
}
