package ch.usi.studysystem.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Admin login and tokens. Checks the configured username/password and issues a small JWT
 * (HS256, claims {@code sub}, {@code role}, {@code iat}, {@code exp}) that the admin guard accepts as
 * {@code Authorization: Bearer <token>}.
 *
 * <p>Plain JDK crypto, no extra library. If no secret is configured, a random one is generated at
 * startup, so tokens stop working after a restart (fine for local dev; set {@code ADMIN_TOKEN_SECRET}
 * to keep them).
 */
public final class AdminTokenService {

    public static final String ROLE_ADMIN = "admin";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"([^\"]*)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final String username;
    private final byte[] password;
    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    public AdminTokenService(String username, String password, String secret, Duration ttl, Clock clock) {
        this.username = username;
        this.password = password == null || password.isBlank() ? null : password.getBytes(StandardCharsets.UTF_8);
        this.secret = secret == null || secret.isBlank() ? randomSecret() : secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    /** False when no password is configured: login is turned off. */
    public boolean enabled() {
        return password != null && username != null && !username.isBlank();
    }

    /** A token for valid credentials, empty otherwise. */
    public Optional<IssuedToken> login(String user, String pass) {
        if (!enabled() || user == null || pass == null) {
            return Optional.empty();
        }
        boolean userOk = MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8), user.getBytes(StandardCharsets.UTF_8));
        boolean passOk = MessageDigest.isEqual(password, pass.getBytes(StandardCharsets.UTF_8));
        if (!(userOk & passOk)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant exp = now.plus(ttl);
        String claims = "{\"sub\":\"" + username.replace("\"", "") + "\",\"role\":\"" + ROLE_ADMIN
                + "\",\"iat\":" + now.getEpochSecond() + ",\"exp\":" + exp.getEpochSecond() + "}";
        String unsigned = HEADER + "." + B64.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        return Optional.of(new IssuedToken(unsigned + "." + sign(unsigned), exp));
    }

    /** The user name in a valid, unexpired token, empty otherwise. */
    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !parts[0].equals(HEADER)) {
            return Optional.empty();
        }
        byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        String claims;
        try {
            claims = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Matcher exp = EXP.matcher(claims);
        Matcher sub = SUB.matcher(claims);
        if (!exp.find() || !sub.find() || Long.parseLong(exp.group(1)) <= clock.instant().getEpochSecond()) {
            return Optional.empty();
        }
        return Optional.of(sub.group(1));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomSecret() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
