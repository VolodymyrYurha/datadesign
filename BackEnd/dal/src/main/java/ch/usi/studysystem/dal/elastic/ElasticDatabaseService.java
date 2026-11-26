package ch.usi.studysystem.dal.elastic;

import ch.usi.studysystem.dal.common.DatabaseService;
import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.db.ElasticService;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import java.util.Map;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Uses the low-level REST client, so the JSON you write is exactly the JSON sent to
 * Elasticsearch (same as Kibana Dev Tools), and the raw response comes back as a map.
 */
@Service
public class ElasticDatabaseService extends DatabaseService implements ElasticService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final Rest5Client rest;

    public ElasticDatabaseService(Rest5Client rest, QueryTracer tracer) {
        super(tracer);
        this.rest = rest;
    }

    @Override
    protected DataStore store() {
        return DataStore.elasticsearch;
    }

    @Override
    public Map<String, Object> search(String index, String bodyJson) {
        String path = "/" + index + "/_search";
        return execute("search", "GET " + path + "\n" + bodyJson, Map.of(),
                () -> request("POST", path, bodyJson),
                ElasticDatabaseService::hitCount);
    }

    @Override
    public long count(String index, String queryJson) {
        String path = "/" + index + "/_count";
        return execute("count", "GET " + path + "\n" + queryJson, Map.of(),
                () -> ((Number) request("POST", path, queryJson).get("count")).longValue(),
                n -> 1);
    }

    @Override
    public Map<String, Object> info() {
        return execute("info", "GET /", Map.of(), () -> request("GET", "/", null), r -> 1);
    }

    private Map<String, Object> request(String method, String path, String body) throws Exception {
        Request req = new Request(method, path);
        if (body != null) {
            req.setJsonEntity(body);
        }
        Response res = rest.performRequest(req);
        String json = EntityUtils.toString(res.getEntity());
        Map<String, Object> parsed = JSON.readValue(json, MAP);
        if (res.getStatusCode() >= 400) {
            throw new IllegalStateException("HTTP " + res.getStatusCode() + ": " + errorReason(parsed));
        }
        return parsed;
    }

    /** Pulls error.reason out of an Elasticsearch error response, e.g. "no such index [x]". */
    private static Object errorReason(Map<String, Object> body) {
        return body.get("error") instanceof Map<?, ?> err && err.get("reason") != null ? err.get("reason") : body;
    }

    /** Number of hits returned in this page of a search response. */
    @SuppressWarnings("unchecked")
    private static Integer hitCount(Map<String, Object> response) {
        Object hits = response.get("hits");
        if (hits instanceof Map<?, ?> h && h.get("hits") instanceof java.util.List<?> list) {
            return list.size();
        }
        return null;
    }
}
