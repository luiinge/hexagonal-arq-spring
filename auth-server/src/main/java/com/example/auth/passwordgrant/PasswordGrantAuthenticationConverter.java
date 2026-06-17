package com.example.auth.passwordgrant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Primera parte del pipeline del password grant: convierte la petición HTTP en un token
 * de autenticación que el Provider puede procesar.
 *
 * Spring Authorization Server delega en una cadena de AuthenticationConverter registrados
 * en el token endpoint. Cada converter devuelve null si el grant_type no es el suyo,
 * o lanza una excepción si los parámetros son inválidos. Si devuelve un token no nulo,
 * el framework lo pasa al Provider correspondiente.
 *
 * En este caso: detecta grant_type=password, extrae username/password/scope del body
 * del formulario, y produce un PasswordGrantAuthenticationToken listo para validar.
 */
public class PasswordGrantAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        // Devolver null hace que el framework pruebe con el siguiente converter de la cadena
        if (!"password".equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }

        // El cliente OAuth2 (gateway) ya fue autenticado antes de llegar aquí
        // mediante CLIENT_SECRET_BASIC o CLIENT_SECRET_POST
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

        String username = request.getParameter(OAuth2ParameterNames.USERNAME);
        if (!StringUtils.hasText(username)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        String password = request.getParameter(OAuth2ParameterNames.PASSWORD);
        if (!StringUtils.hasText(password)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        String scopeParam = request.getParameter(OAuth2ParameterNames.SCOPE);
        Set<String> scopes = StringUtils.hasText(scopeParam)
                ? new HashSet<>(Arrays.asList(scopeParam.split(" ")))
                : Collections.emptySet();

        Map<String, Object> additionalParameters = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE)
                    && !key.equals(OAuth2ParameterNames.USERNAME)
                    && !key.equals(OAuth2ParameterNames.PASSWORD)
                    && !key.equals(OAuth2ParameterNames.SCOPE)) {
                additionalParameters.put(key, values[0]);
            }
        });

        return new PasswordGrantAuthenticationToken(clientPrincipal, username, password, scopes, additionalParameters);
    }
}
