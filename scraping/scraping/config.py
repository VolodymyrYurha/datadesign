"""Central settings. Edit USER_AGENT and ACADEMIC_YEAR_START if needed."""
from pathlib import Path

BASE_URL = "https://search.usi.ch"
FACULTY_URL = f"{BASE_URL}/en/faculties/3/faculty-of-informatics"
FACULTY_COURSES_URL = f"{FACULTY_URL}/courses"

# Known entry points (more programs are discovered automatically from course pages).
SEED_EDUCATION_URLS = [
    f"{BASE_URL}/en/educations/30/bachelor-of-science-in-informatics",
    f"{BASE_URL}/en/educations/106/master-of-science-in-software-engineering-with-ai",
]

# Academic year 2026-2027 -> 2026. Listings show "SA 2026-2027" / "SP 2027".
ACADEMIC_YEAR_START = 2026

# Programs in scope (regexes, matched on the lower-cased name WITHOUT the "(CODE)" suffix).

PROGRAM_PATTERNS = [
    r"^bachelor of science in informatics$",
    r"^master of science in informatics$",
    r"^master of science in artificial intelligence$",
    r"^master of science in data science$",
    r"^master of science in software engineering with ai$",
]


ROOT = Path(__file__).resolve().parents[1]      # DataPipeline/
DATA = ROOT / "data"
RAW = DATA / "raw"
CANONICAL = DATA / "canonical"
SAMPLE = DATA / "sample"
SEED_FILE = DATA / "seeds.txt"

USER_AGENT = "USI-DataDesign-StudentProject/1.0 (university course project; contact: your.name@usi.ch)"
REQUEST_DELAY_SECONDS = 1.0     # SCRAPING.md: 1 request per second
TIMEOUT_SECONDS = 30
MAX_RETRIES = 3

RANDOM_SEED = 42                # fixed seed -> same synthetic data every run