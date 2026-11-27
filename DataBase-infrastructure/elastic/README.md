Elasticsearch index mappings go here as JSON, e.g. `usi-courses.json`, `query-logs.json`.
Create an index with:

    curl -X PUT localhost:9200/usi-courses -H 'Content-Type: application/json' -d @usi-courses.json
