package com.coachrun.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Sécurité stateless : JWT, CORS configurable, headers durcis.
 * Routes publiques : /public/**, /actuator/health, documentation OpenAPI.
 * Tout le reste est authentifié. Le scoping tenant (@PreAuthorize) viendra avec les ressources.
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/public/**",
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Dispatchs internes au conteneur, qu'aucun client ne peut émettre : la
                        // requête a déjà passé l'autorisation sur son dispatch REQUEST.
                        //
                        // ASYNC — un flux SSE se termine par un dispatch asynchrone, sur une
                        // réponse committée depuis longtemps (en-têtes et premier événement
                        // partis). Réautoriser là ne protège rien, et échoue dès que le jeton
                        // porté en paramètre a cessé d'être valable entre-temps — il l'est
                        // quinze minutes en production (JWT_ACCESS_TTL) pour un flux qui peut
                        // durer une demi-heure, et une déconnexion ou un changement de mot de
                        // passe le périment plus tôt encore. Le refus se solde alors par un 401
                        // impossible à écrire — « Unable to handle the Spring Security Exception
                        // because the response is already committed » — puis par la page
                        // d'erreur qui échoue à son tour. Trois ERROR par fermeture de flux,
                        // sans que personne ne voie rien.
                        //
                        // ERROR — le forward vers /error part du conteneur, hors contexte de
                        // sécurité ; le refuser empilait une seconde erreur sur la première,
                        // qu'elle masquait.
                        //
                        // Un appel direct à /error reste authentifié : il arrive en dispatch
                        // REQUEST, que ce matcher ne couvre pas.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::deny)
                        .permissionsPolicy(pp -> pp.policy(
                                "geolocation=(), microphone=(), camera=()")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.origins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
