# MongoDB data requirements

MongoDB (database `usi`) stores the **full records** of the domain model
([`DataPipeline/DATA-MODEL.md`](../../DataPipeline/DATA-MODEL.md)): every field and all texts.
`_id` is the same id as in Neo4j.

## Collections

| Collection | One document per | Embedded |
|---|---|---|
| `programs` | Program | `courses[]`: `{courseId, title, type, studyYear}` |
| `courses` | Course | `professors[]`: `{professorId, name, role}`; `programs[]`: `{programId, name, type, studyYear}`; `prerequisites[]`: `{courseId, title}` |
| `professors` | Professor | `courses[]`: `{courseId, title, role}` |
| `students` | Student | `program`: `{programId, name, startYear, status}`; `courses[]`: `{courseId, title, ects, semester, status, grade}` |

Relationships are embedded as small summaries (id + name) on both sides, so a single read returns a full
record. That is fine because every list here is small: a student takes about 30 courses, a course has a
few professors and programs.

## Example

```json
{
  "_id": "course:35265626",
  "title": "User Experience Design",
  "semester": "fall",
  "ects": 6.0,
  "language": "en",
  "description": "...",
  "objectives": "...",
  "examInfo": "...",
  "url": "https://search.usi.ch/en/courses/35265626/user-experience-design",
  "professors": [
    { "professorId": "prof:95ecad00cca3b94274998db811a1beda", "name": "Monica Landoni", "role": "director" },
    { "professorId": "prof:c543f5bfc3f21587b8b79dee8ce84a93", "name": "Marc Langheinrich", "role": "director" }
  ],
  "programs": [ { "programId": "program:54", "name": "Master of Science in Artificial Intelligence", "type": "elective", "studyYear": 1 } ],
  "prerequisites": []
}
```

## Creation script (`init/01-collections.js`)

- Create the four collections with a `$jsonSchema` validator: required fields, types, enum values
  (`semester`, `level`, `type`, `role`, `status`), `grade` between 1 and 6, `ects` > 0.
- Indexes:
  - `courses`: `semester`; `professors.professorId`; `programs.programId`; text index on `title`, `description`, `objectives`
  - `professors`: `lastName`
  - `students`: `program.programId`; `courses.courseId`; unique `matriculationNumber`

## Loader

- Reads `data/canonical/*.jsonl` and builds the documents with their embedded summaries.
- Upserts by `_id` (`bulkWrite` with `replaceOne`, `upsert: true`), so reloading is safe.
- Reports documents the validator rejects.

## Query types this supports

| Type | Example |
|---|---|
| Filter + projection | Fall courses taught in English with 6 ECTS |
| Array query | Courses where a given professor is director (`$elemMatch`) |
| Aggregation | Average grade and pass rate per course (`$unwind`, `$group`) |
| `$lookup` | Students joined with their program's details |
| Bucketing | Grade distribution per program (`$bucket`) |
| Text search | Courses whose description mentions a keyword (`$text`) |
| Update | Recording a new grade for a student (admin API) |
