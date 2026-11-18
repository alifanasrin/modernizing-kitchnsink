package kitchen.sink.demo.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /** Trims and lower-cases an email address. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
