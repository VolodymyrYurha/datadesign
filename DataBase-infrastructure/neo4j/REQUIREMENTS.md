# Neo4j data requirements

Neo4j stores the **relationships** of the domain model ([`DataPipeline/DATA-MODEL.md`](../../DataPipeline/DATA-MODEL.md)).
Nodes carry only `id` and `name`; all other details are in MongoDB, looked up by the same `id`.

## Nodes

| Label | Properties |
|---|---|
| `Program` | id, name |
| `Course` | id, name (the course title) |
| `Professor` | id, name (full name) |
| `Student` | id, name (full name) |

## Relationships

| Pattern | Properties |
|---|---|
| `(Program)-[:INCLUDES]->(Course)` | type (`compulsory` / `elective`), studyYear |
| `(Professor)-[:TEACHES]->(Course)` | role (`director` / `assistant`) |
| `(Course)-[:REQUIRES]->(Course)` | source (`scraped` / `synthetic`) |
| `(Student)-[:ENROLLED_IN]->(Program)` | startYear, status |
| `(Student)-[:TOOK]->(Course)` | semester, status, grade |

The relationship properties are the ones queries filter on (compulsory vs. elective, passed vs. failed),
so they stay here rather than in MongoDB only.

## Creation script (`init/01-constraints.cypher`)

```cypher
CREATE CONSTRAINT program_id   IF NOT EXISTS FOR (n:Program)   REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT course_id    IF NOT EXISTS FOR (n:Course)    REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT professor_id IF NOT EXISTS FOR (n:Professor) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT student_id   IF NOT EXISTS FOR (n:Student)   REQUIRE n.id IS UNIQUE;
CREATE INDEX course_name IF NOT EXISTS FOR (n:Course) ON (n.name);
```

## Loader

- Reads `data/canonical/*.jsonl`: nodes first, then relationships.
- `UNWIND` + `MERGE` on `id`, so reloading is safe.
- Fails if a relationship points to a node that does not exist.

## Query types this supports

| Type | Example |
|---|---|
| Pattern match | Courses taught by a professor |
| Variable-length path | All prerequisites of a course, direct and indirect |
| Shortest path | How two professors are connected through courses and programs |
| Aggregation | Professors teaching the most courses |
| Negation | Courses of a student's program they have not passed yet |
| Eligibility | Courses whose prerequisites a student has all passed |
| Shared neighbours | Students who took the same courses as a given student |
