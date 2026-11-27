package ch.usi.studysystem.domain.db;

import java.util.List;
import java.util.Map;

/** Direct Cypher access to Neo4j. */
public interface Neo4jService {

    /**
     * Runs a read query and returns one map per record, keyed by the RETURN aliases.
     * Nodes come back as {@code {elementId, labels, ...properties}}, relationships as
     * {@code {elementId, type, startElementId, endElementId, ...properties}}.
     */
    List<Map<String, Object>> query(String cypher, Map<String, Object> params);

    default List<Map<String, Object>> query(String cypher) {
        return query(cypher, Map.of());
    }

    /** Runs a write query (CREATE / MERGE / SET / DELETE) and returns the records it returns. */
    List<Map<String, Object>> write(String cypher, Map<String, Object> params);
}
