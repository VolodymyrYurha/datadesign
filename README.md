# USI Study System

Course project for Data Design and Modeling (USI, Prof. Brambilla). A study system over USI academic
data that keeps different data in different NoSQL stores and combines them in one app.

| Folder | What | Stack |
|---|---|---|
| `DataBase-infrastructure/` | docker-compose for the whole stack + DB creation scripts | Neo4j 5.26, MongoDB 8.0, Elasticsearch 9.4, Kibana, DbGate |
| `BackEnd/` | REST API, one layer per DB + integration layer | Java 21, Spring Boot 4.2, Scalar API docs |
| `FrontEnd/` | Read-only UI with a query inspector | React 19, Vite, TypeScript, Tailwind |
| `DataPipeline/` | Scraping, data generation, loaders, Spark | Python 3, PySpark |

## Prerequisites

- Docker Desktop (give it at least 6 GB of RAM if you also run Kibana)
- JDK 21 (`brew install openjdk@21`)
- Node 20+

## Run everything in Docker

```bash
cd DataBase-infrastructure
cp .env.example .env
docker compose up -d --build
```

| URL | What |
|---|---|
| http://localhost:5173 | Frontend (status page + query inspector) |
| http://localhost:8080/scalar | API docs (Scalar) |
| http://localhost:8080/api/system/health | Pings all three DBs (200 if all up, 503 otherwise) |
| http://localhost:7474 | Neo4j Browser (user `neo4j`, password `usi-study-pass`) |
| http://localhost:8081 | DbGate, MongoDB web UI (no login, `USI MongoDB` connection preconfigured) |
| http://localhost:5601 | Kibana (no login) |

DB ports for drivers and desktop tools: Neo4j bolt `7687`, MongoDB `27017`, Elasticsearch `9200`.
After a code change, rebuild just that container: `docker compose up -d --build api` (or `web`).
The whole stack needs about 5-6 GB of RAM in Docker Desktop.

## Develop from the IDE

Start only the databases (and their UIs if you want them), then run the backend and frontend locally:

```bash
cd DataBase-infrastructure
docker compose up -d neo4j mongo elasticsearch      # + mongo-ui kibana

cd ../BackEnd
./mvnw install -DskipTests                           # first time only
./mvnw spring-boot:run -pl api                       # http://localhost:8080

cd ../FrontEnd
npm install
npm run dev                                          # http://localhost:5173
```

If the `api` or `web` container is running, stop it first (`docker compose stop api web`), since it uses the same port.

## Backend layout

`BackEnd/` is a Maven multi-module build. The parent `pom.xml` is the equivalent of a .NET `.sln`,
and each module is a project:

| Module | .NET analogue | Contains |
|---|---|---|
| `api` | WebAPI (startup project) | `main()`, controllers, `ApiResponse` envelope, error handler, CORS/OpenAPI config, trace-id filter, `application.yml` |
| `application` | BLL / Application | Services and use cases. Uses only `domain` interfaces |
| `domain` | Core / Domain | Models and repository interfaces. Plain Java, no DB code |
| `dal` | DAL | Neo4j, MongoDB and Elasticsearch implementations of the `domain` interfaces |
| `infrastructure` | Infrastructure | Cross-cutting: query tracing, security (admin guard, later) |

References point inward: `api -> application, dal, infrastructure`, `dal -> domain, infrastructure`,
`application -> domain`, `infrastructure -> domain`. Maven fails the build if a module uses a class
from a module it doesn't reference.

Adding a feature usually means: a model + repository interface in `domain`, the implementation in
`dal/<store>`, a service in `application`, and a controller in `api`. The DB health check
(`DatabaseHealthProbe` -> `MongoHealthProbe`/`Neo4jHealthProbe`/`ElasticHealthProbe` ->
`SystemHealthService` -> `SystemController`) is a working example of that path.

Run it with `./mvnw install -DskipTests && ./mvnw spring-boot:run -pl api` from `BackEnd/`, or open `BackEnd/pom.xml` in
IntelliJ and run `StudySystemApplication`.

### Query inspector contract

Every response uses the same envelope:

```json
{
  "data": { },
  "meta": {
    "traceId": "…",
    "durationMs": 24,
    "queries": [
      { "db": "neo4j", "operation": "health", "query": "CALL dbms.components() …", "params": {}, "durationMs": 15, "rows": null, "error": null }
    ]
  },
  "error": null
}
```

Wrap every DB call in `QueryTracer.trace(db, operation, rawQuery, params, () -> call)` and it shows up in
`meta.queries` and in the frontend inspector automatically. Each request also gets an `X-Trace-Id` header.
Finished traces go to a `TraceSink`; it only logs for now, and an Elasticsearch `query-logs` sink comes later.

### Read vs. write

The UI is read-only. Write/edit endpoints will live under `/api/admin/**` behind an admin auth guard
(see `infra/security`). Nothing there is built yet.
