package ch.usi.studysystem.infrastructure.tracing;

import org.springframework.stereotype.Component;

/** Attaches the query to the current HTTP request, so it appears in the response's meta.queries. */
@Component
public class RequestTraceQueryListener implements QueryListener {

    @Override
    public void onQuery(QuerySpan span) {
        TraceContext ctx = TraceContext.current();
        if (ctx != null) { // null outside an HTTP request, e.g. at startup
            ctx.add(span);
        }
    }
}
