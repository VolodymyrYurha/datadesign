package ch.usi.studysystem.domain.professor;

import java.util.List;

/**
 * A professor (anyone who teaches) with the courses they teach. Plain Java, no DB types:
 * the same model would come from Mongo, a test, or any other store.
 */
public record Professor(
        String id,
        String firstName,
        String lastName,
        String position,
        String profileUrl,
        List<TaughtCourse> courses) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    /** A course this professor teaches, with their role in it. */
    public record TaughtCourse(String courseId, String title, String role) {
    }
}
