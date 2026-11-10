package com.coachrun.security;

import com.coachrun.dto.response.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Toute réponse qui ouvre une session (connexion, inscription, invitation, réinitialisation,
 * rafraîchissement) dépose le jeton de rafraîchissement dans le cookie protégé et le retire du
 * corps JSON. Un seul point de passage : aucune route ne peut l'oublier et laisser le jeton
 * atterrir dans le stockage du navigateur.
 *
 * <p>Une réponse sans jeton (impersonation) ne touche pas au cookie : celui de l'administrateur
 * doit survivre à la session empruntée.</p>
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class AuthResponseCookieAdvice implements ResponseBodyAdvice<Object> {

    private final RefreshCookie refreshCookie;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof AuthResponse auth && auth.refreshToken() != null) {
            response.getHeaders().add(HttpHeaders.SET_COOKIE, refreshCookie.set(auth.refreshToken()));
            return new AuthResponse(auth.accessToken(), null, auth.expiresIn(), auth.user());
        }
        return body;
    }
}
