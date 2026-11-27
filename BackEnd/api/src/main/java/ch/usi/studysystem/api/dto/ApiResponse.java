package ch.usi.studysystem.api.dto;

import ch.usi.studysystem.infrastructure.tracing.QuerySpan;
import ch.usi.studysystem.infrastructure.tracing.TraceContext;
import java.util.List;

/**
 * Envelope for every API response. {@code meta} carries the trace id and the DB
 * spans, which is what the frontend query inspector renders.
 */
public record ApiResponse<T>(T data, Meta meta, ApiError error) {

    public record Meta(String traceId, long durationMs, List<QuerySpan> queries) {
    }

    public record ApiError(String code, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, currentMeta(), null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(null, currentMeta(), new ApiError(code, message));
    }

    private static Meta currentMeta() {
        TraceContext ctx = TraceContext.current();
        if (ctx == null) {
            return new Meta(null, 0, List.of());
        }
        return new Meta(ctx.traceId(), ctx.elapsedMs(), ctx.spans());
    }
}
