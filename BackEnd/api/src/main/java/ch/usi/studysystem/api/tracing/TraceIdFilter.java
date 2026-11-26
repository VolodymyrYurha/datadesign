package ch.usi.studysystem.api.tracing;

import ch.usi.studysystem.infrastructure.tracing.RequestTrace;
import ch.usi.studysystem.infrastructure.tracing.TraceContext;
import ch.usi.studysystem.infrastructure.tracing.TraceSink;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a trace id to every HTTP request, returns it as {@code X-Trace-Id},
 * and hands the finished trace to the {@link TraceSink}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private final TraceSink sink;

    public TraceIdFilter(TraceSink sink) {
        this.sink = sink;
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
        } finally {
            try {
                sink.record(new RequestTrace(
                        traceId,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        ctx.elapsedMs(),
                        ctx.spans()));
            } finally {
                MDC.remove("traceId");
                TraceContext.clear();
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
