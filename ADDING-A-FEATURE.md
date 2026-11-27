# Adding a feature: the professors example

The Professors page is a small, complete example of one read feature going through every layer.
Copy its shape for new features. The request flows like this:

```
FrontEnd  ProfessorsPage.tsx ──GET /api/professors?q=lan──┐
                                                          v
api           ProfessorController      (controller)        ── returns ApiResponse<List<Professor>>
application   ProfessorService         (use case)          ── knows only the interface below
domain        ProfessorRepository      (interface) + Professor (model)
dal/mongo     MongoProfessorRepository (implementation)    ── writes the query as JSON
domain/db     MongoService             (interface)         ── the generic "run this Mongo query"
dal/mongo     MongoDatabaseService     (driver code)       ── extends DatabaseService: times + logs every query
                                                          │
          QueryListeners ──> log line, response meta.queries (inspector), Elasticsearch query-spans
```

## The layers, with their .NET equivalents

| File | Layer | .NET equivalent | What it does |
|---|---|---|---|
| `domain/.../professor/Professor.java` | Domain model | A `record` in `Core/Domain` | Plain data. A Java `record` is like a C# `record`: immutable, with equals and getters (`p.lastName()`) |
| `domain/.../professor/ProfessorRepository.java` | Domain interface | `IProfessorRepository` | What the app needs, not how it is stored |
| `dal/.../mongo/MongoProfessorRepository.java` | DAL | `ProfessorRepository : IProfessorRepository` in the Infrastructure project | Builds the Mongo query as JSON, sends it through `MongoService`, maps the `Map` result to `Professor` |
| `application/.../professor/ProfessorService.java` | Application | `ProfessorService` in the Application/BLL project | Use cases and rules (here: trim and cap the search text) |
| `api/.../controllers/ProfessorController.java` | API | `[ApiController] ProfessorsController` | HTTP mapping, 404 for a missing id, OpenAPI docs |
| `FrontEnd/src/pages/ProfessorsPage.tsx` | UI | | Calls the API with `apiGet`, renders the table and the detail card |

## How the pieces are wired (dependency injection)

- `@Service`, `@Repository`, `@RestController` are like registering a class in `builder.Services`.
  Spring scans the packages and registers them as singletons, like `AddSingleton<IProfessorRepository, MongoProfessorRepository>()`.
- Constructor parameters are injected by type, exactly like ASP.NET Core constructor injection.
  `ProfessorService(ProfessorRepository professors)` gets `MongoProfessorRepository` because it is the only
  implementation of that interface.
- Maven modules enforce the direction: `application` cannot even see `dal`, so it can only use the interface.

## Why the repository goes through MongoService

`MongoProfessorRepository` never touches the Mongo driver. It writes the query as mongosh-style JSON
(the same thing you would type in DbGate or mongosh) and calls `mongo.aggregate(...)` / `mongo.find(...)`.
`MongoDatabaseService` runs it inside `DatabaseService.execute`, which gives every feature for free:

- the query, its params, time and row count in the frontend query inspector (right-hand panel),
- one log line (`db.mongodb : aggregate 3 ms, rows=9 | ...`),
- a document in the `query-spans` Elasticsearch data stream (Kibana).

User input is put into the JSON with `quote(...)` (a JSON string literal) and `Pattern.quote(...)` for regexes,
never pasted raw, so a search text cannot change the query.

## Tests

- `MongoProfessorRepositoryTest`: `MongoService` mocked with Mockito (like Moq): checks the mapping and that
  user input is escaped. No database needed.
- `SystemHealthServiceTest` shows the same pattern for an application service.

## Checklist for the next feature

1. Model + repository interface in `domain/<feature>/`.
2. Implementation in `dal/<store>/`, using `Neo4jService` / `MongoService` / `ElasticService`.
3. Service in `application/<feature>/` (inject the interface).
4. Controller in `api/controllers/` returning `ApiResponse.ok(...)`; throw `NotFoundException` for 404.
5. Types in `FrontEnd/src/api/types.ts`, a page in `FrontEnd/src/pages/`, an entry in `pages` in `App.tsx`.
6. A unit test with the DB service mocked.
7. Rebuild: `docker compose up -d --build api web` (in `DataBase-infrastructure/`).
