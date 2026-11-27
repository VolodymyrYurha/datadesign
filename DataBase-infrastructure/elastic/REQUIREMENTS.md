# Elasticsearch data requirements

> [!IMPORTANT]
> Scope agreed with the professor: Elasticsearch (with Kibana as the UI) stores and searches the
> **query logs** of our system. It does not store USI domain data for now. Indexing the domain
> entities is kept as an optional future improvement in section 8.

Every database call the backend makes (Neo4j, MongoDB, and Elasticsearch itself) is recorded and sent
here, so the logs are a searchable history of every Cypher query, Mongo command and ES request, with
timing, result size and errors. Kibana provides dashboards and ad-hoc exploration on top.

## 1. Where the data comes from

The backend already produces this data (`BackEnd/infrastructure/.../tracing`):

- `QueryTracer` wraps each DB call and produces a `QuerySpan`:
  `db`, `operation`, `query`, `params`, `durationMs`, `rows`, `error`.
- Each HTTP request collects its spans into a `RequestTrace`:
  `traceId`, `method`, `path`, `status`, `durationMs`, `spans[]`.
- Finished traces go to a `TraceSink`. Today it only logs; an Elasticsearch sink writes the documents
  below, asynchronously, so logging never slows down or breaks a response.

The documents add a few fields the backend knows but does not put in the record yet
(timestamp, user, client info, app version). Those are marked **new** below.

## 2. Indices

Two data streams, written by the same sink for every finished request:

| Data stream | One document per | Why |
|---|---|---|
| `query-logs` | HTTP request (`RequestTrace`) | Request-level questions: slow endpoints, error rates, which queries one request ran. Spans are a `nested` list |
| `query-spans` | DB call (`QuerySpan`), with request fields copied in | Flat documents for Kibana: Lens and dashboards cannot aggregate inside `nested` fields, so per-query charts (latency per DB, top operations) need this flat copy |

Both are **data streams** (append-only, `@timestamp` required) created from index templates, so new
backing indices roll over automatically. Data streams only accept `create` operations, which fits logs.

## 3. `query-logs` mapping (one document per request)

| Field | Type | Source | Notes |
|---|---|---|---|
| `@timestamp` | date | **new** | Request start time, UTC |
| `traceId` | keyword | RequestTrace | Same value as the `X-Trace-Id` response header |
| `http.method` | keyword | RequestTrace.method | |
| `http.path` | keyword | RequestTrace.path | Concrete path, e.g. `/api/courses/course:abc` |
| `http.route` | keyword | **new** | Route template, e.g. `/api/courses/{id}`, so requests group by endpoint |
| `http.status` | short | RequestTrace.status | |
| `http.query` | keyword | **new** | Query string, if any |
| `durationMs` | long | RequestTrace.durationMs | Whole request |
| `outcome` | keyword | derived | `success` (status < 400), `client_error`, `server_error` |
| `error.message` | text + `.keyword` | **new** | Exception message for failed requests |
| `error.type` | keyword | **new** | Exception class |
| `user.id` | keyword | **new** | Admin user for `/api/admin/**` calls, else `anonymous` |
| `user.role` | keyword | **new** | `public`, `admin` |
| `client.ip` | ip | **new** | Optional; can be dropped for privacy |
| `client.userAgent` | keyword | **new** | Optional |
| `app.version` | keyword | **new** | Backend build version, to compare before/after a change |
| `spanCount` | integer | derived | |
| `dbs` | keyword (array) | derived | Distinct stores touched, e.g. `["neo4j","mongodb"]` |
| `dbDurationMs` | long | derived | Sum of span durations |
| `spans` | nested | RequestTrace.spans | Fields as in section 4, without the copied request fields |

## 4. `query-spans` mapping (one document per DB call)

