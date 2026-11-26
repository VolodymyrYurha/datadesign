package ch.usi.studysystem.dal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.infrastructure.tracing.QuerySpan;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks that every query going through DatabaseService is published to the query listeners. */
class DatabaseServiceTest {

    private final List<QuerySpan> logged = new ArrayList<>();
    private final FakeService service = new FakeService(new QueryTracer(List.of(logged::add)));

    @Test
    void logsSuccessfulQuery() {
        List<String> rows = service.run("MATCH (n) RETURN n", () -> List.of("a", "b"));

        assertThat(rows).hasSize(2);
        assertThat(logged).singleElement().satisfies(s -> {
            assertThat(s.db()).isEqualTo(DataStore.neo4j);
            assertThat(s.query()).isEqualTo("MATCH (n) RETURN n");
            assertThat(s.params()).containsEntry("limit", 10);
            assertThat(s.rows()).isEqualTo(2);
            assertThat(s.error()).isNull();
        });
    }

    @Test
    void logsAndWrapsFailedQuery() {
        assertThatThrownBy(() -> service.run("BAD", () -> {
            throw new IOException("boom");
        })).isInstanceOf(DatabaseException.class).hasMessageContaining("boom");

        assertThat(logged).singleElement().satisfies(s -> {
            assertThat(s.error()).contains("boom");
            assertThat(s.rows()).isNull();
        });
    }

    private static class FakeService extends DatabaseService {
        FakeService(QueryTracer tracer) {
            super(tracer);
        }

        @Override
        protected DataStore store() {
            return DataStore.neo4j;
        }

        <T> T run(String query, DbCall<T> call) {
            return execute("test", query, Map.of("limit", 10), call);
        }
    }
}
