package kitchen.sink.demo.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeysTest {

    private static String pem(String name) throws IOException {
        return new ClassPathResource("test-keys/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    private static JwtProperties props(String priv, String pub) {
        return new JwtProperties(priv, pub, "iss", null, Duration.ofMinutes(15), Duration.ofDays(7));
    }

    @Test
    @Tag("critical")
    void loadsAMatchingPemPair() throws IOException {
        JwtKeys keys = JwtKeys.load(props(pem("private.pem"), pem("public.pem")));

        assertThat(keys.publicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
        assertThat(keys.derivationSecret()).isNotEmpty();
    }

    @Test
    @Tag("critical")
    void refusesToStartWithoutKeys() {
        assertThatThrownBy(() -> JwtKeys.load(props(null, null))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    void rejectsNonPemContent() throws IOException {
        String publicPem = pem("public.pem");
        assertThatThrownBy(() -> JwtKeys.load(props("nope", publicPem))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PEM");
    }

    @Test
    void acceptsPemWithLiteralBackslashNInsteadOfNewlines() throws IOException {
        String oneLinePrivate = pem("private.pem").trim().replace("\n", "\\n");
        String oneLinePublic = pem("public.pem").trim().replace("\n", "\\n");

        assertThat(JwtKeys.load(props(oneLinePrivate, oneLinePublic)).publicKey()).isNotNull();
    }
}
