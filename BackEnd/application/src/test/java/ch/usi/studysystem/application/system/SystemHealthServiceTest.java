package ch.usi.studysystem.application.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.db.ElasticService;
import ch.usi.studysystem.domain.db.MongoService;
import ch.usi.studysystem.domain.db.Neo4jService;
import ch.usi.studysystem.domain.system.DbStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Example of unit-testing an application service with mocked DB services: no database needed. */
class SystemHealthServiceTest {

    private Neo4jService neo4j;
    private MongoService mongo;
    private ElasticService elastic;
    private SystemHealthService service;

    @BeforeEach
    void setUp() {
        neo4j = mock(Neo4jService.class);
        mongo = mock(MongoService.class);
        elastic = mock(ElasticService.class);
        service = new SystemHealthService(neo4j, mongo, elastic);
    }

    @Test
    void allUp() {
        when(mongo.runCommand(anyString())).thenReturn(Map.of("version", "8.0.0"));
        when(neo4j.query(anyString())).thenReturn(List.of(Map.of("version", "5.26.0", "edition", "community")));
        when(elastic.info()).thenReturn(Map.of("cluster_name", "test", "version", Map.of("number", "9.4.5")));

        List<DbStatus> result = service.checkAll();

        assertThat(result).extracting(DbStatus::up).containsOnly(true);
        assertThat(result).extracting(DbStatus::version).containsExactly("8.0.0", "5.26.0", "9.4.5");
    }

    @Test
    void oneDown() {
        when(mongo.runCommand(anyString())).thenReturn(Map.of("version", "8.0.0"));
        when(neo4j.query(anyString())).thenThrow(new RuntimeException("connection refused"));
        when(elastic.info()).thenReturn(Map.of("cluster_name", "test", "version", Map.of("number", "9.4.5")));

        DbStatus neo = service.checkAll().stream().filter(s -> s.db() == DataStore.neo4j).findFirst().orElseThrow();

        assertThat(neo.up()).isFalse();
        assertThat(neo.detail()).isEqualTo("connection refused");
    }
}
