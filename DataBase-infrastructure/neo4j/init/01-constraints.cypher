// Neo4j creation script: uniqueness constraints (they also index id) and lookup indexes.
// Safe to re-run. The loader (DataPipeline: python -m loaders) applies this file before loading;
// to apply it by hand:
//   docker compose exec neo4j cypher-shell -u neo4j -p usi-study-pass -f /init/01-constraints.cypher
CREATE CONSTRAINT program_id   IF NOT EXISTS FOR (n:Program)   REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT course_id    IF NOT EXISTS FOR (n:Course)    REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT professor_id IF NOT EXISTS FOR (n:Professor) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT student_id   IF NOT EXISTS FOR (n:Student)   REQUIRE n.id IS UNIQUE;
CREATE INDEX course_name    IF NOT EXISTS FOR (n:Course)    ON (n.name);
CREATE INDEX professor_name IF NOT EXISTS FOR (n:Professor) ON (n.name);
CREATE INDEX took_status    IF NOT EXISTS FOR ()-[r:TOOK]-()     ON (r.status);
CREATE INDEX includes_type  IF NOT EXISTS FOR ()-[r:INCLUDES]-() ON (r.type);
