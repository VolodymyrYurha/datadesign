Mongo init scripts. Any `*.js` or `*.sh` file in this folder runs once, when the
`mongo` container starts with an empty volume (`docker compose down -v` to re-run).

Planned: `01-collections.js` with collections + `$jsonSchema` validation.
