package com.coachrun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée de l'API CoachRun (plateforme de coaching course à pied).
 */
@EnableScheduling
@SpringBootApplication
public class CoachRunApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoachRunApplication.class, args);
    }
}
