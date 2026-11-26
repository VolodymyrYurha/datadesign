package ch.usi.studysystem.infrastructure.tracing;

import ch.usi.studysystem.domain.DataStore;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Times a database call and hands the result to every {@link QueryListener}.
 * Normally you don't call this directly: extend {@code DatabaseService} in the dal module,
 * which routes every query through here.
 */
@Component
public class QueryTracer {

    private static final Logger log = LoggerFactory.getLogger(QueryTracer.class);

    private final List<QueryListener> listeners;

    public QueryTracer(List<QueryListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public <T> T trace(DataStore db, String operation, String query, Map<String, Object> params, Supplier<T> call) {
        return trace(db, operation, query, params, call, QueryTracer::countRows);
    }

    /** Same as above, with a custom way to count result rows (e.g. hits in a search response). */
    public <T> T trace(DataStore db, String operation, String query, Map<String, Object> params, Supplier<T> call,
                       Function<? super T, Integer> rowCounter) {
        long start = System.nanoTime();
        try {
            T result = call.get();
            publish(db, operation, query, params, start, rowCounter.apply(result), null);
            return result;
        } catch (RuntimeException e) {
            publish(db, operation, query, params, start, null, e.getMessage() != null ? e.getMessage() : e.toString());
            throw e;
        }
    }

    public <T> T trace(DataStore db, String operation, String query, Supplier<T> call) {
        return trace(db, operation, query, Map.of(), call);
    }

    private void publish(DataStore db, String operation, String query, Map<String, Object> params,
                         long startNanos, Integer rows, String error) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        QuerySpan span = new QuerySpan(db, operation, query, params == null ? Map.of() : params, ms, rows, error);
        for (QueryListener l : listeners) {
            try {
                l.onQuery(span);
            } catch (RuntimeException e) {
                log.warn("Query listener {} failed", l.getClass().getSimpleName(), e); // never break the query itself
            }
        }
    }

    private static Integer countRows(Object result) {
        if (result instanceof Collection<?> c) return c.size();
        return null;
    }
}
