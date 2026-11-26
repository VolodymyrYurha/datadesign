
"""
Scrape the Faculty of Informatics from search.usi.ch into data/canonical/*.jsonl

  python -m scraping.scrape                 # normal run (cached pages are reused)
  python -m scraping.scrape --limit 10      # quick test with 10 courses
  python -m scraping.scrape --force         # re-download everything
"""
import argparse
import json
import logging
import re
import sys
from collections import Counter

from . import config, http_cache
from . import parsers as P
from .http_cache import fetch
from .io_utils import write_jsonl

log = logging.getLogger("scrape")


def non_en(url):
    return url.replace("/en/", "/", 1)


def crawl_listing(start_url, max_pages=40):
    seen, queue, entries = set(), [start_url], {}
    while queue and len(seen) < max_pages:
        url = queue.pop(0)
        if url in seen:
            continue
        seen.add(url)
        html = fetch(url)
        if html is None:
            continue
        res = P.parse_listing(html, url)
        for e in res["courses"]:
            old = entries.get(e["id"])
            if old is None:
                entries[e["id"]] = e
            else:
                for k in ("semester", "academicYear"):
                    if old.get(k) is None and e.get(k) is not None:
                        old[k] = e[k]
        for nxt in res["pages"]:
            if nxt not in seen:
                queue.append(nxt)
    return entries


def crawl_listing_any(url):
    u = P.canon_url(url)
    for candidate in (u, non_en(u)):
        entries = crawl_listing(candidate)
        if entries:
            return entries
    return {}


def read_seed_file():
    if not config.SEED_FILE.exists():
        return []
    lines = config.SEED_FILE.read_text(encoding="utf-8").splitlines()
    return [l.strip() for l in lines if l.strip() and not l.strip().startswith("#")]


def build_matcher(patterns):
    regs = [re.compile(p, re.I) for p in patterns]
    return lambda name: any(r.search(P.norm_program_name(name)) for r in regs), regs


