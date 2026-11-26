package ch.usi.studysystem.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Custom settings under the {@code app.*} prefix in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Admin admin) {

    public record Cors(List<String> allowedOrigins) {
    }

    /** {@code apiKey} blank = admin API off. */
    public record Admin(String apiKey) {
    }
}
