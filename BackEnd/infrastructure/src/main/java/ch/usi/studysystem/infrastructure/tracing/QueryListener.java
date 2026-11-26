package ch.usi.studysystem.infrastructure.tracing;

/**
 * Receives every database query after it ran (like an EF Core {@code DbCommandInterceptor}).
 * Register a new implementation as a Spring bean and it gets called automatically.
 *
 * <p>Current listeners: {@link LoggingQueryListener} (application log) and
 * {@link RequestTraceQueryListener} (query inspector in the frontend).
 * TODO: an Elasticsearch listener that writes to the {@code query-logs} index.
 */
public interface QueryListener {
    void onQuery(QuerySpan span);
}