| Field | Type | Source | Notes |
|---|---|---|---|
| `@timestamp` | date | **new** | Start time of the DB call |
| `traceId` | keyword | request | Joins back to `query-logs` |
| `spanIndex` | integer | derived | Position of the call inside the request (0, 1, ...) |
| `db` | keyword | QuerySpan.db | `neo4j`, `mongodb`, `elasticsearch` |
| `operation` | keyword | QuerySpan.operation | e.g. `findEligibleCourses`, `ping` |
| `query` | text (`query_text` analyzer) + `.keyword` (`ignore_above: 8191`) | QuerySpan.query | Raw Cypher / Mongo JSON / ES DSL. The text field allows searching for "all queries using `MATCH (p:Person)`" or "`$lookup`" |
| `queryHash` | keyword | derived | Hash of the query text (params are separate), so identical query shapes group together |
| `language` | keyword | derived | `cypher`, `mongo`, `es-dsl` |
| `params` | flattened | QuerySpan.params | Arbitrary keys; searchable as `params.courseId: "course:..."` |
| `durationMs` | long | QuerySpan.durationMs | |
| `rows` | integer | QuerySpan.rows | Null when not applicable |
| `error` | text + `.keyword` | QuerySpan.error | Null on success |
| `success` | boolean | derived | `error == null` |
| `http.method`, `http.route`, `http.status`, `user.id`, `app.version` | as in section 3 | request | Copied so spans can be filtered by endpoint and user without a join |

Analyzer `query_text`: a pattern tokenizer that splits on whitespace and punctuation but keeps
`$`-prefixed Mongo operators, `:`-prefixed Neo4j labels and identifiers with dots whole, plus a lowercase filter.
This makes `$group`, `:Course` or `offerings.semesterId` searchable as single terms.

`dynamic: "strict"` on both templates, except inside `params`, which is `flattened` on purpose.

## 5. Creation files

In this folder:

- `query-logs-template.json` and `query-spans-template.json`: index templates with `"data_stream": {}`,
  `index_patterns` `query-logs*` / `query-spans*`, the settings (analyzer, 1 shard, 0 replicas for local dev)
  and the mappings above.
- `ilm-query-logs.json` (optional): rollover at 1 GB or 7 days, delete after 30 days.
- `kibana/`: exported saved objects (data views `query-logs` and `query-spans`, dashboards), importable
  with the Kibana Saved Objects API.
- A small script that `PUT`s the ILM policy and templates, then creates the two data streams.

## 6. Data volume for the graded queries

Real traffic from a few demo sessions is not enough to make aggregations interesting. Two sources:

1. **Real traffic:** everything the backend logs while we use the app and run the graded Neo4j/Mongo queries.
2. **Replay generator** (`DataPipeline/`): calls the real API endpoints in a loop with varied parameters
   and a mix of public and admin users, including some invalid inputs to produce errors, over a spread of
   simulated timestamps (e.g. 30 days). Because it goes through the real backend, every document is a
   genuine trace. Generated traffic is tagged with `user.id: "replay-bot"` so it can be filtered out.

Target: at least 50k spans over 30 days.

## 7. Coverage check

The 7 graded Elasticsearch queries run over these indices:

| Query type | Example |
|---|---|
| Full-text over raw queries | Every query that uses `$lookup` or a variable-length path `*1..` |
| Bool with filters + range | Failed Neo4j calls slower than 200 ms in the last 24 h |
| Terms aggregation + metrics | Average and p95 latency per `db` and `operation` (percentiles) |
| Date histogram | Requests per hour split by `outcome` |
| Nested query | Requests where one single span hit MongoDB **and** took over 100 ms |
| Flattened field query | All calls made with `params.courseId` equal to a given id |
| Top hits / collapse | The slowest call for each `queryHash` |
| Significant terms | Operations that are over-represented among errors |

Kibana: a dashboard with latency per DB over time, error rate, slowest endpoints, top query shapes,
and a Discover saved search that opens all spans of a `traceId` (linked from the frontend inspector).

## 8. Future improvements (optional): domain data in Elasticsearch

If time allows, Elasticsearch can also index public USI data for full-text search. None of this is
required now; the domain model ([`DataPipeline/DATA-MODEL.md`](../../DataPipeline/DATA-MODEL.md)) does
not depend on it.

- Indices `usi-courses`, `usi-professors` and `usi-programs` (no students), each behind an alias (`usi-courses` -> `usi-courses-v1`), document `_id` = canonical id.
- Denormalized documents: related names (professors, programs) copied in so search needs no join;
  `nested` for professors and program memberships.
- Bilingual analysis: `usi_en` / `usi_it` analyzers per text field, `.raw` keyword sub-fields, an
  edge-n-gram `.suggest` sub-field for autocomplete, a search-time synonym list (`ml, machine learning`, ...).
- Loaded from `data/canonical/*.jsonl` with the `_bulk` API, independently of MongoDB.
- Enables fuzzy name search, highlighting, more-like-this course similarity and faceted catalogue search.
