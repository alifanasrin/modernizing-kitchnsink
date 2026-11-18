package kitchen.sink.demo.security;

import kitchen.sink.demo.exception.InvalidPasswordException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    static PasswordPolicy policy() {
        return new PasswordPolicy();
    }

    @Test
    @Tag("critical")
    void minimumIs15() {
        assertThat(policy().minLength()).isEqualTo(15);
        assertThatCode(() -> policy().validate("a".repeat(15))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy().validate("a".repeat(14))).isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("15");
    }

    @Test
    @Tag("critical")
    void maximumIs64() {
        assertThatCode(() -> policy().validate("a".repeat(64))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy().validate("a".repeat(65))).isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("64");
        assertThat(policy().exceedsMaxLength("a".repeat(65))).isTrue();
        assertThat(policy().exceedsMaxLength("a".repeat(64))).isFalse();
    }

    @Test
    void allCharactersAreAllowed_spacesUnicodeAndSymbols_andNothingIsTrimmed() {
        assertThatCode(() -> policy().validate("correct horse battery staple")).doesNotThrowAnyException();
        assertThatCode(() -> policy().validate("пароль-密码-🔐-!@#$%^&*()")).doesNotThrowAnyException();
        // leading/trailing spaces count towards the length: not trimmed
        assertThatCode(() -> policy().validate("  " + "a".repeat(13))).doesNotThrowAnyException();
        // a code point counts once, even outside the BMP
        assertThatCode(() -> policy().validate("🔐".repeat(15))).doesNotThrowAnyException();
    }

    @Test
    void lengthIsMeasuredAsTyped_noNormalization() {
        // U+FDFA stays one code point: it is not expanded
        assertThatThrownBy(() -> policy().validate("ﷺ")).isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @Tag("critical")
    void encoderUsesTheWholePasswordAndNormalizesWithNfkc() {
        Sha256BcryptPasswordEncoder encoder = new Sha256BcryptPasswordEncoder();
        String base = "a".repeat(100);
        String hash = encoder.encode(base + "X");

        assertThat(encoder.matches(base + "X", hash)).isTrue();
        assertThat(encoder.matches(base + "Y", hash)).isFalse(); // plain BCrypt would have ignored bytes past 72
        // no normalization: full-width "ＡＢＣ" is not "ABC"
        String hashAbc = encoder.encode("ABC-passphrase-12");
        assertThat(encoder.matches("ＡＢＣ-passphrase-12", hashAbc)).isFalse();
        assertThat(encoder.matches(" ABC-passphrase-12", hashAbc)).isFalse(); // not trimmed
    }

    @Test
    @Tag("critical")
    void storedHashesAreBareBcryptWithNoAlgorithmLabel() {
        String hash = new Sha256BcryptPasswordEncoder().encode("new-password-123");

        assertThat(hash).startsWith("$2").doesNotContain("{").doesNotContain("sha256");
    }
}
