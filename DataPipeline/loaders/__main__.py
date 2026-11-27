"""
Load data/canonical into Neo4j and MongoDB.

  python -m loaders                 # both, upsert (safe to re-run)
  python -m loaders --only neo4j    # or mongo
  python -m loaders --fresh         # wipe the stores' domain data first
  python -m loaders --dir data/sample

Connection settings (defaults match DataBase-infrastructure/docker-compose.yml):
  NEO4J_URI=bolt://localhost:7687 NEO4J_USER=neo4j NEO4J_PASSWORD=usi-study-pass
  MONGO_URI=mongodb://localhost:27017 MONGO_DB=usi
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from . import dataset


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="Load the canonical dataset into Neo4j and MongoDB")
    ap.add_argument("--only", choices=["neo4j", "mongo"])
    ap.add_argument("--dir", default=str(dataset.CANONICAL))
    ap.add_argument("--fresh", action="store_true", help="delete existing domain data before loading")
    args = ap.parse_args(argv)

    directory = Path(args.dir)
    if not directory.is_absolute() and not directory.exists():
        directory = dataset.ROOT / directory
    ds = dataset.load(directory)
    print("dataset:", ds.summary())
    if ds.unmatched:
        print(f"  {len(ds.unmatched)} scraped prerequisite(s) matched no current course, see {dataset.UNMATCHED}")

    ok = True
    if args.only in (None, "neo4j"):
        from . import neo4j_loader
        res = neo4j_loader.load(ds, os.getenv("NEO4J_URI", "bolt://localhost:7687"),
                                os.getenv("NEO4J_USER", "neo4j"), os.getenv("NEO4J_PASSWORD", "usi-study-pass"),
                                fresh=args.fresh)
        print("neo4j:", res)
    if args.only in (None, "mongo"):
        from . import mongo_loader
        res = mongo_loader.load(ds, os.getenv("MONGO_URI", "mongodb://localhost:27017"),
                                os.getenv("MONGO_DB", "usi"), fresh=args.fresh)
        print("mongo:", res)
        ok = res["rejected"] == 0
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
