Neo4j creation scripts (constraints, indexes). Mounted read-only at `/init` in the container.

The loader applies `01-constraints.cypher` before every load (`cd DataPipeline && python -m loaders`).
Neo4j has no auto-run folder, so to apply it by hand:

    docker compose exec neo4j cypher-shell -u neo4j -p usi-study-pass -f /init/01-constraints.cypher
