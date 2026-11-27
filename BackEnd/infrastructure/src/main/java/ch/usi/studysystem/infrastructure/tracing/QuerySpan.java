package ch.usi.studysystem.infrastructure.tracing;

import ch.usi.studysystem.domain.DataStore;
import java.time.Instant;
import java.util.Map;

/**
 * One database call made while serving a request. This is what the frontend
 * "query inspector" shows and what the Elasticsearch sink writes to the
 * {@code query-spans} data stream (and nested inside {@code query-logs}).
 *
 * @param db         which store was called: neo4j, mongodb or elasticsearch
 * @param operation  short label, e.g. "ping" or "findEligibleCourses"
 * @param query      the raw query text (Cypher, Mongo JSON, ES DSL)
 * @param params     query parameters, if any
 * @param durationMs wall time of the call
 * @param rows       number of results, or null if not applicable
 * @param error      error message, or null on success
 * @param startedAt  when the call started (UTC)
 */
public record QuerySpan(
        DataStore db,
        String operation,
        String query,
        Map<String, Object> params,
        long durationMs,
        Integer rows,
        String error,
        Instant startedAt) {
}
