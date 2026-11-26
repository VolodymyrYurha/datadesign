package ch.usi.studysystem.dal.mongo;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.system.DatabaseHealthProbe;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import org.bson.Document;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Order(1)
public class MongoHealthProbe implements DatabaseHealthProbe {

    private static final String COMMAND = "{ \"buildInfo\": 1 }";

    private final MongoTemplate mongo;
    private final QueryTracer tracer;

    public MongoHealthProbe(MongoTemplate mongo, QueryTracer tracer) {
        this.mongo = mongo;
        this.tracer = tracer;
    }

    @Override
    public DataStore store() {
        return DataStore.mongodb;
    }

    @Override
    public ProbeResult probe() {
        Document info = tracer.trace(DataStore.mongodb, "health", "db.runCommand(" + COMMAND + ")",
                () -> mongo.executeCommand(COMMAND));
        return new ProbeResult(info.getString("version"), "database: " + mongo.getDb().getName());
    }
}
