package kitchen.sink.demo;

import com.sun.net.httpserver.HttpServer;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import kitchen.sink.demo.client.MemberServiceClient;
import kitchen.sink.demo.dto.request.CreateMemberRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the whole application with no MongoDB and no Valkey (Mongock off, rate-limit store mocked). */
@SpringBootTest(properties = "mongock.enabled=false")
@TestPropertySource(locations = "classpath:jwt-test.properties")
class TracePropagationTest {

    // the starter auto-configures the (traced) builder, not a RestClient bean
    @MockitoBean ProxyManager<String> rateLimitProxyManager;

    private static final AtomicReference<String> feignTraceparent = new AtomicReference<>();
    private static HttpServer memberService;

    @DynamicPropertySource
    static void memberServiceUrl(DynamicPropertyRegistry registry) throws Exception {
        memberService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        memberService.createContext("/", exchange -> {
            feignTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        memberService.start();
        registry.add("app.member-service.url", () -> "http://127.0.0.1:" + memberService.getAddress().getPort());
    }

    @AfterAll
    static void stopMemberService() {
        memberService.stop(0);
    }

    @Autowired MemberServiceClient memberServiceClient;
    @Autowired RestClient.Builder restClientBuilder;
    @Autowired Tracer tracer;

    @Test
    void outgoingCallCarriesCurrentTraceId() throws Exception {
        RestClient restClient = restClientBuilder.build();
        AtomicReference<String> traceparent = new AtomicReference<>();
        HttpServer downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.createContext("/", exchange -> {
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        downstream.start();
        Span span = tracer.nextSpan().name("test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            restClient.get().uri("http://127.0.0.1:" + downstream.getAddress().getPort() + "/ping")
                    .retrieve().toBodilessEntity();
        } finally {
            span.end();
            downstream.stop(0);
        }
        assertNotNull(traceparent.get(), "traceparent header was not sent");
        // format: 00-<traceId>-<spanId>-<flags>; trace id must match, the span id is the client call's own span
        assertTrue(traceparent.get().startsWith("00-" + span.context().traceId() + "-"), traceparent.get());
        assertEquals(4, traceparent.get().split("-").length);
    }

    @Test
    void feignCallToMemberServiceCarriesCurrentTraceId() {
        Span span = tracer.nextSpan().name("test").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            memberServiceClient.createMember(new CreateMemberRequest("ann@example.com", "1234567890", "Ann", "Lee"));
        } finally {
            span.end();
        }
        assertNotNull(feignTraceparent.get(), "traceparent header was not sent by the Feign client");
        assertTrue(feignTraceparent.get().startsWith("00-" + span.context().traceId() + "-"), feignTraceparent.get());
    }
}
