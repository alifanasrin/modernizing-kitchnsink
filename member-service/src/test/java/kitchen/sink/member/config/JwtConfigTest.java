package kitchen.sink.member.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    @Test
    void startupFails_whenThePublicKeyIsMissing() {
        assertThatThrownBy(() -> JwtConfig.publicKey(""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> JwtConfig.publicKey(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFails_whenTheKeyIsNotAPublicKeyPem() {
        assertThatThrownBy(() -> JwtConfig.publicKey("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PUBLIC KEY");
    }
}
