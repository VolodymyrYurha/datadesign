package ch.usi.studysystem.dal.elastic;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.infrastructure.tracing.QuerySpan;
import ch.usi.studysystem.infrastructure.tracing.RequestTrace;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds the Elasticsearch documents for one finished request, following
 * {@code DataBase-infrastructure/elastic/REQUIREMENTS.md}: one {@code query-logs} document per request
 * (spans nested) and one flat {@code query-spans} document per DB call. Null fields are left out.
 */
final class QueryLogDocuments {

    private QueryLogDocuments() {
    }

    /** One document per HTTP request, for the {@code query-logs} data stream. */
    static Map<String, Object> requestDocument(RequestTrace t, String appVersion) {
        Map<String, Object> doc = new LinkedHashMap<>();
        put(doc, "@timestamp", t.startedAt() != null ? t.startedAt().toString() : null);
        put(doc, "traceId", t.traceId());
        doc.put("http", http(t, true));
        doc.put("durationMs", t.durationMs());
        doc.put("outcome", outcome(t.status()));
        if (t.errorType() != null || t.errorMessage() != null) {
            Map<String, Object> error = new LinkedHashMap<>();
            put(error, "type", t.errorType());
            put(error, "message", t.errorMessage());
            doc.put("error", error);
        }
        doc.put("user", user(t));
        Map<String, Object> client = new LinkedHashMap<>();
        put(client, "ip", t.clientIp());
        put(client, "userAgent", t.userAgent());
        if (!client.isEmpty()) {
            doc.put("client", client);
        }
        doc.put("app", Map.of("version", appVersion));

        List<QuerySpan> spans = t.spans() == null ? List.of() : t.spans();
        LinkedHashSet<String> dbs = new LinkedHashSet<>();
        long dbMs = 0;
        List<Map<String, Object>> nested = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            QuerySpan s = spans.get(i);
            dbs.add(s.db().name());
            dbMs += s.durationMs();
            nested.add(spanFields(s, i));
        }
        doc.put("spanCount", spans.size());
        doc.put("dbs", List.copyOf(dbs));
        doc.put("dbDurationMs", dbMs);
        doc.put("spans", nested);
        return doc;
    }

    /** One flat document per DB call, for the {@code query-spans} data stream. */
    static Map<String, Object> spanDocument(RequestTrace t, QuerySpan s, int index, String appVersion) {
        Map<String, Object> fields = spanFields(s, index);
        Object ts = fields.remove("@timestamp");
        Map<String, Object> doc = new LinkedHashMap<>();
        put(doc, "@timestamp", ts != null ? ts : t.startedAt() != null ? t.startedAt().toString() : null);
        put(doc, "traceId", t.traceId());
        doc.putAll(fields);
        doc.put("http", http(t, false));
        doc.put("user", Map.of("id", userId(t)));
        doc.put("app", Map.of("version", appVersion));
        return doc;
    }

    private static Map<String, Object> spanFields(QuerySpan s, int index) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "@timestamp", s.startedAt() != null ? s.startedAt().toString() : null);
        m.put("spanIndex", index);
        m.put("db", s.db().name());
        put(m, "operation", s.operation());
        put(m, "query", s.query());
        if (s.query() != null) {
            m.put("queryHash", queryHash(s.query()));
        }
        m.put("language", language(s.db()));
        if (s.params() != null && !s.params().isEmpty()) {
            m.put("params", s.params());
        }
        m.put("durationMs", s.durationMs());
        put(m, "rows", s.rows());
        put(m, "error", s.error());
        m.put("success", s.error() == null);
        return m;
    }

    private static Map<String, Object> http(RequestTrace t, boolean full) {
        Map<String, Object> http = new LinkedHashMap<>();
        put(http, "method", t.method());
        if (full) {
            put(http, "path", t.path());
        }
        put(http, "route", t.route());
        http.put("status", t.status());
        if (full) {
            put(http, "query", t.query());
        }
        return http;
    }

    private static Map<String, Object> user(RequestTrace t) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId(t));
        user.put("role", t.userRole() != null ? t.userRole() : "public");
        return user;
    }

    private static String userId(RequestTrace t) {
        return t.userId() != null ? t.userId() : "anonymous";
    }

    static String outcome(int status) {
        if (status >= 500) return "server_error";
        if (status >= 400) return "client_error";
        return "success";
    }

    static String language(DataStore db) {
        return switch (db) {
            case neo4j -> "cypher";
            case mongodb -> "mongo";
            case elasticsearch -> "es-dsl";
        };
    }

    /** Short hash of the query text with whitespace collapsed, so identical query shapes group together. */
    static String queryHash(String query) {
        String normalized = query.strip().replaceAll("\\s+", " ");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void put(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }
}
