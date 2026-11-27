package ch.usi.studysystem.infrastructure.tracing;

/**
 * Destination for finished request traces.
 * TODO: add an Elasticsearch implementation that writes to the {@code query-logs}
 * index asynchronously, so it does not slow down responses.
 */
public interface TraceSink {
    void record(RequestTrace trace);
}
