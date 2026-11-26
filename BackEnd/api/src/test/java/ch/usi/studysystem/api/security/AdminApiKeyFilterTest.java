package ch.usi.studysystem.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.usi.studysystem.infrastructure.tracing.RequestTrace;
import ch.usi.studysystem.infrastructure.tracing.TraceSink;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

/** The admin guard and the request trace it feeds. Needs no database. */
@SpringBootTest(properties = {"app.admin.api-key=test-key", "app.query-logs.enabled=false"})
@AutoConfigureMockMvc
class AdminApiKeyFilterTest {

    @TestConfiguration
    static class CapturingSink {
        static final List<RequestTrace> TRACES = new CopyOnWriteArrayList<>();

        @Bean
        TraceSink capturingSink() {
            return TRACES::add;
        }
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        CapturingSink.TRACES.clear();
    }

    @Test
    void rejectsMissingKey() throws Exception {
        mvc.perform(get("/api/admin/whoami"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        assertThat(CapturingSink.TRACES).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(401);
            assertThat(t.userId()).isEqualTo("anonymous");
        });
    }

    @Test
    void rejectsWrongKey() throws Exception {
        mvc.perform(get("/api/admin/whoami").header(AdminApiKeyFilter.HEADER, "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidKeyAndTagsTheTraceAsAdmin() throws Exception {
        mvc.perform(get("/api/admin/whoami").header(AdminApiKeyFilter.HEADER, "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("admin"));

        assertThat(CapturingSink.TRACES).singleElement().satisfies(t -> {
            assertThat(t.userId()).isEqualTo("admin");
            assertThat(t.userRole()).isEqualTo("admin");
            assertThat(t.route()).isEqualTo("/api/admin/whoami");
            assertThat(t.startedAt()).isNotNull();
        });
    }

    @Test
    void publicEndpointsNeedNoKey() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());

        assertThat(CapturingSink.TRACES).singleElement().satisfies(t -> {
            assertThat(t.userRole()).isEqualTo("public");
            assertThat(t.errorType()).isNull();
        });
    }
}
