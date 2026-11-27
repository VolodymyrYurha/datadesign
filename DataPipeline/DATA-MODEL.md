# Domain model

The data our system works with, independent of any app feature. Scrapers, the student generator, the DB
loaders and Spark all use this model. The per-DB documents map it onto each store:

- Neo4j (relationships): [`DataBase-infrastructure/neo4j/REQUIREMENTS.md`](../DataBase-infrastructure/neo4j/REQUIREMENTS.md)
- MongoDB (full records): [`DataBase-infrastructure/mongo/REQUIREMENTS.md`](../DataBase-infrastructure/mongo/REQUIREMENTS.md)
- Elasticsearch holds only the backend's query logs, no domain data:
  [`DataBase-infrastructure/elastic/REQUIREMENTS.md`](../DataBase-infrastructure/elastic/REQUIREMENTS.md)
- Where the data comes from: [`scraping/SCRAPING.md`](scraping/SCRAPING.md)

## Scope

- **USI, Faculty of Informatics only.** There is no university or faculty entity; both are implicit.
- **A few programs** of the faculty, e.g. Bachelor in Informatics, MSc in Artificial Intelligence,
  MSc in Software and Data Engineering, MSc in Informatics.
- **One academic year** of courses (the current one).
- Real data where USI publishes it (programs, courses, professors). Students and everything about them are generated.

## Entities

```
Program ──INCLUDES (compulsory / elective, year)──> Course ──REQUIRES──> Course
                                                      ^
Professor ──TEACHES (role)────────────────────────────┤
                                                      │
Student ──ENROLLED_IN──> Program                       │
Student ──TOOK (semester, grade, status)──────────────┘
```

Origin: **S** = scraped from search.usi.ch, **G** = generated.

### Program

| Field | Type | Origin | Notes |
|---|---|---|---|
| id | string | S | `program:<usi id>`, from `/en/educations/<id>/<slug>` |
| name | string | S | e.g. "Master of Science in Artificial Intelligence" |
| level | `bachelor` / `master` | S | |
| ects | int | S | 180 or 120 |
| durationSemesters | int | S | |
| language | string | S | e.g. `en` |
| description | string | S | |

### Course

| Field | Type | Origin | Notes |
|---|---|---|---|
| id | string | S | `course:<usi id>`, the number in `/en/courses/<id>/<slug>` |
| title | string | S | |
| semester | `fall` / `spring` | S | |
| ects | float | S | |
| language | string | S | |
| description | string | S | |
| objectives | string | S | |
| examInfo | string | S | How the course is assessed, as published |
| url | string | S | Source page |

> [!NOTE]
> USI gives a course a new id every academic year. Because we scrape one year, the USI id is fine as our
> course id. Prerequisites on USI point to past offerings (e.g. "SP 2023"), so they are matched to our
> courses by title, not by id. If we ever add more years, a course needs its own id separate from the yearly offering.

### Professor

Everyone who teaches: professors, lecturers and teaching assistants.

| Field | Type | Origin | Notes |
|---|---|---|---|
| id | string | S | `prof:<32-hex usi id>`, from `/en/people/<id>/<slug>` |
| firstName, lastName | string | S | |
| position | string | S | e.g. "Full Professor", "Assistant" |
| profileUrl | string | S | |

No emails, phone numbers or photos.

### Student (generated)

| Field | Type | Notes |
|---|---|---|
| id | string | `student:<n>` |
| firstName, lastName | string | Fake names |
| email | string | `<first>.<last>@student.example` |
| matriculationNumber | string | e.g. `25-901-234` |
| startYear | int | e.g. 2024 |

### Relationships

| Relationship | Fields | Origin | Notes |
|---|---|---|---|
| Program INCLUDES Course | `type` (`compulsory` / `elective`), `studyYear` (1, 2, 3) | S | From the "Education" table on each course page |
| Professor TEACHES Course | `role` (`director` / `assistant`) | S | From the "People" section on each course page |
| Course REQUIRES Course | `source` (`scraped` / `synthetic`) | S, G fallback | The course must be taken before. Scraped from the "Prerequisite" section of the course page (see SCRAPING.md). Only courses with no published prerequisites may get a few generated ones (year 1 before year 2) |
| Student ENROLLED_IN Program | `startYear`, `status` (`active` / `graduated`) | G | Exactly one program per student |
| Student TOOK Course | `semester` (e.g. `2025-fall`), `status` (`enrolled` / `passed` / `failed`), `grade` | G | Grade on the Swiss 1 to 6 scale, pass >= 4; only courses of the student's program |

## Rules

- Ids are strings with a type prefix and are the same in every DB (Mongo `_id`, Neo4j `id`).
- Every course is taught by at least one professor and belongs to at least one program.
- `REQUIRES` has no cycles.
- A student only takes a course after passing the courses it requires.
- `grade` is set only when the status is `passed` or `failed`.

## Volumes

| Entity | Sample (`data/sample`) | Full |
|---|---|---|
| Program | 2 | 4-6 |
| Course | ~20 | all Informatics courses of one year (~150-250) |
| Professor | ~15 | all who teach those courses |
| Student | 30 | ~500-1000 |
| TOOK | ~200 | ~10k-20k |

## Files

The pipeline writes JSON Lines, one object per line, one file per entity or relationship:

```
DataPipeline/data/
  raw/          cached HTML from the scraper
  canonical/    programs.jsonl  courses.jsonl  professors.jsonl  students.jsonl
                program_courses.jsonl  teaches.jsonl  prerequisites.jsonl
                enrollments.jsonl  took.jsonl
  sample/       same files, small subset, committed
```

Generated records carry `"synthetic": true`.

## Possible extensions

Not needed now. Add here first if a feature needs them: rooms and schedules, exams, publications and
research areas, several academic years.
