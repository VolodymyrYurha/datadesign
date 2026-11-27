package ch.usi.studysystem.infrastructure.tracing;

import ch.usi.studysystem.domain.DataStore;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Wrap every database call with this, so the call shows up in the query
 * inspector and in the query log:
 *
 * <pre>{@code
 * List<Course> result = tracer.trace(DataStore.neo4j, "eligibleCourses", cypher, params,
 *         () -> neo4jClient.query(cypher).bindAll(params).fetchAs(Course.class).all());
 * }</pre>
 */
@Component
public class QueryTracer {

    public <T> T trace(DataStore db, String operation, String query, Map<String, Object> params, Supplier<T> call) {
        long start = System.nanoTime();
        try {
            T result = call.get();
            record(db, operation, query, params, start, countRows(result), null);
            return result;
        } catch (RuntimeException e) {
            record(db, operation, query, params, start, null, e.getMessage());
            throw e;
        }
    }

    public <T> T trace(DataStore db, String operation, String query, Supplier<T> call) {
        return trace(db, operation, query, Map.of(), call);
    }

    private void record(DataStore db, String operation, String query, Map<String, Object> params,
                        long startNanos, Integer rows, String error) {
        TraceContext ctx = TraceContext.current();
        if (ctx == null) {
            return; // called outside an HTTP request (e.g. startup); nothing to attach to
        }
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        ctx.add(new QuerySpan(db, operation, query, params == null ? Map.of() : params, ms, rows, error));
    }

    private static Integer countRows(Object result) {
        if (result instanceof Collection<?> c) return c.size();
        return null;
    }
}
