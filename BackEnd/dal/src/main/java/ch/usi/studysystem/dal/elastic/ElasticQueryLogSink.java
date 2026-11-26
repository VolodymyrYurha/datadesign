package ch.usi.studysystem.dal.elastic;

import ch.usi.studysystem.infrastructure.tracing.QuerySpan;
import ch.usi.studysystem.infrastructure.tracing.RequestTrace;
import ch.usi.studysystem.infrastructure.tracing.TraceSink;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes every finished request to Elasticsearch: one document to the {@code query-logs} data stream
 * and one per DB call to {@code query-spans}, in a single {@code _bulk} request.
 *
 * <p>Runs on one background thread with a bounded queue, so a slow or stopped Elasticsearch never
 * slows down or breaks a response; when the queue is full, traces are dropped (and counted).
 * It uses the low-level client directly, not {@link ElasticDatabaseService}, so its own writes are
 * not traced again. The data streams and their mappings are created by
 * {@code DataBase-infrastructure/elastic/setup.sh}.
 *
 * <p>Turn it off with {@code app.query-logs.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.query-logs.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticQueryLogSink implements TraceSink {

    static final String LOGS_STREAM = "query-logs";
    static final String SPANS_STREAM = "query-spans";

    private static final Logger log = LoggerFactory.getLogger(ElasticQueryLogSink.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final Rest5Client rest;
    private final String appVersion;
    private final ThreadPoolExecutor executor;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public ElasticQueryLogSink(Rest5Client rest,
                               @Value("${app.version:dev}") String appVersion,
                               @Value("${app.query-logs.queue-size:2000}") int queueSize) {
        this.rest = rest;
        this.appVersion = appVersion;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                r -> {
                    Thread t = new Thread(r, "query-log-writer");
                    t.setDaemon(true);
                    return t;
                },
                (r, ex) -> {
                    if (dropped.incrementAndGet() % 100 == 1) {
                        log.warn("query-logs queue full, dropped {} trace(s) so far", dropped.get());
                    }
                });
    }

    @Override
    public void record(RequestTrace trace) {
        executor.execute(() -> write(trace));
    }

    void write(RequestTrace trace) {
        try {
            Request req = new Request("POST", "/_bulk");
            req.setJsonEntity(bulkBody(trace, appVersion));
            Response res = rest.performRequest(req);
            Map<String, Object> body = JSON.readValue(EntityUtils.toString(res.getEntity()), MAP);
            if (Boolean.TRUE.equals(body.get("errors"))) {
                warnOnce("Elasticsearch rejected query-log documents: {}", firstError(body));
            }
        } catch (Exception e) {
            warnOnce("Could not write query logs to Elasticsearch: {}", e.toString());
        }
    }

    /** NDJSON for {@code _bulk}: data streams only accept {@code create}. */
    static String bulkBody(RequestTrace trace, String appVersion) {
        StringBuilder sb = new StringBuilder();
        append(sb, LOGS_STREAM, QueryLogDocuments.requestDocument(trace, appVersion));
        List<QuerySpan> spans = trace.spans() == null ? List.of() : trace.spans();
        for (int i = 0; i < spans.size(); i++) {
            append(sb, SPANS_STREAM, QueryLogDocuments.spanDocument(trace, spans.get(i), i, appVersion));
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String stream, Map<String, Object> doc) {
        sb.append("{\"create\":{\"_index\":\"").append(stream).append("\"}}\n");
        sb.append(JSON.writeValueAsString(doc)).append('\n');
    }

    @SuppressWarnings("unchecked")
    private static Object firstError(Map<String, Object> bulkResponse) {
        if (bulkResponse.get("items") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> m && m.get("create") instanceof Map<?, ?> op && op.get("error") != null) {
                    return op.get("error") instanceof Map<?, ?> err ? err.get("reason") : op.get("error");
                }
            }
        }
        return "unknown";
    }

    /** Logs the first failure, then every 100th, so a stopped Elasticsearch doesn't flood the log. */
    private void warnOnce(String message, Object arg) {
        if (failed.incrementAndGet() % 100 == 1) {
            log.warn(message + " (failure #{})", arg, failed.get());
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
    }
}
