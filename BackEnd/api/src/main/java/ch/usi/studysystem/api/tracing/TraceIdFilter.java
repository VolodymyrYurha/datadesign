package ch.usi.studysystem.api.tracing;

import ch.usi.studysystem.infrastructure.tracing.RequestTrace;
import ch.usi.studysystem.infrastructure.tracing.TraceContext;
import ch.usi.studysystem.infrastructure.tracing.TraceSink;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Assigns a trace id to every HTTP request, returns it as {@code X-Trace-Id},
 * and hands the finished trace to every {@link TraceSink} (log, Elasticsearch).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    private final List<TraceSink> sinks;

    public TraceIdFilter(List<TraceSink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();

        TraceContext ctx = TraceContext.start(traceId);
        MDC.put("traceId", traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            TraceContext.recordError(e);
            throw e;
        } finally {
            try {
                publish(toTrace(ctx, request, response));
            } finally {
                MDC.remove("traceId");
                TraceContext.clear();
            }
        }
    }

    private static RequestTrace toTrace(TraceContext ctx, HttpServletRequest request, HttpServletResponse response) {
        Throwable error = ctx.error();
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        int status = response.getStatus();
        if (error != null && status < 400) {
            status = 500; // the exception escaped the filter chain; the container turns it into a 500
        }
        return new RequestTrace(
                ctx.traceId(),
                ctx.startedAt(),
                request.getMethod(),
                request.getRequestURI(),
                route != null ? route.toString() : null,
                request.getQueryString(),
                status,
                ctx.elapsedMs(),
                error != null ? error.getClass().getName() : null,
                error != null ? error.getMessage() : null,
                ctx.userId(),
                ctx.userRole(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                ctx.spans());
    }

    private void publish(RequestTrace trace) {
        for (TraceSink sink : sinks) {
            try {
                sink.record(trace);
            } catch (RuntimeException e) {
                log.warn("Trace sink {} failed", sink.getClass().getSimpleName(), e); // never break the response
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Docs and static assets are not interesting for the query log.
        return path.startsWith("/scalar") || path.startsWith("/v3/api-docs");
    }
}
