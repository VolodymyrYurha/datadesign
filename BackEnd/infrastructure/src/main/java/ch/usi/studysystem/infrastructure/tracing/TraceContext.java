package ch.usi.studysystem.infrastructure.tracing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-request trace state (trace id, start time, recorded DB spans).
 * Bound to the request thread by the API's trace-id filter.
 */
public final class TraceContext {

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private final String traceId;
    private final long startNanos = System.nanoTime();
    private final List<QuerySpan> spans = Collections.synchronizedList(new ArrayList<>());

    private TraceContext(String traceId) {
        this.traceId = traceId;
    }

    public static TraceContext start(String traceId) {
        TraceContext ctx = new TraceContext(traceId);
        CURRENT.set(ctx);
        return ctx;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** The context of the current request, or null outside a request. */
    public static TraceContext current() {
        return CURRENT.get();
    }

    public String traceId() {
        return traceId;
    }

    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public void add(QuerySpan span) {
        spans.add(span);
    }

    public List<QuerySpan> spans() {
        synchronized (spans) {
            return List.copyOf(spans);
        }
    }
}