def main(argv=None):
    ap = argparse.ArgumentParser(description="Scrape USI Informatics data")
    ap.add_argument("--force", action="store_true", help="re-download pages even if cached")
    ap.add_argument("--limit", type=int, default=0, help="only load N candidate courses (testing)")
    ap.add_argument("--academic-year", type=int, default=config.ACADEMIC_YEAR_START,
                    help="start year of the academic year, e.g. 2026 for 2026-2027")
    ap.add_argument("--programs", nargs="*", help="override program regexes")
    ap.add_argument("--course-ids", help="comma separated extra course ids")
    args = ap.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s",
                        datefmt="%H:%M:%S")
    http_cache.set_force(args.force)
    ay = args.academic_year
    match, regs = build_matcher(args.programs or config.PROGRAM_PATTERNS)

    candidates = {}   # cid -> {"url": ...}
    hints = {}        # cid -> semester ("fall"/"spring") from listings
    edu_refs = {}     # education id -> {"id","url","name"}
    parsed = {}       # cid -> parsed course (or None)

    def add_ref(r):
        cur = edu_refs.get(r["id"])
        if cur is None:
            edu_refs[r["id"]] = dict(r)
        else:
            if not cur.get("name") and r.get("name"):
                cur["name"] = r["name"]
            if not cur.get("url") and r.get("url"):
                cur["url"] = r["url"]

    def add_candidate(cid, url, semester=None):
        candidates.setdefault(cid, {"url": url})
        if semester and cid not in hints:
            hints[cid] = semester

    def load_course(cid):
        if cid in parsed:
            return parsed[cid]
        url = candidates[cid]["url"]
        html = fetch(url)
        if html is None:
            parsed[cid] = None
            return None
        try:
            c = P.parse_course(html, url)
        except Exception:
            log.exception("Could not parse course %s", url)
            parsed[cid] = None
            return None
        parsed[cid] = c
        for pr in c["programs"]:
            if pr.get("programId"):
                add_ref({"id": pr["programId"], "url": pr.get("url") or "", "name": pr["name"]})
        return c

    # 1. seeds, faculty page, faculty course listing -------------------------
    for item in config.SEED_EDUCATION_URLS + read_seed_file():
        full = item if item.startswith("http") else config.BASE_URL + item
        cm = P.COURSE_HREF.search(full)
        ref = P.education_ref_from_url(full)
        if cm and "/courses/" in full:
            add_candidate(cm.group(1), P.canon_url(full))
        elif ref:
            add_ref({"id": ref[0], "url": ref[1], "name": ""})
    for cid in (args.course_ids or "").split(","):
        if cid.strip().isdigit():
            add_candidate(cid.strip(), f"{config.BASE_URL}/en/courses/{cid.strip()}")

    html = fetch(config.FACULTY_URL)
    if html:
        for r in P.extract_education_refs(html, config.FACULTY_URL):
            add_ref(r)
    else:
        log.warning("Faculty page not reachable: %s", config.FACULTY_URL)

    entries = crawl_listing_any(config.FACULTY_COURSES_URL)
    kept = dropped = 0
    for cid, e in entries.items():
        if e["academicYear"] is None or e["academicYear"] == ay:
            add_candidate(cid, e["url"], e["semester"])
            kept += 1
        else:
            dropped += 1
    log.info("Faculty listing: %d courses for %d-%d kept, %d from other years skipped",
             kept, ay, ay + 1, dropped)

    # 2. load course pages (also discovers programs) ------------------------
    ids = sorted(candidates)
    if args.limit:
        ids = ids[:args.limit]
    for i, cid in enumerate(ids, 1):
        if i % 25 == 0:
            log.info("courses loaded: %d/%d", i, len(ids))
        load_course(cid)

    # 3. programs in scope ---------------------------------------------------
    programs = {}     # raw id -> parsed program
    all_names = []
    for eid, ref in sorted(edu_refs.items()):
        name = ref.get("name", "")
        if name:
            all_names.append(P.display_program_name(name))
        if name and not match(name):
            continue
        url = ref.get("url") or f"{config.BASE_URL}/en/educations/{eid}"
        html = fetch(url)
        if html is None:
            continue
        try:
            prog = P.parse_program(html, url)
        except Exception:
            log.exception("Could not parse program %s", url)
            continue
        prog["rawId"] = eid
        if match(prog["name"]):
            prog["_url"] = url
            programs[eid] = prog
    if not programs:
        log.error("No program matched PROGRAM_PATTERNS. Programs seen:\n  %s",
                  "\n  ".join(sorted(set(all_names))) or "(none)")
        log.error("Fix PROGRAM_PATTERNS in scraping/config.py or add URLs to data/seeds.txt, "
                  "then run: python -m scraping.recon <url>")
        return 2
    for rx in regs:
        if not any(rx.search(P.norm_program_name(p["name"])) for p in programs.values()):
            log.warning("Pattern matched no program: %s", rx.pattern)
    log.info("Programs in scope: %s", ", ".join(f"{p['name']} ({k})" for k, p in programs.items()))

    # 4. program course listings (current academic year) ---------------------
    for eid, prog in programs.items():
        listing = crawl_listing_any(prog["_url"].rstrip("/") + "/courses")
        log.info("Program %s: %d courses listed", prog["name"], len(listing))
        for cid, e in listing.items():
            add_candidate(cid, e["url"], e["semester"])
            if e["semester"] and cid not in hints:
                hints[cid] = e["semester"]
    ids = sorted(candidates)
    if args.limit:
        ids = ids[:args.limit]
    for i, cid in enumerate(ids, 1):
        if cid not in parsed and i % 25 == 0:
            log.info("courses loaded: %d/%d", i, len(ids))
        load_course(cid)

    # 5. build records --------------------------------------------------------
    by_name = {P.norm_program_name(p["name"]): eid for eid, p in programs.items()}
    courses_out, pc_out, teaches_out = [], [], []
    prerequisite_details_out = []
    prof_refs = {}
    seen_teach = set()

    for cid in sorted(parsed, key=int):
        c = parsed[cid]
        if not c or not c["title"]:
            continue

        links = {}
        for pr in c["programs"]:
            eid = (
                pr["programId"]
                if pr.get("programId") in programs
                else by_name.get(P.norm_program_name(pr["name"]))
            )
            if eid and (
                eid not in links
                or pr["studyYear"] < links[eid]["studyYear"]
            ):
                links[eid] = pr
        if not links:
            log.info(
                "Keeping course outside selected programmes: %s - %s",
                cid, c["title"]
            )

        course_id = f"course:{cid}"

        for req in c.get("prerequisites", []):
            prerequisite_details_out.append({
                "courseId": course_id,
                "prerequisiteName": req["courseName"],
                "professorNames": req["professorNames"],
                "academicYear": req["academicYear"]
            })

        courses_out.append({
            "id": course_id, "title": c["title"],
            "semester": hints.get(cid) or c["semester"],
            "ects": c["ects"], "language": c["language"],
            "description": c["description"], "objectives": c["objectives"],
            "examInfo": c["examInfo"], "url": c["url"],
        })

        for eid, pr in sorted(links.items()):
            pc_out.append({
                "programId": f"program:{eid}",
                "courseId": course_id,
                "type": pr["type"],
                "studyYear": pr["studyYear"]
            })

        for p in c["people"]:
            key = (p["rawId"], cid)
            if key in seen_teach:
                continue
            seen_teach.add(key)
            prof_refs.setdefault(p["rawId"], p)
            teaches_out.append({
                "professorId": f"prof:{p['rawId']}",
                "courseId": course_id,
                "role": p["role"]
            })

    # 6. professors -----------------------------------------------------------
    profs_out = []
    for i, (pid, p) in enumerate(sorted(prof_refs.items()), 1):
        if i % 25 == 0:
            log.info("people loaded: %d/%d", i, len(prof_refs))
        rec = None
        html = fetch(p["url"])
        if html:
            try:
                rec = P.parse_person(html, p["url"], p["shortName"])
            except Exception:
                log.exception("Could not parse person %s", p["url"])
        if rec is None or not (rec["lastName"] or rec["firstName"]):
            last, first = P.split_name(p["shortName"], p["shortName"])
            rec = {"firstName": first, "lastName": last, "position": "", "profileUrl": p["url"]}
        profs_out.append({"id": f"prof:{pid}", "firstName": rec["firstName"], "lastName": rec["lastName"],
                          "position": rec["position"], "profileUrl": rec["profileUrl"]})

    programs_out = [{"id": f"program:{eid}", "name": p["name"], "level": p["level"], "ects": p["ects"],
                     "durationSemesters": p["durationSemesters"], "language": p["language"],
                     "description": p["description"]} for eid, p in sorted(programs.items(), key=lambda kv: int(kv[0]))]

    # 7. write ----------------------------------------------------------------
    out = config.CANONICAL
    counts = {
        "programs": write_jsonl(out / "programs.jsonl", programs_out),
        "courses": write_jsonl(out / "courses.jsonl", courses_out),
        "professors": write_jsonl(out / "professors.jsonl", sorted(profs_out, key=lambda r: r["id"])),
        "program_courses": write_jsonl(out / "program_courses.jsonl", pc_out),
        "teaches": write_jsonl(out / "teaches.jsonl", teaches_out),
        "prerequisite_details": write_jsonl(
            out / "prerequisite_details.jsonl", prerequisite_details_out
        ),
    }
    missing = Counter()
    for c in courses_out:
        for k in ("semester", "ects", "language", "description", "objectives", "examInfo"):
            if c[k] in (None, ""):
                missing[k] += 1
    no_prof = sum(1 for c in courses_out if not any(t["courseId"] == c["id"] for t in teaches_out))
    report = {"academicYearStart": ay, "counts": counts,
              "coursesWithMissingField": dict(missing), "coursesWithoutProfessor": no_prof}
    (out / "_scrape_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    log.info("DONE %s", json.dumps(counts))
    if missing:
        log.warning("Courses with missing fields: %s", dict(missing))
        log.warning("Inspect with: python -m scraping.recon <course url>")
    if not courses_out:
        log.error("0 courses extracted. Run recon on a course page and a listing page.")
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
