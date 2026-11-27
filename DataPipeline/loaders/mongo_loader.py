"""MongoDB: one collection per entity, full records, relationships embedded as small summaries on
both sides (extended reference). Spec: DataBase-infrastructure/mongo/REQUIREMENTS.md.
Upserts by _id with replaceOne, so reloading is safe. The collections and their validators come from
DataBase-infrastructure/mongo/init/01-collections.js."""
from __future__ import annotations

from collections import defaultdict

from pymongo import MongoClient, ReplaceOne
from pymongo.errors import BulkWriteError

from .dataset import Dataset, full_name


def _doc(row: dict, drop=("id",)) -> dict:
    d = {"_id": row["id"]}
    d.update({k: v for k, v in row.items() if k not in drop and v is not None})
    return d


def build(ds: Dataset) -> dict[str, list[dict]]:
    programs = ds.by_id(ds.programs)
    courses = ds.by_id(ds.courses)
    profs = ds.by_id(ds.professors)

    prog_courses, course_progs = defaultdict(list), defaultdict(list)
    for pc in ds.program_courses:
        prog_courses[pc["programId"]].append({"courseId": pc["courseId"], "title": courses[pc["courseId"]]["title"],
                                              "type": pc["type"], "studyYear": pc["studyYear"]})
        course_progs[pc["courseId"]].append({"programId": pc["programId"], "name": programs[pc["programId"]]["name"],
                                             "type": pc["type"], "studyYear": pc["studyYear"]})

    course_profs, prof_courses = defaultdict(list), defaultdict(list)
    for t in ds.teaches:
        course_profs[t["courseId"]].append({"professorId": t["professorId"], "name": full_name(profs[t["professorId"]]),
                                            "role": t["role"]})
        prof_courses[t["professorId"]].append({"courseId": t["courseId"], "title": courses[t["courseId"]]["title"],
                                               "role": t["role"]})

    course_prereqs = defaultdict(list)
    for r in ds.requires:
        course_prereqs[r["courseId"]].append({"courseId": r["requiresCourseId"],
                                              "title": courses[r["requiresCourseId"]]["title"],
                                              "source": r["source"]})

    enrollment = {e["studentId"]: e for e in ds.enrollments}
    student_courses = defaultdict(list)
    for t in ds.took:
        c = courses[t["courseId"]]
        entry = {"courseId": t["courseId"], "title": c["title"], "ects": c.get("ects"),
                 "semester": t["semester"], "status": t["status"], "grade": t.get("grade")}
        student_courses[t["studentId"]].append({k: v for k, v in entry.items() if v is not None})

    out = {"programs": [], "courses": [], "professors": [], "students": []}
    for p in ds.programs:
        out["programs"].append({**_doc(p), "courses": prog_courses[p["id"]]})
    for c in ds.courses:
        out["courses"].append({**_doc(c), "professors": course_profs[c["id"]], "programs": course_progs[c["id"]],
                               "prerequisites": course_prereqs[c["id"]]})
    for p in ds.professors:
        out["professors"].append({**_doc(p), "courses": prof_courses[p["id"]]})
    for s in ds.students:
        doc = {**_doc(s), "courses": student_courses[s["id"]]}
        e = enrollment.get(s["id"])
        if e:
            doc["program"] = {"programId": e["programId"], "name": programs[e["programId"]]["name"],
                              "startYear": e["startYear"], "status": e["status"]}
        out["students"].append(doc)
    return out


def load(ds: Dataset, uri: str, database: str = "usi", fresh: bool = False) -> dict[str, int]:
    docs = build(ds)
    rejected = 0
    counts = {}
    with MongoClient(uri, serverSelectionTimeoutMS=5000) as client:
        db = client[database]
        missing = [name for name in docs if name not in db.list_collection_names()]
        if missing:
            raise RuntimeError(f"Collections {missing} do not exist. Run DataBase-infrastructure/mongo/init/"
                               "01-collections.js first (see loaders/README.md).")
        for name, rows in docs.items():
            if fresh:
                db[name].delete_many({})
            try:
                db[name].bulk_write([ReplaceOne({"_id": d["_id"]}, d, upsert=True) for d in rows], ordered=False)
            except BulkWriteError as e:
                errs = e.details.get("writeErrors", [])
                rejected += len(errs)
                for err in errs[:5]:
                    print(f"  {name}: rejected {err.get('op', {}).get('_id')}: {err.get('errmsg')}")
            counts[name] = db[name].count_documents({})
    counts["rejected"] = rejected
    return counts
