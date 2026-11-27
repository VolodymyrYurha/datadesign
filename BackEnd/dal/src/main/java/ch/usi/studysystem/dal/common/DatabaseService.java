package ch.usi.studysystem.dal.common;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import java.util.Map;
import java.util.function.Function;

/**
 * Base class for the per-database services ({@code Neo4jDatabaseService}, {@code MongoDatabaseService},
 * {@code ElasticDatabaseService}).
 *
 * <p>Every query goes through {@link #execute}, which times it and publishes it to all query
 * listeners: the application log, the frontend query inspector, and later the {@code query-logs}
 * index. A subclass only says which store it is and how to run the query.
 */
public abstract class DatabaseService {

    private final QueryTracer tracer;

    protected DatabaseService(QueryTracer tracer) {
        this.tracer = tracer;
    }

    /** Which database this service talks to. */
    protected abstract DataStore store();

    /**
     * Runs one query and logs it.
     *
     * @param operation short label for the log and the inspector, e.g. "find" or "search"
     * @param query     the raw query text exactly as sent (Cypher, Mongo JSON, ES JSON)
     * @param params    query parameters, or an empty map
     * @param call      the actual driver call; checked exceptions are allowed
     */
    protected <T> T execute(String operation, String query, Map<String, Object> params, DbCall<T> call) {
        return tracer.trace(store(), operation, query, params, () -> invoke(call));
    }

    /** Same as {@link #execute}, with a custom row count for results that are not a collection. */
    protected <T> T execute(String operation, String query, Map<String, Object> params, DbCall<T> call,
                            Function<? super T, Integer> rowCounter) {
        return tracer.trace(store(), operation, query, params, () -> invoke(call), rowCounter);
    }

    private <T> T invoke(DbCall<T> call) {
        try {
            return call.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DatabaseException(store(), e);
        }
    }

    /** A driver call that may throw checked exceptions (e.g. IOException from the ES client). */
    @FunctionalInterface
    public interface DbCall<T> {
        T run() throws Exception;
    }
}
