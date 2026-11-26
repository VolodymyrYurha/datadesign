Neo4j creation scripts (constraints, indexes). Mounted read-only at `/init` in the container.
Neo4j has no auto-run folder, so apply them with:

    docker compose exec neo4j cypher-shell -u neo4j -p usi-study-pass -f /init/01-constraints.cypher
