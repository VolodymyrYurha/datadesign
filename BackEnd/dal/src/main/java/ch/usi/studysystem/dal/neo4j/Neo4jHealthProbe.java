package ch.usi.studysystem.dal.neo4j;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.system.DatabaseHealthProbe;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import org.neo4j.driver.Driver;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Repository;

@Repository
@Order(2)
public class Neo4jHealthProbe implements DatabaseHealthProbe {

    private static final String CYPHER = "CALL dbms.components() YIELD name, versions, edition "
            + "RETURN name, versions[0] AS version, edition";

    private final Driver driver;
    private final QueryTracer tracer;

    public Neo4jHealthProbe(Driver driver, QueryTracer tracer) {
        this.driver = driver;
        this.tracer = tracer;
    }

    @Override
    public DataStore store() {
        return DataStore.neo4j;
    }

    @Override
    public ProbeResult probe() {
        return tracer.trace(DataStore.neo4j, "health", CYPHER, () -> {
            try (var session = driver.session()) {
                var rec = session.run(CYPHER).single();
                return new ProbeResult(rec.get("version").asString(), rec.get("edition").asString() + " edition");
            }
        });
    }
}
