package com.coachrun.exception;

import com.coachrun.config.LogContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire global : réponses d'erreur JSON normalisées.
 * 400 (+ fieldErrors), 500 (+ correlationId, sans fuite de détail). À enrichir avec les features
 * (404 ressource, 409 conflit/transition, 403 module/tenant).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(ex.getStatus().value(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.FORBIDDEN.value(),
                "Accès refusé.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Requête invalide",
                request.getRequestURI(),
                Instant.now(),
                fieldErrors,
                null);
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Corps absent ou illisible. Sans ce cas, le filet à {@code Exception} plus bas transformait
     * un POST sans corps en 500 « erreur interne » — une requête malformée n'est pas une panne du
     * serveur, et le client n'avait aucun moyen de comprendre ce qui manquait.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(),
                "Corps de requête absent ou illisible.", request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Paramètre de chemin ou de requête au mauvais type — typiquement un UUID malformé dans une
     * URL (lien tronqué dans un e-mail, copier-coller partiel, exploration automatisée).
     *
     * <p>Sans ce cas, le filet à {@code Exception} plus bas en faisait une <b>500 « erreur
     * interne »</b> : l'utilisateur voyait une panne serveur pour une URL invalide, et chaque
     * requête de ce genre écrivait une trace et remontait dans Sentry. Or c'est exactement le
     * bruit qui rend inexploitable la règle d'alerte « nouvelle anomalie → e-mail » recommandée
     * pour la bêta : le premier robot d'indexation la déclenche.</p>
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(),
                "Paramètre « " + ex.getName() + " » invalide.", request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    /** Paramètre de requête obligatoire absent : requête malformée, pas panne du serveur. */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(),
                "Paramètre « " + ex.getParameterName() + " » manquant.", request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Pièce jointe au-delà de la limite de téléversement (10 Mo, {@code spring.servlet.multipart}).
     *
     * <p>Le front sait déjà afficher proprement un 413 — le message du quota de stockage passe
     * par là — mais la limite de taille produisait une 500 générique : « Une erreur interne est
     * survenue » pour un fichier trop gros, alors que la cause est parfaitement explicable et que
     * l'utilisateur peut y remédier seul.</p>
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Fichier trop volumineux (10 Mo maximum).", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /** Méthode HTTP non supportée sur cette route : 405, pas 500. */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Méthode " + ex.getMethod() + " non autorisée sur cette ressource.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    /**
     * URL inconnue : 404, pas 500.
     *
     * <p>Faute de gestionnaire, une adresse mal orthographiée tombait dans le filet à
     * {@code Exception} : le client recevait « Une erreur interne est survenue », et chaque appel
     * sur une route inexistante écrivait une stacktrace de niveau ERROR avec un identifiant de
     * corrélation — du bruit qui ressemble à une panne dans les journaux, et un message qui
     * envoie chercher un incident là où il n'y a qu'une URL fausse. On l'a constaté en appelant
     * un point d'entrée depuis un serveur qui ne l'avait pas encore.</p>
     *
     * <p>Pas de journal ici : une 404 est une réponse normale, pas un incident.</p>
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            HttpServletRequest request) {
        ApiError error = ApiError.of(HttpStatus.NOT_FOUND.value(),
                "Ressource introuvable.", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Violation de contrainte en base (unicité, clé étrangère). C'est un conflit métier — deux
     * coachs qui créent la même ressource en même temps, une suppression qui laisserait une
     * référence pendante — et non une panne : 409 plutôt que 500, sans exposer le détail SQL.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violation de contrainte sur {} : {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        ApiError error = ApiError.of(HttpStatus.CONFLICT.value(),
                "Cette opération entre en conflit avec des données existantes.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Fin de vie d'une connexion longue : le flux temps réel a atteint son délai.
     *
     * <p><b>Ce n'est pas une erreur serveur.</b> Les flux SSE — badge de notifications, messagerie
     * — sont ouverts une demi-heure puis expirent ; le navigateur en rouvre un aussitôt. Sans ce
     * gestionnaire, l'expiration tombait dans {@link #handleUnexpected}, qui la journalisait en
     * ERROR avec un identifiant de corrélation et tentait d'écrire un corps JSON sur une réponse
     * déjà partie — ce qui échouait à son tour (« Failure in @ExceptionHandler »). Deux lignes
     * d'alerte à chaque expiration, pour un événement parfaitement normal, remontées à Sentry et
     * aux journaux centralisés où elles noient les vraies.</p>
     *
     * <p>Journalisé en DEBUG : l'information n'a d'intérêt que lorsqu'on enquête précisément sur
     * les flux. Le 503 n'est posé que si la réponse n'est pas encore partie — sur un flux établi,
     * elle l'est toujours, et il n'y a plus rien à écrire.</p>
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(HttpServletRequest request, HttpServletResponse response) {
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        log.debug("Flux expiré sur {} — le client rouvrira.", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request,
                                                     HttpServletResponse response) {
        // Réponse déjà envoyée : aucun corps ne peut plus être écrit, et tenter de le faire
        // produit une seconde erreur qui masque la première. Le cas se présente sur tout ce qui
        // diffuse — flux SSE, téléchargement de pièce jointe, export — où l'échec survient après
        // les premiers octets. On trace sans identifiant de corrélation : il est destiné à être
        // affiché à l'utilisateur, or il ne le verra jamais.
        if (response.isCommitted()) {
            log.warn("Erreur après envoi de la réponse sur {} : {}",
                    request.getRequestURI(), ex.toString());
            return null;
        }

        String correlationId = UUID.randomUUID().toString();
        // Le correlationId est renvoyé à l'utilisateur et capté par le formulaire de retour bêta :
        // il n'a de valeur que si l'on peut le RECHERCHER. Dans le message, c'est du texte noyé ;
        // en MDC, c'est un champ indexé côté Better Stack (cf. logback-spring.xml). Le nettoyage
        // est assuré par LogContextFilter en fin de requête.
        MDC.put(LogContextFilter.CORRELATION_ID, correlationId);
        log.error("Erreur inattendue [{}] sur {}", correlationId, request.getRequestURI(), ex);

        ApiError error = new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Une erreur interne est survenue",
                request.getRequestURI(),
                Instant.now(),
                null,
                correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
