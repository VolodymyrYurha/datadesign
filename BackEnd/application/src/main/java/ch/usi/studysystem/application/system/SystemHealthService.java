package ch.usi.studysystem.application.system;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.db.ElasticService;
import ch.usi.studysystem.domain.db.MongoService;
import ch.usi.studysystem.domain.db.Neo4jService;
import ch.usi.studysystem.domain.system.DbStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Pings every database with its cheapest query. Only depends on the domain interfaces,
 * so a unit test can pass mocks (see SystemHealthServiceTest).
 */
@Service
public class SystemHealthService {

    static final String NEO4J_PING = "CALL dbms.components() YIELD name, versions, edition "
            + "RETURN name, versions[0] AS version, edition";
    static final String MONGO_PING = "{ \"buildInfo\": 1 }";

    private final Neo4jService neo4j;
    private final MongoService mongo;
    private final ElasticService elastic;

    public SystemHealthService(Neo4jService neo4j, MongoService mongo, ElasticService elastic) {
        this.neo4j = neo4j;
        this.mongo = mongo;
        this.elastic = elastic;
    }

    public List<DbStatus> checkAll() {
        return List.of(checkMongo(), checkNeo4j(), checkElastic());
    }

    DbStatus checkMongo() {
        return check(DataStore.mongodb, () -> {
            Map<String, Object> info = mongo.runCommand(MONGO_PING);
            return new Probe((String) info.get("version"), "database: usi");
        });
    }

    DbStatus checkNeo4j() {
        return check(DataStore.neo4j, () -> {
            Map<String, Object> row = neo4j.query(NEO4J_PING).getFirst();
            return new Probe((String) row.get("version"), row.get("edition") + " edition");
        });
    }

    @SuppressWarnings("unchecked")
    DbStatus checkElastic() {
        return check(DataStore.elasticsearch, () -> {
            Map<String, Object> info = elastic.info();
            Map<String, Object> version = (Map<String, Object>) info.get("version");
            return new Probe((String) version.get("number"), "cluster: " + info.get("cluster_name"));
        });
    }

    private record Probe(String version, String detail) {
    }

    private static DbStatus check(DataStore store, Supplier<Probe> call) {
        long start = System.nanoTime();
        try {
            Probe p = call.get();
            return new DbStatus(store, true, ms(start), p.version(), p.detail());
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new DbStatus(store, false, ms(start), null, msg);
        }
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
