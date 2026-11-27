package ch.usi.studysystem.api.config;

import ch.usi.studysystem.infrastructure.security.AdminKeyVerifier;
import ch.usi.studysystem.infrastructure.security.AdminTokenService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the admin login/token service and the optional API-key check from {@code app.admin.*}. */
@Configuration
public class SecurityConfig {

    @Bean
    AdminTokenService adminTokenService(AppProperties props) {
        AppProperties.Admin a = props.admin();
        Duration ttl = a.tokenTtl() != null ? a.tokenTtl() : Duration.ofHours(8);
        return new AdminTokenService(a.username(), a.password(), a.tokenSecret(), ttl, Clock.systemUTC());
    }

    @Bean
    AdminKeyVerifier adminKeyVerifier(AppProperties props) {
        return new AdminKeyVerifier(props.admin().apiKey());
    }
}
