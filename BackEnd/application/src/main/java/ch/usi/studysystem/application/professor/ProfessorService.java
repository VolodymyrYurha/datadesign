package ch.usi.studysystem.application.professor;

import ch.usi.studysystem.domain.professor.Professor;
import ch.usi.studysystem.domain.professor.ProfessorRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Use cases around professors. Depends only on the domain interface, so it does not know (or care)
 * that the data is in MongoDB. Business rules go here; for a plain list there is little to add.
 */
@Service
public class ProfessorService {

    static final int MAX_QUERY_LENGTH = 100;

    private final ProfessorRepository professors;

    public ProfessorService(ProfessorRepository professors) {
        this.professors = professors;
    }

    public List<Professor> list(String name) {
        String q = name == null ? null : name.strip();
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        return professors.search(q);
    }

    public Optional<Professor> get(String id) {
        return professors.findById(id);
    }
}
