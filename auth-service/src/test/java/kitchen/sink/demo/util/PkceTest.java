package kitchen.sink.demo.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceTest {

    // the example from RFC 7636 appendix B
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    @Tag("critical")
    void challengeFor_matchesTheRfcExample() {
        assertThat(Pkce.challengeFor(VERIFIER)).isEqualTo(CHALLENGE);
    }

    @Test
    @Tag("critical")
    void matches_onlyForTheRightVerifier() {
        assertThat(Pkce.matches(VERIFIER, CHALLENGE)).isTrue();
        assertThat(Pkce.matches(VERIFIER + "x", CHALLENGE)).isFalse();
        assertThat(Pkce.matches(CHALLENGE, CHALLENGE)).isFalse(); // sending the challenge itself must not work (plain)
    }
}
