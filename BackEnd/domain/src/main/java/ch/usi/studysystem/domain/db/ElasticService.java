package ch.usi.studysystem.domain.db;

import java.util.Map;

/**
 * Direct access to Elasticsearch with request bodies written as JSON (the same syntax as in
 * Kibana Dev Tools).
 */
public interface ElasticService {

    /**
     * Runs {@code GET /<index>/_search} with the given body, e.g.
     * {@code { "query": { "match": { "title": "database" } } }}.
     * Returns the full response as a map: {@code hits.total}, {@code hits.hits[]._source},
     * {@code aggregations}, {@code highlight}, ...
     */
    Map<String, Object> search(String index, String bodyJson);

    long count(String index, String queryJson);

    /** {@code GET /}: cluster name and version. */
    Map<String, Object> info();
}
