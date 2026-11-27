package ch.usi.studysystem.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AdminTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    private AdminTokenService at(Instant now) {
        return new AdminTokenService("admin", "admin", "secret", Duration.ofHours(8), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void loginIssuesAVerifiableToken() {
        String token = at(NOW).login("admin", "admin").orElseThrow().token();
        assertThat(at(NOW).verify(token)).contains("admin");
    }

    @Test
    void wrongCredentialsGetNoToken() {
        assertThat(at(NOW).login("admin", "nope")).isEmpty();
        assertThat(at(NOW).login("root", "admin")).isEmpty();
        assertThat(at(NOW).login(null, null)).isEmpty();
    }

    @Test
    void rejectsExpiredOrTamperedTokens() {
        String token = at(NOW).login("admin", "admin").orElseThrow().token();
        assertThat(at(NOW.plus(Duration.ofHours(9))).verify(token)).isEmpty();
        assertThat(at(NOW).verify(token.substring(0, token.length() - 2) + "xx")).isEmpty();
        assertThat(at(NOW).verify("garbage")).isEmpty();

        AdminTokenService otherSecret = new AdminTokenService("admin", "admin", "other", Duration.ofHours(8),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(otherSecret.verify(token)).isEmpty();
    }

    @Test
    void blankPasswordDisablesLogin() {
        AdminTokenService off = new AdminTokenService("admin", "", null, Duration.ofHours(1), Clock.systemUTC());
        assertThat(off.enabled()).isFalse();
        assertThat(off.login("admin", "")).isEmpty();
    }
}
