Removed section **2. Technologies Used** and renumbered the remaining sections.

# DataPipeline – USI Data Scraping

## 1. Overview

This component of the Data Design project collects, processes, and validates academic data from the official Università della Svizzera italiana (USI) website.

The pipeline extracts information about Informatics-related degree programmes, courses, professors, and their relationships. It also generates synthetic student data to support the database and analytics components of the project.

The scraped data is stored in JSON Lines (`.jsonl`) format.

## 2. Project Structure

```
DataPipeline/
│
├── scraping/
│   ├── config.py
│   ├── io_utils.py
│   ├── http_cache.py
│   ├── parsers.py
│   ├── scrape.py
│   ├── generate_synthetic.py
│   ├── validate.py
│   ├── recon.py
│   └── SCRAPING.md
│
├── data/
│   ├── cache/
│   ├── canonical/
│   └── sample/
│
├── DATA-MODEL.md
└── README.md
```

## 3. Data Sources and Scope

The data was collected from the official USI website:

[https://search.usi.ch/](https://search.usi.ch/)

The scraping focuses on the Faculty of Informatics and the academic year 2026–2027.

Five degree programmes were selected:

1. Bachelor of Science in Informatics
2. Master of Science in Informatics
3. Master of Science in Artificial Intelligence
4. Master of Science in Data Science
5. Master of Science in Software Engineering with AI

The scraper follows programme, course, and professor links to collect the relevant information.

**Important:** The final dataset contains all 135 discovered Faculty course records, including courses that are not associated with the five selected programmes. These courses are retained as course records without artificially assigning them to one of the selected programmes.

## 4. Scraped Data

The pipeline extracts the following information where available:

### Programmes

- Programme ID
- Programme name
- Degree information
- Source URL

### Courses

- Course ID
- Course title
- Semester
- ECTS credits
- Language
- Description
- Learning objectives
- Examination information
- Source URL

### Professors

- Professor ID
- Name
- Available profile information
- Profile URL

### Relationships

- Programme-course relationships
- Teaching relationships between professors and courses
- Scraped prerequisite information

The scraper also extracts course roles and study-year information for programme-course relationships.

## 5. Synthetic Student Data

Student data is generated using Faker because real student-level records are not publicly available.

The synthetic generator creates:

- Students
- Programme enrollments
- Course-taking records
- Grades
- Course statuses
- Synthetic prerequisite relationships

Grades follow the Swiss grading scale from 1 to 6. Passing grades are 4 or higher.

Synthetic student records are generated separately from the scraped academic data.

## 6. Final Dataset

The final validated dataset contains:

| Dataset                        | Records |
| ------------------------------ | ------- |
| Programmes                     | 5       |
| Courses                        | 135     |
| Professors                     | 173     |
| Programme-course relationships | 151     |
| Teaching relationships         | 369     |
| Students                       | 600     |
| Enrollments                    | 600     |
| Course-taking records          | 9,021   |
| Synthetic prerequisites        | 27      |
| Scraped prerequisite details   | 23      |

The canonical JSONL files are stored in `data/canonical/`.

Main files include:

- `programs.jsonl`
- `courses.jsonl`
- `professors.jsonl`
- `program_courses.jsonl`
- `teaches.jsonl`
- `students.jsonl`
- `enrollments.jsonl`
- `took.jsonl`
- `prerequisites.jsonl`
- `prerequisite_details.jsonl`

## 7. How to Run the Pipeline

Run all commands from the `DataPipeline` directory.

### Step 1: Activate the virtual environment

```
source .venv/bin/activate
```

### Step 2: Run the scraper

```
python -m scraping.scrape
```

This collects the academic data and writes the canonical JSONL files.

### Step 3: Generate synthetic student data

```
python -m scraping.generate_synthetic --students 600
```

This generates 600 students and their associated academic records.

### Step 4: Validate the dataset

```
python -m scraping.validate
```

The validator checks references, duplicate IDs, relationship integrity, prerequisite cycles, student enrollments, grades, and missing fields.

The command exits with an error status if validation errors are found.

## 8. Validation Results

The final validation command was:

```
python -m scraping.validate
```

Final output:

```
programs=5 courses=135 professors=173 students=600
took=9021 prerequisites=27 prerequisite_details=23

OK: no errors
```

There were no remaining warnings or validation errors.

## 9. Backup

A backup of the validated canonical dataset was created using:

```
cp -R data/canonical data/canonical_backup
```

The backup is stored in:

```
data/canonical_backup/
```

It preserves the validated dataset independently of future scraping runs.

## 10. Current Status

The DataPipeline component is complete for the current project scope.

It provides:

- A working USI academic data scraper
- 135 scraped course records
- 173 professor records
- Five selected degree programmes
- Programme and teaching relationships
- Scraped prerequisite details
- 600 synthetic students
- Synthetic academic history and prerequisite data
- A validated canonical dataset
- A backup of the final dataset

The resulting JSONL files are ready to be used by the subsequent database and analytics components of the Data Design project.

Remove section 2 from my README
