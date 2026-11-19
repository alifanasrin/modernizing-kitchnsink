package kitchen.sink.member.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Writes 401/403 in the same JSON shape as {@code ErrorResponse}, since these happen before the controller advice. */
public final class RestAuthenticationHandlers {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationHandlers.class);

    private RestAuthenticationHandlers() {
    }

    /** Returns the handler that answers unauthenticated requests with a 401 JSON error. */
    public static AuthenticationEntryPoint entryPoint() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) -> {
            log.warn("unauthenticated request {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            // keeps the WWW-Authenticate: Bearer header
            bearer.commence(request, response, ex);
            write(response, 401, "Unauthorized");
        };
    }

    /** Returns the handler that answers forbidden requests with a 403 JSON error. */
    public static AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) -> {
            log.warn("access denied for {} {}", request.getMethod(), request.getRequestURI());
            write(response, 403, "Access denied");
        };
    }

    /** Writes a JSON error body with the given status and message. */
    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\",\"timestamp\":\""
                + LocalDateTime.now() + "\"}");
    }
}
