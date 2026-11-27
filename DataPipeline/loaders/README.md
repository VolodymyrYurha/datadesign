# Loaders

Fill Neo4j and MongoDB from `data/canonical/*.jsonl`. Both are upserts, so re-running is safe.

`docker compose up` already does this through the `seed` service (image from `DataPipeline/Dockerfile.seed`,
which contains the loaders and the canonical data). Run the loaders by hand only while working on them:

```bash
cd DataPipeline
python3 -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt   # first time

# once per fresh Mongo volume it runs by itself; on an existing volume run it by hand:
docker compose -f ../DataBase-infrastructure/docker-compose.yml exec mongo \
  mongosh usi /docker-entrypoint-initdb.d/01-collections.js

python -m loaders                 # both stores
python -m loaders --only neo4j    # or mongo
python -m loaders --fresh         # wipe domain data first
```

Defaults match docker-compose; override with `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`, `MONGO_URI`, `MONGO_DB`.

| File | Does |
|---|---|
| `dataset.py` | Reads the JSONL once, builds `REQUIRES` edges, checks every reference |
| `neo4j_loader.py` | Applies `neo4j/init/01-constraints.cypher`, then `UNWIND` + `MERGE` nodes (id, name) and relationships |
| `mongo_loader.py` | Builds the four documents with embedded summaries on both sides, `replaceOne` upsert, reports validator rejections |

**Prerequisites.** Scraped entries (`prerequisite_details.jsonl`) are matched to this year's courses by
normalized title (lowercase, `&` -> `and`, no punctuation) and stored with `source: "scraped"`. Synthetic edges
(`prerequisites.jsonl`) are kept only for courses with no scraped prerequisite (`source: "synthetic"`).
Titles that match nothing go to `data/raw/_unmatched_prerequisites.jsonl`; add a `TITLE_ALIASES` entry in
`dataset.py` if a course was only renamed. Edges that would create a cycle are dropped.

**Data fixes on load.** `ects: 0` (pages that show no ECTS) is stored as unknown (field left out).

Result with the current dataset: 913 nodes and 10,178 relationships in Neo4j; 5 programs, 135 courses,
173 professors and 600 students in MongoDB; 37 prerequisites (21 scraped, 16 synthetic).
