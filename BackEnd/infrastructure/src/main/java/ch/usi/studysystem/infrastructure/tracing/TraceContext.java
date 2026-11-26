package ch.usi.studysystem.infrastructure.tracing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-request trace state: trace id, start time, recorded DB spans, the caller and the error (if any).
 * Bound to the request thread by the API's trace-id filter.
 */
public final class TraceContext {

    public static final String ANONYMOUS = "anonymous";
    public static final String ROLE_PUBLIC = "public";

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private final String traceId;
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private final List<QuerySpan> spans = Collections.synchronizedList(new ArrayList<>());
    private volatile String userId = ANONYMOUS;
    private volatile String userRole = ROLE_PUBLIC;
    private volatile Throwable error;

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

    /** Records the exception that failed the current request, if there is a request. */
    public static void recordError(Throwable error) {
        TraceContext ctx = current();
        if (ctx != null) {
            ctx.error = error;
        }
    }

    public String traceId() {
        return traceId;
    }

    public Instant startedAt() {
        return startedAt;
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

    public void setUser(String id, String role) {
        this.userId = id;
        this.userRole = role;
    }

    public String userId() {
        return userId;
    }

    public String userRole() {
        return userRole;
    }

    public Throwable error() {
        return error;
    }
}
