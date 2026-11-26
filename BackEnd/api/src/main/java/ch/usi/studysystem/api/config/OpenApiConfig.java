package ch.usi.studysystem.api.config;

import ch.usi.studysystem.api.security.AdminApiKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata. The spec is served at /v3/api-docs and rendered by Scalar at /scalar. */
@Configuration
public class OpenApiConfig {

    public static final String ADMIN_KEY_SCHEME = "adminKey";

    @Bean
    OpenAPI studySystemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("USI Study System API")
                        .version("0.1.0")
                        .description("Polyglot backend over Neo4j, MongoDB and Elasticsearch. "
                                + "Read endpoints are public. Endpoints under /api/admin need the "
                                + AdminApiKeyFilter.HEADER + " header."))
                .components(new Components().addSecuritySchemes(ADMIN_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(AdminApiKeyFilter.HEADER)));
    }
}
