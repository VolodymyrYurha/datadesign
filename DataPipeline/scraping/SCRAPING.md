# Scraping requirements

What to collect and from where. The target format is [`../DATA-MODEL.md`](../DATA-MODEL.md).

## Source

**search.usi.ch**, USI's public course and people directory, limited to the **Faculty of Informatics**
(`/en/faculties/3/faculty-of-informatics`) and the current academic year.

| Page | URL pattern | Gives us |
|---|---|---|
| Program | `/en/educations/<id>/<slug>` | Program |
| Course | `/en/courses/<id>/<slug>` | Course, TEACHES, INCLUDES |
| Person | `/en/people/<id>/<surname>-<firstname>` | Professor |

Example course page: [User Experience Design](https://search.usi.ch/en/courses/35265626/user-experience-design).

## What to extract

**Course page**

| Page element | Field |
|---|---|
| Number in the URL | `Course.id` |
| Title | `title` |
| Description, Objectives, Examination information | `description`, `objectives`, `examInfo` |
| Additional information: Semester, ECTS, Language | `semester`, `ects`, `language` |
| People: name, role, profile link | TEACHES (`Course director` -> `director`, `Assistant` -> `assistant`) |
| Education table: program link, Compulsory/Elective, year | INCLUDES, only for the programs in scope |
| Prerequisite list | REQUIRES (`source: scraped`), see below |

**Prerequisite section.** Each entry is a past offering of the course that must be taken before:
`<title>, <professors>, <semester>`, e.g.
`Algorithms & Data Structures, Carzaniga A., Di Lauro F., Mangipudi S., SP 2023`
(`SP` = spring, `SA` = fall/autumn semester). Only the title matters for us:

1. Take the title: the text before the first professor name. If the entry links to a course page, use
   that page's title instead, which is more reliable than splitting on commas (titles can contain commas).
2. Normalize it (lowercase, `&` -> `and`, strip punctuation and extra spaces) and match it against the
   titles of the courses we scraped for the current year.
3. Matched: create `REQUIRES` from this course to the matched course.
4. Not matched (the course is no longer offered, or belongs to another faculty): log it to
   `data/raw/_unmatched_prerequisites.jsonl` and skip it. Review that file by hand once; add a manual
   title alias if a course was only renamed.

**Program page:** name, level, ECTS, duration, language, description.

**Person page:** first and last name, position. Nothing else.

## Steps

1. Scrape the chosen program pages.
2. List the faculty's courses for the current year and scrape each course page.
3. Scrape the person page of everyone linked from those courses.
4. Normalize into `data/canonical/*.jsonl`.

Rules: 1 request per second, respect `robots.txt`, cache every page in `data/raw/` and parse from the
cache, so we scrape once.

## Generated data

| Data | Rule |
|---|---|
| Students | Fake names (Faker), fake matriculation numbers, `@student.example` emails |
| ENROLLED_IN | Spread students over the programs |
| TOOK | Each student takes the courses of their program by study year; grades 1 to 6 in 0.25 steps, mostly 4.5 to 5.5, about 10% failed |
| REQUIRES (fallback only) | Only if a program ends up with too few scraped prerequisites: add a few edges between courses with no published prerequisites, year 1 before year 2, no cycles, `source: synthetic` |

The generator uses a fixed random seed, so the data is the same on every run.
