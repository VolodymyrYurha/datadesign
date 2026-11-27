package ch.usi.studysystem.domain.db;

import java.util.List;
import java.util.Map;

/**
 * Direct access to MongoDB with queries written as JSON (the same syntax as mongosh), e.g.
 * {@code find("courses", "{ \"credits\": { \"$gte\": 6 } }")}.
 * Documents come back as maps; ObjectIds and dates are converted to strings.
 */
public interface MongoService {

    List<Map<String, Object>> find(String collection, String filterJson);

    /** {@code pipelineJson} is a JSON array of stages, e.g. {@code [{ "$group": { ... } }]}. */
    List<Map<String, Object>> aggregate(String collection, String pipelineJson);

    long count(String collection, String filterJson);

    /** Runs a database command, e.g. {@code { "buildInfo": 1 }}. */
    Map<String, Object> runCommand(String commandJson);
}
