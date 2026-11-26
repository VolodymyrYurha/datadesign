package ch.usi.studysystem.dal.mongo;

import ch.usi.studysystem.dal.common.DatabaseService;
import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.db.MongoService;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class MongoDatabaseService extends DatabaseService implements MongoService {

    private final MongoTemplate mongo;

    public MongoDatabaseService(MongoTemplate mongo, QueryTracer tracer) {
        super(tracer);
        this.mongo = mongo;
    }

    @Override
    protected DataStore store() {
        return DataStore.mongodb;
    }

    @Override
    public List<Map<String, Object>> find(String collection, String filterJson) {
        String shown = "db." + collection + ".find(" + filterJson + ")";
        return execute("find", shown, Map.of(), () -> {
            List<Map<String, Object>> out = new ArrayList<>();
            mongo.getCollection(collection).find(Document.parse(filterJson)).forEach(d -> out.add(toMap(d)));
            return out;
        });
    }

    @Override
    public List<Map<String, Object>> aggregate(String collection, String pipelineJson) {
        String shown = "db." + collection + ".aggregate(" + pipelineJson + ")";
        return execute("aggregate", shown, Map.of(), () -> {
            List<Document> pipeline = Document.parse("{\"p\": " + pipelineJson + "}").getList("p", Document.class);
            List<Map<String, Object>> out = new ArrayList<>();
            mongo.getCollection(collection).aggregate(pipeline).forEach(d -> out.add(toMap(d)));
            return out;
        });
    }

    @Override
    public long count(String collection, String filterJson) {
        String shown = "db." + collection + ".countDocuments(" + filterJson + ")";
        return execute("count", shown, Map.of(),
                () -> mongo.getCollection(collection).countDocuments(Document.parse(filterJson)),
                n -> 1);
    }

    @Override
    public Map<String, Object> runCommand(String commandJson) {
        return execute("command", "db.runCommand(" + commandJson + ")", Map.of(),
                () -> toMap(mongo.executeCommand(commandJson)),
                r -> 1);
    }

    /** BSON document to plain Java: ObjectId to hex string, Date to ISO string, Decimal128 to BigDecimal. */
    private static Map<String, Object> toMap(Document doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        doc.forEach((k, v) -> m.put(k, convert(v)));
        return m;
    }

    private static Object convert(Object v) {
        if (v instanceof Document d) return toMap(d);
        if (v instanceof List<?> l) return l.stream().map(MongoDatabaseService::convert).toList();
        if (v instanceof ObjectId id) return id.toHexString();
        if (v instanceof Date date) return date.toInstant().toString();
        if (v instanceof Decimal128 dec) return dec.bigDecimalValue();
        return v;
    }
}
