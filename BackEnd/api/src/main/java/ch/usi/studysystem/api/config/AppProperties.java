package ch.usi.studysystem.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Custom settings under the {@code app.*} prefix in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Admin admin) {

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Admin access for {@code /api/admin/**}.
     *
     * @param username    login name for {@code POST /api/auth/login}
     * @param password    login password; blank turns login off
     * @param tokenSecret HMAC secret for login tokens; blank = random per start
     * @param tokenTtl    how long a login token stays valid
     * @param apiKey      optional static key for scripts ({@code X-Admin-Key}); blank = off
     */
    public record Admin(String username, String password, String tokenSecret, Duration tokenTtl, String apiKey) {
    }
}
