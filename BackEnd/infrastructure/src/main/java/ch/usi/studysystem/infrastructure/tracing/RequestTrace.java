package ch.usi.studysystem.infrastructure.tracing;

import java.time.Instant;
import java.util.List;

/**
 * A finished HTTP request with all its DB spans. The Elasticsearch sink turns it into one
 * {@code query-logs} document plus one {@code query-spans} document per span.
 *
 * @param route        route template, e.g. {@code /api/courses/{id}}, or null if no controller matched
 * @param query        query string without the {@code ?}, or null
 * @param errorType    exception class for failed requests, or null
 * @param errorMessage exception message for failed requests, or null
 * @param userId       {@code admin} for authenticated admin calls, else {@code anonymous}
 * @param userRole     {@code admin} or {@code public}
 */
public record RequestTrace(
        String traceId,
        Instant startedAt,
        String method,
        String path,
        String route,
        String query,
        int status,
        long durationMs,
        String errorType,
        String errorMessage,
        String userId,
        String userRole,
        String clientIp,
        String userAgent,
        List<QuerySpan> spans) {
}
