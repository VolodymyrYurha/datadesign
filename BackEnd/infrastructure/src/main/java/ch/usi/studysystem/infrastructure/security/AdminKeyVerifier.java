package ch.usi.studysystem.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Checks the admin API key in constant time. A blank configured key disables the admin API. */
public final class AdminKeyVerifier {

    public enum Result { OK, MISSING, WRONG, DISABLED }

    private final byte[] expected;

    public AdminKeyVerifier(String configuredKey) {
        this.expected = configuredKey == null || configuredKey.isBlank()
                ? null
                : configuredKey.getBytes(StandardCharsets.UTF_8);
    }

    public boolean enabled() {
        return expected != null;
    }

    public Result check(String presentedKey) {
        if (expected == null) {
            return Result.DISABLED;
        }
        if (presentedKey == null || presentedKey.isBlank()) {
            return Result.MISSING;
        }
        return MessageDigest.isEqual(expected, presentedKey.getBytes(StandardCharsets.UTF_8))
                ? Result.OK
                : Result.WRONG;
    }
}
