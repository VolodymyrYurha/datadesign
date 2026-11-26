package ch.usi.studysystem.infrastructure.tracing;

/**
 * Destination for finished request traces. Every sink bean is called for every request.
 * Current sinks: {@link LoggingTraceSink} (application log) and {@code ElasticQueryLogSink}
 * in the dal module ({@code query-logs} / {@code query-spans} data streams).
 *
 * <p>A sink is called on the request thread, so it must return quickly (queue the work).
 */
public interface TraceSink {
    void record(RequestTrace trace);
}
