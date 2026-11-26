# Elasticsearch: query logs

Elasticsearch stores the backend's query logs (see [REQUIREMENTS.md](REQUIREMENTS.md)); Kibana is the UI.

| File | What |
|---|---|
| `query-logs-template.json` | Index template for the `query-logs` data stream: one document per HTTP request, DB calls nested in `spans` |
| `query-spans-template.json` | Index template for the `query-spans` data stream: one flat document per DB call, for Kibana charts |
| `ilm-query-logs.json` | ILM policy: roll over at 1 GB or 7 days, delete after 30 days |
| `kibana/saved-objects.ndjson` | Data views `query-logs*` / `query-spans*` and the "Spans of a trace" saved search |
| `setup.sh` | PUTs the policy and templates, creates both data streams, imports the Kibana objects. Safe to re-run |

`docker compose up` runs it for you (`elastic-init` and `kibana-init` services). From the IDE setup:

```bash
./setup.sh                                          # Elasticsearch only
KIBANA_URL=http://localhost:5601 ./setup.sh         # + Kibana data views
```

After a mapping change, re-run `setup.sh`, then roll the streams over so new backing indices pick it up:
`curl -X POST localhost:9200/query-spans/_rollover` (or delete the data streams locally to start fresh).

The backend writes here through `ElasticQueryLogSink` (BackEnd `dal/elastic`); set
`QUERY_LOGS_ENABLED=false` to turn it off.

Quick checks:

```bash
curl -s 'localhost:9200/query-logs/_search?size=1&sort=@timestamp:desc' | jq '.hits.hits[0]._source'
curl -s 'localhost:9200/query-spans/_search' -H 'Content-Type: application/json' \
  -d '{"size":0,"aggs":{"by_db":{"terms":{"field":"db"},"aggs":{"p95":{"percentiles":{"field":"durationMs","percents":[95]}}}}}}'
```
