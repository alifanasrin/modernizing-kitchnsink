package kitchen.sink.demo.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Per-IP token-bucket rate limit (bucket4j). Buckets are resolved by client IP through the given
 * function, which in production is backed by Valkey. Fails closed (503) if the store is unreachable.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Function<String, Bucket> bucketResolver;

    private final Predicate<HttpServletRequest> appliesTo;

    public RateLimitFilter(Function<String, Bucket> bucketResolver) {
        this(bucketResolver, request -> true);
    }

    /** Limits only the requests {@code appliesTo} accepts; the others pass untouched. */
    public RateLimitFilter(Function<String, Bucket> bucketResolver, Predicate<HttpServletRequest> appliesTo) {
        this.bucketResolver = bucketResolver;
        this.appliesTo = appliesTo;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !appliesTo.test(request);
    }

    /** Passes the request on if the IP has a token left, else answers 429 (503 if the store is down). */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        ConsumptionProbe probe;
        try {
            probe = bucketResolver.apply(ip).tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            log.error("Rate limit store unavailable, rejecting request from {}: {}", ip, e.getMessage());
            writeError(response, 503, "Service temporarily unavailable, please try again later");
            return;
        }
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        log.warn("Rate limit exceeded for {}; retry in {}s", ip, retryAfter);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        writeError(response, 429, "Too many requests from this IP address, try again later");
    }

    /** Writes a JSON error response with the given status and message. */
    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\",\"timestamp\":\""
                + LocalDateTime.now() + "\"}");
    }
}
