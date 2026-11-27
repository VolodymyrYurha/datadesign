package ch.usi.studysystem.dal.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.usi.studysystem.domain.db.MongoService;
import ch.usi.studysystem.domain.professor.Professor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Checks the query the repository sends and the mapping back, with MongoService mocked. */
class MongoProfessorRepositoryTest {

    private final MongoService mongo = mock(MongoService.class);
    private final MongoProfessorRepository repo = new MongoProfessorRepository(mongo);

    private static final Map<String, Object> DOC = Map.of(
            "_id", "prof:1", "firstName", "Monica", "lastName", "Landoni", "position", "Professor",
            "courses", List.of(Map.of("courseId", "course:9", "title", "UX Design", "role", "director")));

    @Test
    void mapsDocumentToDomainModel() {
        when(mongo.find(eq("professors"), anyString())).thenReturn(List.of(DOC));

        Professor p = repo.findById("prof:1").orElseThrow();

        assertThat(p.fullName()).isEqualTo("Monica Landoni");
        assertThat(p.courses()).containsExactly(new Professor.TaughtCourse("course:9", "UX Design", "director"));
    }

    @Test
    void searchEscapesUserInput() {
        when(mongo.aggregate(eq("professors"), anyString())).thenReturn(List.of(DOC));
        ArgumentCaptor<String> pipeline = ArgumentCaptor.forClass(String.class);

        repo.search("o\"} ], \"$where\": \"x");

        verify(mongo).aggregate(eq("professors"), pipeline.capture());
        assertThat(pipeline.getValue()).doesNotContain("\"$where\": \"x").contains("\\\"");
    }

    @Test
    void emptySearchMatchesEverything() {
        when(mongo.aggregate(eq("professors"), anyString())).thenReturn(List.of());
        ArgumentCaptor<String> pipeline = ArgumentCaptor.forClass(String.class);

        repo.search(" ");

        verify(mongo).aggregate(eq("professors"), pipeline.capture());
        assertThat(pipeline.getValue()).contains("{ \"$match\": {} }");
    }
}
