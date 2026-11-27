# DataPipeline

Python side of the project: scraping usi.ch, generating students, loading the DBs, and Spark (Level 3).

```
DataPipeline/
  scraping/      usi.ch scraper + synthetic students -> data/canonical (see scraping/README.md)
  loaders/       canonical JSONL -> Neo4j and MongoDB (see loaders/README.md)
  data/sample/   small committed sample (canonical JSON format), used by loaders and the mock profile
  spark/         PySpark jobs (JSON -> Parquet, DataFrame/RDD versions of the queries)
```

Setup:

```bash
cd DataPipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

Fill the databases (with the stack running):

```bash
python -m loaders
```

Spark also needs a JDK 17+ on the PATH.
