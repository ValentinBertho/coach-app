package com.coachrun.service;

import com.coachrun.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * RAZ démo : purge puis recharge le jeu de démo. Garde-fous : interdit en prod,
 * désactivé par défaut (app.demo.reset.enabled), réservé au PLATFORM_ADMIN (contrôleur).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private final DemoSeedService demoSeedService;
    private final Environment environment;

    @Value("${app.demo.reset.enabled:false}")
    private boolean resetEnabled;

    public void reset() {
        if (isProd()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RAZ démo interdite en production.");
        }
        if (!resetEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "RAZ démo désactivée (app.demo.reset.enabled=false).");
        }
        log.warn("[RAZ démo] Déclenchée par un administrateur.");
        demoSeedService.reset();
    }

    public boolean isAvailable() {
        return resetEnabled && !isProd();
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
