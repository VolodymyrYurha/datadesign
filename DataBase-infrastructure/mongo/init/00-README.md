Mongo init scripts. Any `*.js` or `*.sh` file in this folder runs once, when the
`mongo` container starts with an empty volume (`docker compose down -v` to re-run).

- `01-collections.js`: the four collections with `$jsonSchema` validation, plus indexes. Safe to re-run on an
  existing database: `docker compose exec mongo mongosh usi /docker-entrypoint-initdb.d/01-collections.js`

Data is loaded by `DataPipeline` (`python -m loaders`).
