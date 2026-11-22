package kitchen.sink.demo.client;

import com.sun.net.httpserver.HttpServer;
import kitchen.sink.demo.dto.request.CreateMemberRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Feign client against a tiny local HTTP server: checks the path, method and body sent to member-service. */
@SpringJUnitConfig
class MemberServiceClientTest {

    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = MemberServiceClient.class)
    static class Config {
    }

    static HttpServer server;
    static final AtomicReference<String> request = new AtomicReference<>();

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/members", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.compatibility-verifier.enabled", () -> "false");
        registry.add("app.member-service.url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @Autowired MemberServiceClient client;

    @Test
    @Tag("critical")
    void shouldPostMemberDataAsJson_withoutAnyPassword() {
        client.createMember(new CreateMemberRequest("ann@example.com", "1234567890", "Ann", "Lee"));

        assertThat(request.get()).startsWith("POST /members ")
                .contains("\"email\":\"ann@example.com\"", "\"firstName\":\"Ann\"")
                .doesNotContainIgnoringCase("password");
    }
}
