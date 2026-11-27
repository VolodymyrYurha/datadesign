"""Reads the canonical JSONL files once and derives what both loaders need.

Prerequisites (DATA-MODEL.md, SCRAPING.md): entries scraped from course pages
(prerequisite_details.jsonl) are matched to this year's courses by normalized title and become
REQUIRES edges with source "scraped". Synthetic edges (prerequisites.jsonl) are kept only for courses
that have no scraped prerequisite. Unmatched titles go to data/raw/_unmatched_prerequisites.jsonl.
Edges that would create a cycle are dropped.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]  # DataPipeline/
CANONICAL = ROOT / "data" / "canonical"
UNMATCHED = ROOT / "data" / "raw" / "_unmatched_prerequisites.jsonl"

# Courses renamed between years: normalized old title -> normalized current title. Add entries after
# reviewing _unmatched_prerequisites.jsonl.
TITLE_ALIASES: dict[str, str] = {}


def read_jsonl(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def normalize_title(title: str) -> str:
    t = title.lower().replace("&", " and ")
    t = re.sub(r"[^\w\s]", " ", t)
    t = re.sub(r"\s+", " ", t).strip()
    return TITLE_ALIASES.get(t, t)


def full_name(person: dict) -> str:
    return f"{person.get('firstName', '')} {person.get('lastName', '')}".strip()


@dataclass
class Dataset:
    programs: list[dict]
    courses: list[dict]
    professors: list[dict]
    students: list[dict]
    program_courses: list[dict]
    teaches: list[dict]
    enrollments: list[dict]
    took: list[dict]
    requires: list[dict] = field(default_factory=list)  # {courseId, requiresCourseId, source}
    unmatched: list[dict] = field(default_factory=list)
    dropped_cycles: list[dict] = field(default_factory=list)
    unknown_ects: list[str] = field(default_factory=list)

    def by_id(self, rows: list[dict]) -> dict[str, dict]:
        return {r["id"]: r for r in rows}

    def summary(self) -> str:
        scraped = sum(1 for r in self.requires if r["source"] == "scraped")
        return (f"programs={len(self.programs)} courses={len(self.courses)} professors={len(self.professors)} "
                f"students={len(self.students)} took={len(self.took)} requires={len(self.requires)} "
                f"(scraped={scraped}, synthetic={len(self.requires) - scraped}) "
                f"unmatched_prerequisites={len(self.unmatched)} dropped_for_cycles={len(self.dropped_cycles)} "
                f"courses_without_ects={len(self.unknown_ects)}")


def load(directory: Path = CANONICAL, write_unmatched: bool = True) -> Dataset:
    r = lambda name: read_jsonl(directory / f"{name}.jsonl")  # noqa: E731
    ds = Dataset(
        programs=r("programs"), courses=r("courses"), professors=r("professors"), students=r("students"),
        program_courses=r("program_courses"), teaches=r("teaches"), enrollments=r("enrollments"), took=r("took"),
    )
    # The scraper writes 0 ECTS when a page shows none (e.g. the Seminario Alpino courses): treat as unknown.
    for c in ds.courses:
        if not c.get("ects"):
            c.pop("ects", None)
            ds.unknown_ects.append(c["id"])
    details = r("prerequisite_details") if (directory / "prerequisite_details.jsonl").exists() else []
    synthetic = r("prerequisites") if (directory / "prerequisites.jsonl").exists() else []
    _build_requires(ds, details, synthetic)
    _check_references(ds)
    if write_unmatched and ds.unmatched:
        UNMATCHED.parent.mkdir(parents=True, exist_ok=True)
        with UNMATCHED.open("w", encoding="utf-8") as f:
            for u in ds.unmatched:
                f.write(json.dumps(u, ensure_ascii=False) + "\n")
    return ds


def _build_requires(ds: Dataset, details: list[dict], synthetic: list[dict]) -> None:
    by_title: dict[str, list[str]] = {}
    for c in ds.courses:
        by_title.setdefault(normalize_title(c["title"]), []).append(c["id"])

    edges: list[dict] = []
    seen: set[tuple[str, str]] = set()

    def add(course_id: str, required_id: str, source: str) -> None:
        if course_id == required_id or (course_id, required_id) in seen:
            return
        if _reachable(edges, required_id, course_id):  # required already (indirectly) requires course
            ds.dropped_cycles.append({"courseId": course_id, "requiresCourseId": required_id, "source": source})
            return
        seen.add((course_id, required_id))
        edges.append({"courseId": course_id, "requiresCourseId": required_id, "source": source})

    with_scraped: set[str] = set()
    for d in details:
        matches = by_title.get(normalize_title(d["prerequisiteName"]), [])
        if not matches:
            ds.unmatched.append(d)
            continue
        for m in matches:
            add(d["courseId"], m, "scraped")
        with_scraped.add(d["courseId"])

    for s in synthetic:
        if s["courseId"] not in with_scraped:
            add(s["courseId"], s["requiresCourseId"], "synthetic")
    ds.requires = edges


def _reachable(edges: list[dict], start: str, target: str) -> bool:
    graph: dict[str, list[str]] = {}
    for e in edges:
        graph.setdefault(e["courseId"], []).append(e["requiresCourseId"])
    stack, seen = [start], set()
    while stack:
        n = stack.pop()
        if n == target:
            return True
        if n in seen:
            continue
        seen.add(n)
        stack.extend(graph.get(n, []))
    return False


def _check_references(ds: Dataset) -> None:
    """Both loaders refuse to run if a relationship points to an entity that does not exist."""
    P = {x["id"] for x in ds.programs}
    C = {x["id"] for x in ds.courses}
    F = {x["id"] for x in ds.professors}
    S = {x["id"] for x in ds.students}
    problems = []
    for x in ds.program_courses:
        if x["programId"] not in P or x["courseId"] not in C:
            problems.append(("program_courses", x))
    for x in ds.teaches:
        if x["professorId"] not in F or x["courseId"] not in C:
            problems.append(("teaches", x))
    for x in ds.enrollments:
        if x["studentId"] not in S or x["programId"] not in P:
            problems.append(("enrollments", x))
    for x in ds.took:
        if x["studentId"] not in S or x["courseId"] not in C:
            problems.append(("took", x))
    for x in ds.requires:
        if x["courseId"] not in C or x["requiresCourseId"] not in C:
            problems.append(("requires", x))
    if problems:
        head = "\n".join(f"  {name}: {row}" for name, row in problems[:10])
        raise ValueError(f"{len(problems)} dangling reference(s) in the dataset:\n{head}")
