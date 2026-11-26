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
| REQUIRES | A few prerequisite edges inside each program, year 1 courses before year 2, no cycles |

The generator uses a fixed random seed, so the data is the same on every run.
