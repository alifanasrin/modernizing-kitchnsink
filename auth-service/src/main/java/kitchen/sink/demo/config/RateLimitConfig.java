package kitchen.sink.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import jakarta.servlet.http.HttpServletRequest;
import kitchen.sink.demo.filter.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    static final String AUTHORIZE_PATH = "/oauth2/authorize";

    // Fixed limits: they do not differ between environments, so they are code, not configuration.
    /** Length of every rate-limit window, in seconds. */
    public static final long WINDOW_SECONDS = 60;
    /** Requests per IP and window for every endpoint except {@value #AUTHORIZE_PATH}. */
    public static final int MAX_REQUESTS = 40;
    /** Requests per IP and window for {@value #AUTHORIZE_PATH}, which writes a document on every call. */
    public static final int AUTHORIZE_MAX_REQUESTS = 60;

    /** A bucket that holds {@code max} tokens and refills them all every {@code windowSeconds}. */
    public static BucketConfiguration limit(int max, long windowSeconds) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(max)
                        .refillIntervally(max, Duration.ofSeconds(windowSeconds))
                        .build())
                .build();
    }

    /** Creates the Valkey-backed store for the rate-limit buckets. */
    @Bean
    ProxyManager<String> rateLimitProxyManager(LettuceConnectionFactory connectionFactory) {
        RedisClient client = (RedisClient) connectionFactory.getNativeClient();
        return LettuceBasedProxyManager.builderFor(client)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(WINDOW_SECONDS)))
                .build()
                .withMapper((String ip) -> ip.getBytes(StandardCharsets.UTF_8));
    }

    /** Creates the per-IP rate-limit filter that runs before Spring Security. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(ProxyManager<String> proxyManager) {
        BucketConfiguration configuration = limit(MAX_REQUESTS, WINDOW_SECONDS);
        log.info("rate limit: {} requests per {} s per IP (all endpoints but {})", MAX_REQUESTS, WINDOW_SECONDS, AUTHORIZE_PATH);
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
                new RateLimitFilter(ip -> proxyManager.builder().build(ip, () -> configuration),
                        // the authorization endpoint has its own, separate bucket
                        (HttpServletRequest r) -> !AUTHORIZE_PATH.equals(r.getRequestURI())));
        bean.addUrlPatterns("/*");
        // runs before Spring Security (-100) so unauthenticated floods and login guessing are throttled too
        bean.setOrder(-101);
        return bean;
    }

    /** Creates the per-IP limit of {@code GET /oauth2/authorize}, which writes a document on every call. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> authorizeRateLimitFilter(ProxyManager<String> proxyManager) {
        BucketConfiguration configuration = limit(AUTHORIZE_MAX_REQUESTS, WINDOW_SECONDS);
        log.info("rate limit: {} requests per {} s per IP on {}", AUTHORIZE_MAX_REQUESTS, WINDOW_SECONDS, AUTHORIZE_PATH);
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
                new RateLimitFilter(ip -> proxyManager.builder().build("authorize:" + ip, () -> configuration)));
        bean.addUrlPatterns(AUTHORIZE_PATH);
        bean.setOrder(-102);
        return bean;
    }
}
