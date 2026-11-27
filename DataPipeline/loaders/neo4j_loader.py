"""Neo4j: nodes with only id + name, relationships with the properties queries filter on.
Spec: DataBase-infrastructure/neo4j/REQUIREMENTS.md. UNWIND + MERGE on id, so reloading is safe."""
from __future__ import annotations

from pathlib import Path

from neo4j import GraphDatabase

from .dataset import Dataset, full_name

CONSTRAINTS = Path(__file__).resolve().parents[2] / "DataBase-infrastructure" / "neo4j" / "init" / "01-constraints.cypher"
BATCH = 1000


def _batches(rows: list[dict]):
    for i in range(0, len(rows), BATCH):
        yield rows[i:i + BATCH]


def _strip_comments(text: str) -> str:
    return "\n".join(line for line in text.splitlines() if not line.strip().startswith("//"))


def load(ds: Dataset, uri: str, user: str, password: str, fresh: bool = False) -> dict[str, int]:
    with GraphDatabase.driver(uri, auth=(user, password)) as driver:
        driver.verify_connectivity()
        with driver.session() as s:
            if fresh:
                s.run("MATCH (n) CALL (n) { DETACH DELETE n } IN TRANSACTIONS OF 5000 ROWS").consume()
            for stmt in _strip_comments(CONSTRAINTS.read_text()).split(";"):
                if stmt.strip():
                    s.run(stmt).consume()

            def nodes(label: str, rows: list[dict]) -> None:
                for b in _batches(rows):
                    s.run(f"UNWIND $rows AS r MERGE (n:{label} {{id: r.id}}) SET n.name = r.name", rows=b).consume()

            nodes("Program", [{"id": p["id"], "name": p["name"]} for p in ds.programs])
            nodes("Course", [{"id": c["id"], "name": c["title"]} for c in ds.courses])
            nodes("Professor", [{"id": p["id"], "name": full_name(p)} for p in ds.professors])
            nodes("Student", [{"id": x["id"], "name": full_name(x)} for x in ds.students])

            def rels(query: str, rows: list[dict]) -> None:
                for b in _batches(rows):
                    s.run("UNWIND $rows AS r " + query, rows=b).consume()

            rels("MATCH (a:Program {id: r.programId}), (b:Course {id: r.courseId}) "
                 "MERGE (a)-[x:INCLUDES]->(b) SET x.type = r.type, x.studyYear = r.studyYear", ds.program_courses)
            rels("MATCH (a:Professor {id: r.professorId}), (b:Course {id: r.courseId}) "
                 "MERGE (a)-[x:TEACHES]->(b) SET x.role = r.role", ds.teaches)
            rels("MATCH (a:Course {id: r.courseId}), (b:Course {id: r.requiresCourseId}) "
                 "MERGE (a)-[x:REQUIRES]->(b) SET x.source = r.source", ds.requires)
            rels("MATCH (a:Student {id: r.studentId}), (b:Program {id: r.programId}) "
                 "MERGE (a)-[x:ENROLLED_IN]->(b) SET x.startYear = r.startYear, x.status = r.status", ds.enrollments)
            rels("MATCH (a:Student {id: r.studentId}), (b:Course {id: r.courseId}) "
                 "MERGE (a)-[x:TOOK]->(b) SET x.semester = r.semester, x.status = r.status, x.grade = r.grade",
                 [{**t, "grade": t.get("grade")} for t in ds.took])

            counts = s.run("MATCH (n) RETURN count(n) AS nodes").single()["nodes"]
            rel_count = s.run("MATCH ()-[r]->() RETURN count(r) AS rels").single()["rels"]
    return {"nodes": counts, "relationships": rel_count}
