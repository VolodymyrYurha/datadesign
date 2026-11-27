"""
Generate the synthetic part of the data model (SCRAPING.md "Generated data"):
students, enrollments, took, prerequisites. Fixed seed -> identical output on every run.

  python -m scraping.generate_synthetic --students 600
"""
import argparse
import math
import random
import re
import unicodedata
from collections import defaultdict

from faker import Faker

from . import config
from .io_utils import read_jsonl, write_jsonl

CURRENT_ACADEMIC_START = config.ACADEMIC_YEAR_START


def ascii_slug(s):
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", s.lower())


def build_prerequisites(rng, pcs):
    year_of = defaultdict(dict)                       # programId -> {courseId: studyYear}
    for r in pcs:
        year_of[r["programId"]][r["courseId"]] = r["studyYear"]
    prereq = defaultdict(set)

    def creates_cycle(course, req):
        stack, seen = [req], set()
        while stack:
            x = stack.pop()
            if x == course:
                return True
            if x in seen:
                continue
            seen.add(x)
            stack.extend(prereq.get(x, ()))
        return False

    for pid in sorted(year_of):
        items = year_of[pid]
        for cid in sorted(items):
            y = items[cid]
            if y < 2:
                continue
            cands = [d for d in sorted(items) if items[d] == y - 1] or [d for d in sorted(items) if items[d] < y]
            if not cands:
                continue
            k = rng.choice([0, 1, 1, 2])
            rng.shuffle(cands)
            for d in cands:
                if k == 0:
                    break
                if d == cid or d in prereq[cid]:
                    continue
                if any(d in yo and cid in yo and yo[d] >= yo[cid] for yo in year_of.values()):
                    continue
                if creates_cycle(cid, d):
                    continue
                prereq[cid].add(d)
                k -= 1
    return {c: sorted(v) for c, v in prereq.items() if v}


def make_grade(rng, force_pass):
    if not force_pass and rng.random() < 0.10:
        return rng.choice([1.0 + 0.25 * i for i in range(12)]), "failed"        # 1.0 .. 3.75
    g = min(6.0, max(4.0, rng.gauss(5.0, 0.45)))
    return round(g * 4) / 4, "passed"


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--students", type=int, default=600)
    ap.add_argument("--seed", type=int, default=config.RANDOM_SEED)
    args = ap.parse_args(argv)

    rng = random.Random(args.seed)
    Faker.seed(args.seed)
    fake = Faker(["en_US", "it_IT", "de_DE"])

    src = config.CANONICAL
    programs = {p["id"]: p for p in read_jsonl(src / "programs.jsonl")}
    courses = {c["id"]: c for c in read_jsonl(src / "courses.jsonl")}
    pcs = read_jsonl(src / "program_courses.jsonl")
    if not programs or not courses:
        raise SystemExit("Run `python -m scraping.scrape` first (no programs/courses found).")

    prereq = build_prerequisites(rng, pcs)
    prereq_rows = [{"courseId": c, "requiresCourseId": r, "synthetic": True}
                   for c in sorted(prereq) for r in prereq[c]]

    by_prog = defaultdict(lambda: defaultdict(list))      # pid -> year -> [(cid, type, sem)]
    for r in sorted(pcs, key=lambda r: (r["programId"], r["studyYear"], r["courseId"])):
        by_prog[r["programId"]][r["studyYear"]].append(
            (r["courseId"], r["type"], courses[r["courseId"]].get("semester") or "fall"))
    usable = [pid for pid in sorted(programs) if by_prog.get(pid)]
    if not usable:
        raise SystemExit("No program has courses.")

    students, enrollments, took = [], [], []
    used_mail, used_matr = set(), set()
    for n in range(1, args.students + 1):
        pid = rng.choice(usable)
        prog = programs[pid]
        dur = prog.get("durationSemesters")
        years = max(1, math.ceil(dur / 2)) if dur else (3 if prog["level"] == "bachelor" else 2)
        start_year = rng.randint(CURRENT_ACADEMIC_START - years - 1, CURRENT_ACADEMIC_START)
        elapsed = CURRENT_ACADEMIC_START - start_year
        graduated = elapsed >= years
        last_year = years if graduated else elapsed + 1

        first, last = fake.first_name(), fake.last_name()
        base = f"{ascii_slug(first)}.{ascii_slug(last)}"
        email, i = f"{base}@student.example", 1
        while email in used_mail:
            i += 1
            email = f"{base}{i}@student.example"
        used_mail.add(email)
        while True:
            matr = f"{start_year % 100:02d}-9{rng.randint(0, 99):02d}-{rng.randint(0, 999):03d}"
            if matr not in used_matr:
                used_matr.add(matr)
                break
        sid = f"student:{n}"
        students.append({"id": sid, "firstName": first, "lastName": last, "email": email,
                         "matriculationNumber": matr, "startYear": start_year, "synthetic": True})
        enrollments.append({"studentId": sid, "programId": pid, "startYear": start_year,
                            "status": "graduated" if graduated else "active", "synthetic": True})

        passed = set()
        for y in range(1, last_year + 1):
            ay = start_year + y - 1
            in_progress = (not graduated) and y == elapsed + 1
            for cid, ctype, sem in by_prog[pid].get(y, []):
                if ctype == "elective" and rng.random() > 0.6:
                    continue
                if any(r not in passed for r in prereq.get(cid, [])):
                    continue
                label = f"{ay}-spring" if False else (f"{ay + 1}-spring" if sem == "spring" else f"{ay}-fall")
                if in_progress:
                    took.append({"studentId": sid, "courseId": cid, "semester": label,
                                 "status": "enrolled", "grade": None, "synthetic": True})
                    continue
                grade, status = make_grade(rng, force_pass=graduated)
                if status == "passed":
                    passed.add(cid)
                took.append({"studentId": sid, "courseId": cid, "semester": label,
                             "status": status, "grade": grade, "synthetic": True})

    out = src
    print("students       ", write_jsonl(out / "students.jsonl", students))
    print("enrollments    ", write_jsonl(out / "enrollments.jsonl", enrollments))
    print("took           ", write_jsonl(out / "took.jsonl", took))
    print("prerequisites  ", write_jsonl(out / "prerequisites.jsonl", prereq_rows))


if __name__ == "__main__":
    main()