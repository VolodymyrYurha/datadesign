#!/bin/sh
# Creates the query-log storage in Elasticsearch, and the Kibana data views if Kibana is reachable.
# Safe to run again: everything is PUT/overwritten, existing data streams are kept.
#
#   ./setup.sh                                   # localhost (IDE setup)
#   ES_URL=http://elasticsearch:9200 KIBANA_URL=http://kibana:5601 ./setup.sh   # inside compose
#
# Needs only curl. Runs automatically as the `elastic-init` / `kibana-init` compose services.
set -eu

ES_URL="${ES_URL:-http://localhost:9200}"
KIBANA_URL="${KIBANA_URL:-}"
DIR="$(cd "$(dirname "$0")" && pwd)"

put() { # put <path> <file>
  printf 'PUT %s ... ' "$1"
  curl -fsS -X PUT "$ES_URL$1" -H 'Content-Type: application/json' --data-binary "@$DIR/$2" >/dev/null
  echo ok
}

if [ "${SKIP_ES:-}" != "1" ]; then
  echo "Elasticsearch: $ES_URL"
  until curl -fs "$ES_URL/_cluster/health?wait_for_status=yellow&timeout=5s" >/dev/null; do
    echo "waiting for Elasticsearch..."; sleep 3
  done

  put /_ilm/policy/query-logs-policy ilm-query-logs.json
  put /_index_template/query-logs query-logs-template.json
  put /_index_template/query-spans query-spans-template.json

  for ds in query-logs query-spans; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$ES_URL/_data_stream/$ds")
    if [ "$code" = "200" ]; then
      echo "data stream $ds exists"
    else
      printf 'PUT /_data_stream/%s ... ' "$ds"
      curl -fsS -X PUT "$ES_URL/_data_stream/$ds" >/dev/null
      echo ok
    fi
  done
fi

if [ -n "$KIBANA_URL" ]; then
  echo "Kibana: $KIBANA_URL"
  until curl -fs "$KIBANA_URL/api/status" >/dev/null; do
    echo "waiting for Kibana..."; sleep 5
  done
  printf 'import kibana/saved-objects.ndjson ... '
  curl -fsS -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" \
    -H 'kbn-xsrf: true' -F "file=@$DIR/kibana/saved-objects.ndjson" >/dev/null
  echo ok
fi
