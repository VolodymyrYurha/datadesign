package ch.usi.studysystem.domain.professor;

import java.util.List;
import java.util.Optional;

/**
 * Where professors come from (like an {@code IProfessorRepository} in .NET). The application layer
 * only sees this interface; {@code dal/mongo/MongoProfessorRepository} implements it on MongoDB.
 */
public interface ProfessorRepository {

    /** All professors sorted by last name, or only those whose first or last name contains {@code name}. */
    List<Professor> search(String name);

    Optional<Professor> findById(String id);
}
