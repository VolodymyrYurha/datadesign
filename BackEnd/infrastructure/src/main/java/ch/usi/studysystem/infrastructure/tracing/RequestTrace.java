package ch.usi.studysystem.infrastructure.tracing;

import java.util.List;

/** A finished request with all its DB spans. One document per request in {@code query-logs}. */
public record RequestTrace(
        String traceId,
        String method,
        String path,
        int status,
        long durationMs,
        List<QuerySpan> spans) {
}
