package ch.usi.studysystem.dal.mongo;

import ch.usi.studysystem.domain.db.MongoService;
import ch.usi.studysystem.domain.professor.Professor;
import ch.usi.studysystem.domain.professor.ProfessorRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ProfessorRepository} on the {@code professors} collection.
 *
 * <p>It does not touch the Mongo driver: it writes the query as mongosh-style JSON and sends it through
 * {@link MongoService}, so every query is timed, logged, shown in the frontend inspector and written to
 * Elasticsearch without any code here. This class only knows the query and how to map a document.
 */
@Repository
public class MongoProfessorRepository implements ProfessorRepository {

    static final String COLLECTION = "professors";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MongoService mongo;

    public MongoProfessorRepository(MongoService mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<Professor> search(String name) {
        String match = (name == null || name.isBlank())
                ? "{}"
                : """
                  { "$or": [
                      { "lastName":  { "$regex": %1$s, "$options": "i" } },
                      { "firstName": { "$regex": %1$s, "$options": "i" } } ] }""".formatted(quote(Pattern.quote(name.strip())));
        String pipeline = """
                [
                  { "$match": %s },
                  { "$sort": { "lastName": 1, "firstName": 1 } }
                ]""".formatted(match);
        return mongo.aggregate(COLLECTION, pipeline).stream().map(MongoProfessorRepository::toProfessor).toList();
    }

    @Override
    public Optional<Professor> findById(String id) {
        return mongo.find(COLLECTION, "{ \"_id\": " + quote(id) + " }").stream()
                .findFirst()
                .map(MongoProfessorRepository::toProfessor);
    }

    /** User input goes into the JSON as a properly escaped string literal, never pasted raw. */
    private static String quote(String value) {
        return JSON.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    static Professor toProfessor(Map<String, Object> doc) {
        List<Map<String, Object>> courses = (List<Map<String, Object>>) doc.getOrDefault("courses", List.of());
        return new Professor(
                (String) doc.get("_id"),
                (String) doc.get("firstName"),
                (String) doc.get("lastName"),
                (String) doc.get("position"),
                (String) doc.get("profileUrl"),
                courses.stream()
                        .map(c -> new Professor.TaughtCourse(
                                (String) c.get("courseId"), (String) c.get("title"), (String) c.get("role")))
                        .toList());
    }
}
