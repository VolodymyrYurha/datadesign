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
| http://localhost:5173 | Frontend (professors, status, query inspector) |
| http://localhost:8080/scalar | API docs (Scalar) |
| http://localhost:8080/api/system/health | Pings all three DBs (200 if all up, 503 otherwise) |
| http://localhost:7474 | Neo4j Browser (user `neo4j`, password `usi-study-pass`) |
| http://localhost:8081 | DbGate, MongoDB web UI (no login, `USI MongoDB` connection preconfigured) |
| http://localhost:5601 | Kibana (no login) |

Neo4j and MongoDB come up **filled with the real scraped dataset** (no mocks): the one-shot `mongo-init`
service creates the collections and validators, then `seed` loads `DataPipeline/data/canonical` into both
stores (913 nodes / 10,178 relationships in Neo4j; 5 programs, 135 courses, 173 professors, 600 students in
MongoDB). It upserts by id, so it runs safely on every `up`. After the dataset changes:
`docker compose up -d --build seed`. Details: [`DataPipeline/loaders/README.md`](DataPipeline/loaders/README.md).

DB ports for drivers and desktop tools: Neo4j bolt `7687`, MongoDB `27017`, Elasticsearch `9200`.
After a code change, rebuild just that container: `docker compose up -d --build api` (or `web`).
The whole stack needs about 5-6 GB of RAM in Docker Desktop.

## Develop from the IDE

Start only the databases (and their UIs if you want them), then run the backend and frontend locally:

```bash
cd DataBase-infrastructure
docker compose up -d seed elastic-init             # Neo4j + Mongo (filled) + Elasticsearch; + mongo-ui kibana kibana-init

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
`dal/<store>`, a service in `application`, and a controller in `api`. The Professors page is a worked
example through every layer: [`ADDING-A-FEATURE.md`](ADDING-A-FEATURE.md). The DB health check
(`SystemHealthService` using `Neo4jService`/`MongoService`/`ElasticService` -> `SystemController`)
is a working example.

Run it with `./mvnw install -DskipTests && ./mvnw spring-boot:run -pl api` from `BackEnd/`, or open `BackEnd/pom.xml` in
IntelliJ and run `StudySystemApplication`.

### Database services and query logging

Raw DB access goes through one service per database. Each is an interface in `domain` (like an
`IXxxService` in .NET) with its implementation in `dal`, built on a shared abstract base class:

```
domain/db/Neo4jService     <- dal/neo4j/Neo4jDatabaseService    : query(cypher, params), write(cypher, params)
domain/db/MongoService     <- dal/mongo/MongoDatabaseService    : find, aggregate, count, runCommand (JSON, mongosh syntax)
domain/db/ElasticService   <- dal/elastic/ElasticDatabaseService: search, count, info (JSON, Kibana Dev Tools syntax)
                                     extends dal/common/DatabaseService (abstract)
```

- **Inject the interface**, never the implementation: `public CourseService(Neo4jService neo4j, MongoService mongo)`.
  In unit tests, pass `Mockito.mock(Neo4jService.class)` instead (see `SystemHealthServiceTest`).
- **`DatabaseService.execute(...)`** is the one place every query passes through. It times the call,
  counts rows, wraps checked driver exceptions in `DatabaseException`, and publishes a `QuerySpan`
  (db, raw query, params, ms, rows, error) to every `QueryListener`.
- **Query listeners** (`infrastructure/tracing`) are pluggable, like EF Core interceptors. To add one,
  implement `QueryListener` and annotate it with `@Component`. Current listeners:
  - `LoggingQueryListener`: one log line per query, with logger name `db.neo4j` / `db.mongodb` / `db.elasticsearch`,
    so you can tune them separately, e.g. `logging.level.db.neo4j: DEBUG`
  - `RequestTraceQueryListener`: adds the query to the HTTP response's `meta.queries` (frontend inspector)
- Results are plain `Map`/`List` values (ObjectIds and dates become strings, Neo4j nodes become maps with
  `elementId` and `labels`), so nothing outside `dal` depends on a DB driver.

Example log line (the trace id in brackets matches the `X-Trace-Id` response header):

```
[5fc515f4-…] db.neo4j : query 261 ms, rows=1 | CALL dbms.components() YIELD … | params={}
```

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

Every query made through the database services above shows up in `meta.queries` and in the frontend
inspector automatically. Each request also gets an `X-Trace-Id` header.

Finished requests go to every `TraceSink`:

- `LoggingTraceSink`: one summary log line per request
- `ElasticQueryLogSink` (`dal/elastic`): one document per request in the `query-logs` data stream and one per
  DB call in `query-spans`, written in the background with `_bulk`, so a slow or stopped Elasticsearch never
  affects a response. The trace records time, route template, status, error, caller (`admin` / `anonymous`)
  and app version. Mappings and setup: [`DataBase-infrastructure/elastic/`](DataBase-infrastructure/elastic/README.md).
  Turn it off with `QUERY_LOGS_ENABLED=false`.

In Kibana, open Discover with the `Query spans (per DB call)` data view, or the "Spans of a trace" saved search
filtered by `traceId`.

### Read vs. write (admin login)

The UI is read-only. Write/edit endpoints live under `/api/admin/**` and need an admin login:

```bash
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'          # -> data.token
curl -H "Authorization: Bearer <token>" localhost:8080/api/admin/whoami
```

- Credentials come from `ADMIN_USERNAME` / `ADMIN_PASSWORD` (default `admin` / `admin`). A blank password turns login off.
- The token is a JWT (HS256) valid for 8 h, signed with `ADMIN_TOKEN_SECRET`. Without a secret, a random
  one is made at every start, so you log in again after a restart.
- Scripts can send `X-Admin-Key: <ADMIN_API_KEY>` instead, when that variable is set.
- Anything else on `/api/admin/**` gets 401. Accepted calls are logged with `user.id` = the admin user.

In Scalar: run `POST /api/auth/login`, copy `data.token`, and paste it under Authentication > `bearer`.
The code: `AuthController`, `api/security/AdminApiKeyFilter`, and `AdminTokenService` / `AdminKeyVerifier`
in `infrastructure/security`. To add a write endpoint, put a controller under `/api/admin` and annotate it
with `@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)` (see `AdminController`).
