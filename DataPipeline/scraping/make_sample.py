"""
Build the committed sample (data/sample): 2 programs, ~20 courses, their professors, ~30 students.

  python -m scraping.make_sample
"""
import argparse
from collections import defaultdict

from . import config
from .io_utils import read_jsonl, write_jsonl

FILES = ["programs", "courses", "professors", "students", "program_courses",
         "teaches", "prerequisites", "enrollments", "took"]


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--programs", type=int, default=2)
    ap.add_argument("--courses", type=int, default=20)
    ap.add_argument("--students", type=int, default=30)
    args = ap.parse_args(argv)

    d = {f: read_jsonl(config.CANONICAL / f"{f}.jsonl") for f in FILES}
    if not d["courses"]:
        raise SystemExit("Nothing to sample. Run scrape + generate_synthetic first.")

    per_prog = defaultdict(list)
    for r in d["program_courses"]:
        per_prog[r["programId"]].append(r["courseId"])
    top = sorted(per_prog, key=lambda p: (-len(per_prog[p]), p))[:args.programs]

    chosen, lists = [], [sorted(set(per_prog[p])) for p in top]
    i = 0
    while len(chosen) < args.courses and any(lists):
        lst = lists[i % len(lists)]
        while lst and lst[0] in chosen:
            lst.pop(0)
        if lst:
            chosen.append(lst.pop(0))
        i += 1
        if i > 10000:
            break
    cset, pset = set(chosen), set(top)

    pc = [r for r in d["program_courses"] if r["programId"] in pset and r["courseId"] in cset]
    teaches = [r for r in d["teaches"] if r["courseId"] in cset]
    fset = {r["professorId"] for r in teaches}
    enr = [r for r in d["enrollments"] if r["programId"] in pset]
    sset = {r["studentId"] for r in sorted(enr, key=lambda r: r["studentId"])[:args.students]}
    enr = [r for r in enr if r["studentId"] in sset]

    out = config.SAMPLE
    n = {
        "programs": write_jsonl(out / "programs.jsonl", [r for r in d["programs"] if r["id"] in pset]),
        "courses": write_jsonl(out / "courses.jsonl", [r for r in d["courses"] if r["id"] in cset]),
        "professors": write_jsonl(out / "professors.jsonl", [r for r in d["professors"] if r["id"] in fset]),
        "students": write_jsonl(out / "students.jsonl", [r for r in d["students"] if r["id"] in sset]),
        "program_courses": write_jsonl(out / "program_courses.jsonl", pc),
        "teaches": write_jsonl(out / "teaches.jsonl", teaches),
        "prerequisites": write_jsonl(out / "prerequisites.jsonl",
                                     [r for r in d["prerequisites"]
                                      if r["courseId"] in cset and r["requiresCourseId"] in cset]),
        "enrollments": write_jsonl(out / "enrollments.jsonl", enr),
        "took": write_jsonl(out / "took.jsonl",
                            [r for r in d["took"] if r["studentId"] in sset and r["courseId"] in cset]),
    }
    print(n)
    print("Now run: python -m scraping.validate --dir data/sample")


if __name__ == "__main__":
    main()