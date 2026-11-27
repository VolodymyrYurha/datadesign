package ch.usi.studysystem.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Custom settings under the {@code app.*} prefix in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors) {

    public record Cors(List<String> allowedOrigins) {
    }
}
