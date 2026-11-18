package kitchen.sink.demo.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitFilterTest {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(ip -> buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillIntervally(100, Duration.ofMinutes(1)).build())
                .build()));
    }

    private MockHttpServletResponse call(RateLimitFilter f, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/members");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void allows100ThenBlocks() throws Exception {
        for (int i = 0; i < 100; i++) assertEquals(200, call(filter, "1.1.1.1").getStatus());
        MockHttpServletResponse res = call(filter, "1.1.1.1");
        assertEquals(429, res.getStatus());
        assertNotNull(res.getHeader("Retry-After"));
    }

    @Test
    void ipsAreIndependent() throws Exception {
        for (int i = 0; i < 101; i++) call(filter, "1.1.1.1");
        assertEquals(200, call(filter, "2.2.2.2").getStatus());
    }

    @Test
    void rejectsWith503WhenStoreDown() throws Exception {
        RateLimitFilter broken = new RateLimitFilter(ip -> { throw new RuntimeException("down"); });
        assertEquals(503, call(broken, "1.1.1.1").getStatus());
    }

    @Test
    void requestsOutsideTheScopeAreNotCounted() throws Exception {
        RateLimitFilter scoped = new RateLimitFilter(ip -> { throw new RuntimeException("must not be used"); },
                r -> !"/oauth2/authorize".equals(r.getRequestURI()));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/oauth2/authorize");
        req.setRemoteAddr("1.1.1.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        scoped.doFilter(req, res, new MockFilterChain());
        assertEquals(200, res.getStatus());
    }
}
