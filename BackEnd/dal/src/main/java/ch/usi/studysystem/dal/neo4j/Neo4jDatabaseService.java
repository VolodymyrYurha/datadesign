package ch.usi.studysystem.dal.neo4j;

import ch.usi.studysystem.dal.common.DatabaseService;
import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.db.Neo4jService;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.RoutingControl;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;

@Service
public class Neo4jDatabaseService extends DatabaseService implements Neo4jService {

    private static final QueryConfig READ = QueryConfig.builder().withRouting(RoutingControl.READ).build();
    private static final QueryConfig WRITE = QueryConfig.builder().withRouting(RoutingControl.WRITE).build();

    private final Driver driver;

    public Neo4jDatabaseService(Driver driver, QueryTracer tracer) {
        super(tracer);
        this.driver = driver;
    }

    @Override
    protected DataStore store() {
        return DataStore.neo4j;
    }

    @Override
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        return run("query", cypher, params, READ);
    }

    @Override
    public List<Map<String, Object>> write(String cypher, Map<String, Object> params) {
        return run("write", cypher, params, WRITE);
    }

    private List<Map<String, Object>> run(String operation, String cypher, Map<String, Object> params, QueryConfig config) {
        return execute(operation, cypher, params, () -> driver.executableQuery(cypher)
                .withParameters(params)
                .withConfig(config)
                .execute()
                .records().stream()
                .map(Neo4jDatabaseService::toMap)
                .toList());
    }

    private static Map<String, Object> toMap(Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        record.keys().forEach(k -> row.put(k, convert(record.get(k).asObject())));
        return row;
    }

    /** Turns driver types (Node, Relationship, Path) into plain maps and lists. */
    static Object convert(Object value) {
        if (value instanceof Node n) {
            Map<String, Object> m = entity(n);
            m.put("labels", toList(n.labels()));
            return m;
        }
        if (value instanceof Relationship r) {
            Map<String, Object> m = entity(r);
            m.put("type", r.type());
            m.put("startElementId", r.startNodeElementId());
            m.put("endElementId", r.endNodeElementId());
            return m;
        }
        if (value instanceof Path p) {
            List<Object> parts = new ArrayList<>();
            p.nodes().forEach(n -> parts.add(convert(n)));
            List<Object> rels = new ArrayList<>();
            p.relationships().forEach(r -> rels.add(convert(r)));
            return Map.of("nodes", parts, "relationships", rels);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Neo4jDatabaseService::convert).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> m = new LinkedHashMap<>();
            map.forEach((k, v) -> m.put(String.valueOf(k), convert(v)));
            return m;
        }
        return value;
    }

    private static Map<String, Object> entity(Entity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("elementId", e.elementId());
        e.asMap(v -> convert(v.asObject())).forEach(m::put);
        return m;
    }

    private static List<String> toList(Iterable<String> it) {
        List<String> out = new ArrayList<>();
        it.forEach(out::add);
        return out;
    }
}
