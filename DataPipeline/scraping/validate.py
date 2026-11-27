
"""
Check the canonical dataset against the rules in DATA-MODEL.md.

  python -m scraping.validate               # data/canonical
  python -m scraping.validate --dir data/sample
Exit code 1 if there are errors.
"""
import argparse
import sys
from collections import defaultdict
from pathlib import Path

from . import config
from .io_utils import read_jsonl


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=str(config.CANONICAL))
    args = ap.parse_args(argv)
    d = Path(args.dir)
    if not d.is_absolute():
        d = config.ROOT / d if not d.exists() else d

    programs = read_jsonl(d / "programs.jsonl")
    courses = read_jsonl(d / "courses.jsonl")
    profs = read_jsonl(d / "professors.jsonl")
    students = read_jsonl(d / "students.jsonl")
    pcs = read_jsonl(d / "program_courses.jsonl")
    teaches = read_jsonl(d / "teaches.jsonl")
    prereqs = read_jsonl(d / "prerequisites.jsonl")
    prerequisite_details = read_jsonl(d / "prerequisite_details.jsonl")
    enrolls = read_jsonl(d / "enrollments.jsonl")
    took = read_jsonl(d / "took.jsonl")

    errors, warnings = [], []

    def err(m):
        errors.append(m)

    for name, rows in (
        ("programs", programs),
        ("courses", courses),
        ("professors", profs),
        ("students", students)
    ):
        ids = [r["id"] for r in rows]
        if len(ids) != len(set(ids)):
            err(f"{name}: duplicate ids")

    P = {r["id"] for r in programs}
    C = {r["id"] for r in courses}
    F = {r["id"] for r in profs}
    S = {r["id"] for r in students}

    for r in pcs:
        if r["programId"] not in P or r["courseId"] not in C:
            err(f"program_courses: dangling reference {r}")
        if r["type"] not in ("compulsory", "elective"):
            err(f"program_courses: bad type {r}")
        if not isinstance(r["studyYear"], int) or not 1 <= r["studyYear"] <= 6:
            err(f"program_courses: bad studyYear {r}")

    for r in teaches:
        if r["professorId"] not in F or r["courseId"] not in C:
            err(f"teaches: dangling reference {r}")
        if r["role"] not in ("director", "assistant"):
            err(f"teaches: bad role {r}")

    taught = {t["courseId"] for t in teaches}
    included = {r["courseId"] for r in pcs}


    for c in C:
        if c not in taught:
            err(f"course without professor: {c}")

    # Prerequisites: references + no cycles (Kahn)
    graph = defaultdict(set)
    indeg = defaultdict(int)

    for r in prereqs:
        a, b = r["courseId"], r["requiresCourseId"]

        if a not in C or b not in C:
            err(f"prerequisites: dangling reference {r}")
            continue

        if b not in graph[a]:
            graph[a].add(b)
            indeg[b] += 1

    queue = [c for c in C if indeg[c] == 0]
    visited = 0

    while queue:
        x = queue.pop()
        visited += 1

        for y in graph.get(x, ()):
            indeg[y] -= 1
            if indeg[y] == 0:
                queue.append(y)

    if visited != len(C):
        err("prerequisites contain a cycle")

    # Prerequisite details: validate extracted information
    for r in prerequisite_details:
        if r.get("courseId") not in C:
            err(f"prerequisite_details: unknown course {r}")

        if not r.get("prerequisiteName"):
            err(f"prerequisite_details: missing prerequisite name {r}")

        if not isinstance(r.get("professorNames"), list):
            err(f"prerequisite_details: professorNames must be a list {r}")

        if not r.get("academicYear"):
            err(f"prerequisite_details: missing academic year {r}")

    if students:
        prog_of = {}

        for r in enrolls:
            if r["studentId"] not in S or r["programId"] not in P:
                err(f"enrollments: dangling reference {r}")

            if r["studentId"] in prog_of:
                err(f"student with two programs: {r['studentId']}")

            prog_of[r["studentId"]] = r["programId"]

        for s in S:
            if s not in prog_of:
                err(f"student without enrollment: {s}")

        in_prog = defaultdict(set)

        for r in pcs:
            in_prog[r["programId"]].add(r["courseId"])

        passed = defaultdict(set)

        for r in took:
            if r["status"] == "passed":
                passed[r["studentId"]].add(r["courseId"])

        seen = set()

        for r in took:
            sid, cid = r["studentId"], r["courseId"]

            if sid not in S or cid not in C:
                err(f"took: dangling reference {r}")
                continue

            if (sid, cid) in seen:
                err(f"took: duplicate {sid} {cid}")

            seen.add((sid, cid))

            if cid not in in_prog.get(prog_of.get(sid), set()):
                err(f"took: course outside student's program {sid} {cid}")

            g, st = r.get("grade"), r["status"]

            if st == "enrolled" and g is not None:
                err(f"took: grade on enrolled {r}")

            if st in ("passed", "failed"):
                if g is None or not 1 <= g <= 6:
                    err(f"took: bad grade {r}")
                elif (st == "passed") != (g >= 4):
                    err(f"took: status/grade mismatch {r}")

            for req in graph.get(cid, ()):
                if req not in passed[sid]:
                    err(f"took: {sid} took {cid} without passing {req}")

    for c in courses:
        for k in ("semester", "ects", "language"):
            if c.get(k) in (None, ""):
                warnings.append(f"{c['id']}: {k} missing")

    print(
        f"programs={len(programs)} courses={len(courses)} "
        f"professors={len(profs)} students={len(students)} "
        f"took={len(took)} prerequisites={len(prereqs)} "
        f"prerequisite_details={len(prerequisite_details)}"
    )

    if warnings:
        print(f"{len(warnings)} warnings (first 8):")
        for w in warnings[:8]:
            print("  -", w)

    if errors:
        print(f"{len(errors)} ERRORS (first 20):")
        for e in errors[:20]:
            print("  -", e)
        return 1

    print("OK: no errors")
    return 0


if __name__ == "__main__":
    sys.exit(main())
s