package ch.usi.studysystem.dal.elastic;

import static org.assertj.core.api.Assertions.assertThat;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.infrastructure.tracing.QuerySpan;
import ch.usi.studysystem.infrastructure.tracing.RequestTrace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The documents must match the strict mappings in DataBase-infrastructure/elastic/*-template.json. */
class QueryLogDocumentsTest {

    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");

    private final QuerySpan neo = new QuerySpan(DataStore.neo4j, "eligible", "MATCH (c:Course)\n  RETURN c",
            Map.of("courseId", "course:1"), 12, 3, null, T0.plusMillis(1));
    private final QuerySpan mongo = new QuerySpan(DataStore.mongodb, "find", "db.courses.find({})",
            Map.of(), 30, null, "timeout", T0.plusMillis(20));
    private final RequestTrace trace = new RequestTrace("t-1", T0, "GET", "/api/courses/course:1",
            "/api/courses/{id}", "lang=en", 500, 55, "java.lang.IllegalStateException", "boom",
            "anonymous", "public", "127.0.0.1", "curl/8", List.of(neo, mongo));

    @Test
    @SuppressWarnings("unchecked")
    void requestDocument() {
        Map<String, Object> doc = QueryLogDocuments.requestDocument(trace, "0.1.0");

        assertThat(doc).containsEntry("@timestamp", "2026-11-26T10:00:00Z")
                .containsEntry("traceId", "t-1")
                .containsEntry("outcome", "server_error")
                .containsEntry("spanCount", 2)
                .containsEntry("dbs", List.of("neo4j", "mongodb"))
                .containsEntry("dbDurationMs", 42L)
                .containsEntry("app", Map.of("version", "0.1.0"));
        assertThat((Map<String, Object>) doc.get("http"))
                .containsEntry("route", "/api/courses/{id}").containsEntry("query", "lang=en");
        assertThat((Map<String, Object>) doc.get("error")).containsEntry("type", "java.lang.IllegalStateException");

        List<Map<String, Object>> spans = (List<Map<String, Object>>) doc.get("spans");
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0)).containsEntry("language", "cypher").containsEntry("success", true)
                .doesNotContainKey("traceId").doesNotContainKey("http");
        assertThat(spans.get(1)).containsEntry("error", "timeout").containsEntry("success", false)
                .doesNotContainKey("rows").doesNotContainKey("params");
    }

    @Test
    @SuppressWarnings("unchecked")
    void spanDocumentCopiesRequestFields() {
        Map<String, Object> doc = QueryLogDocuments.spanDocument(trace, neo, 0, "0.1.0");

        assertThat(doc).containsEntry("@timestamp", "2026-11-26T10:00:00.001Z")
                .containsEntry("traceId", "t-1")
                .containsEntry("spanIndex", 0)
                .containsEntry("params", Map.of("courseId", "course:1"))
                .containsEntry("user", Map.of("id", "anonymous"));
        assertThat((Map<String, Object>) doc.get("http"))
                .containsOnlyKeys("method", "route", "status");
    }

    @Test
    void queryHashIgnoresWhitespace() {
        assertThat(QueryLogDocuments.queryHash("MATCH (c)\n   RETURN c"))
                .isEqualTo(QueryLogDocuments.queryHash("MATCH (c) RETURN c"))
                .hasSize(16);
    }

    @Test
    void bulkBodyHasOneRequestAndOneLinePerSpan() {
        String body = ElasticQueryLogSink.bulkBody(trace, "0.1.0");
        String[] lines = body.split("\n");

        assertThat(lines).hasSize(6);
        assertThat(lines[0]).isEqualTo("{\"create\":{\"_index\":\"query-logs\"}}");
        assertThat(lines[2]).isEqualTo("{\"create\":{\"_index\":\"query-spans\"}}");
        assertThat(body).endsWith("\n");
    }
}
