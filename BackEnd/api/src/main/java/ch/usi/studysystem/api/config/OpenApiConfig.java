package ch.usi.studysystem.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata. The spec is served at /v3/api-docs and rendered by Scalar at /scalar. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI studySystemOpenApi() {
        return new OpenAPI().info(new Info()
                .title("USI Study System API")
                .version("0.1.0")
                .description("Polyglot backend over Neo4j, MongoDB and Elasticsearch. "
                        + "Read endpoints are public. Write endpoints will require admin auth."));
    }
}
