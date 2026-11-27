"""HTML -> dict parsers for search.usi.ch. Pure functions, no network access."""
from logging import root
import re
import unicodedata
from urllib.parse import parse_qs, urljoin, urlparse

from bs4 import BeautifulSoup, NavigableString, Tag

from . import config

HOST = urlparse(config.BASE_URL).netloc

COURSE_HREF = re.compile(r"/(?:en/|it/)?courses/(\d+)")
EDU_HREF = re.compile(r"/(?:en/|it/)?educations/(\d+)")
PEOPLE_HREF = re.compile(r"/(?:en/|it/)?people/([0-9a-f]{32})")
LABEL_RE = re.compile(r"\b(SA|SP)\s*(\d{4})(?:\s*[-/]\s*(\d{4}))?")
SEM_HEADING_RE = re.compile(r"^(fall|autumn|spring)\s+semester", re.I)
EDU_YEAR_RE = re.compile(r"(\d)\s*(?:st|nd|rd|th)\s+year\s*$", re.I)
COMPULSORY_RE = re.compile(r"\b(compulsory|mandatory|core)\b", re.I)

H_DESC = {"description", "course description", "contents", "content", "course content"}
H_OBJ = {"objectives", "learning objectives", "aims", "course objectives", "goals"}
H_EXAM = {"examination information", "examination", "exam information", "assessment",
          "examination and assessment"}
H_PROG = {"description", "presentation", "overview", "about", "introduction",
          "programme description", "program description", "objectives"}
ROLE_LABELS = {
    "course director": "director", "director": "director",
    "assistant": "assistant", "assistants": "assistant",
    "teaching assistant": "assistant", "teaching assistants": "assistant",
    "lecturer": "assistant", "lecturers": "assistant", "instructor": "assistant",
}
OTHER_HEADINGS = {
    "people", "teaching mode", "learning methods", "additional information", "education",
    "educations", "literature", "bibliography", "prerequisite", "prerequisites",
    "schedule", "references", "programme", "programmes", "study programmes", "notes",
    "downloads", "materials", "further information", "language", "semester", "ects", "credits", "courses",
}
ALL_HEADINGS = H_DESC | H_OBJ | H_EXAM | OTHER_HEADINGS | set(ROLE_LABELS)


# ----------------------------------------------------------------- helpers
def clean(s):
    return re.sub(r"\s+", " ", (s or "")).strip()


def norm(s):
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", s).strip().lower()


def display_program_name(name):
    return clean(re.sub(r"\s*\([^)]*\)\s*$", "", name or ""))


def norm_program_name(name):
    return norm(display_program_name(name))


def heading_key(line):
    return clean(line).lower().rstrip(":").strip()


def make_soup(html):
    soup = BeautifulSoup(html, "html.parser")
    for t in soup(["script", "style", "noscript", "template", "svg"]):
        t.decompose()
    return soup


def main_root(soup):
    m = soup.find("main")
    if m is not None and m.find("h1") is not None:
        return m
    return soup.body or soup


def text_lines(root):
    return [l for l in (clean(x) for x in root.get_text("\n").split("\n")) if l]


def canon_url(url):
    """Normalise to https://search.usi.ch/en/... (keeps only ?page=N)."""
    p = urlparse(url)
    path = p.path or "/"
    if path.startswith("/it/"):
        path = "/en/" + path[4:]
    elif not path.startswith("/en/") and path != "/en":
        path = "/en" + path
    path = path.rstrip("/") or "/"
    qs = parse_qs(p.query)
    q = f"?page={qs['page'][0]}" if "page" in qs else ""
    return f"{config.BASE_URL}{path}{q}"


def iter_links(soup, base_url):
    for a in soup.find_all("a", href=True):
        href = a["href"].strip()
        if not href or href.startswith(("mailto:", "javascript:", "#", "tel:")):
            continue
        full = urljoin(base_url, href)
        p = urlparse(full)
        if p.netloc and p.netloc != HOST:
            continue
        yield a, full


def education_ref_from_url(full):
    path = urlparse(full).path
    m = EDU_HREF.search(path)
    if not m:
        return None
    eid = m.group(1)
    rest = [x for x in path[m.end():].split("/") if x]
    slug = rest[0] if rest else ""
    url = f"{config.BASE_URL}/en/educations/{eid}" + (f"/{slug}" if slug else "")
    return eid, url


def extract_education_refs(html, base_url):
    soup = make_soup(html)
    refs = {}
    for a, full in iter_links(soup, base_url):
        ref = education_ref_from_url(full)
        if not ref:
            continue
        eid, url = ref
        name = clean(a.get_text(" "))
        cur = refs.get(eid)
        if cur is None:
            refs[eid] = {"id": eid, "url": url, "name": name}
        elif len(name) > len(cur["name"]):
            cur["name"] = name
    return list(refs.values())


def lang_code(text):
    t = (text or "").lower()
    for word, code in (("english", "en"), ("italian", "it"), ("french", "fr"),
                       ("german", "de"), ("spanish", "es")):
        if word in t:
            return code
    return None


def _label_to_ay(kind, y1, y2):
    y1 = int(y1)
    if kind == "SA":
        return y1, "fall"
    return (y1 if y2 else y1 - 1), "spring"


def _context_text(a, levels=3):
    node = a
    best = clean(a.get_text(" "))
    for _ in range(levels):
        parent = node.parent
        if parent is None or parent.name in ("body", "html", "main", "[document]"):
            break
        if len(parent.find_all("a", href=COURSE_HREF)) > 1:
            break
        node = parent
        best = clean(node.get_text(" "))
    return best


# ----------------------------------------------------------------- listings
def parse_listing(html, base_url):
    """Course listing page (faculty or program). Returns courses + pagination links."""
    soup = make_soup(html)
    root = main_root(soup)
    courses = {}
    heading_sem = None
    for node in root.descendants:
        if isinstance(node, NavigableString):
            t = clean(str(node))
            if t and len(t) < 40:
                m = SEM_HEADING_RE.match(t)
                if m:
                    heading_sem = "spring" if m.group(1).lower() == "spring" else "fall"
        elif isinstance(node, Tag) and node.name == "a" and node.get("href"):
            full = urljoin(base_url, node["href"].strip())
            p = urlparse(full)
            if p.netloc and p.netloc != HOST:
                continue
            m = COURSE_HREF.search(p.path)
            if not m:
                continue
            cid = m.group(1)
            ctx = _context_text(node)
            sem, ay = heading_sem, None
            lm = LABEL_RE.search(ctx)
            if lm:
                ay, sem = _label_to_ay(lm.group(1), lm.group(2), lm.group(3))
            entry = {"id": cid, "url": canon_url(full), "title": clean(node.get_text(" ")),
                     "semester": sem, "academicYear": ay}
            old = courses.get(cid)
            if old is None:
                courses[cid] = entry
            else:
                for k in ("semester", "academicYear"):
                    if old[k] is None and entry[k] is not None:
                        old[k] = entry[k]
    here = urlparse(canon_url(base_url)).path
    pages = set()
    for a, full in iter_links(soup, base_url):
        p = urlparse(full)
        if "page=" in p.query and urlparse(canon_url(full)).path == here:
            pages.add(canon_url(full))
    return {"courses": list(courses.values()), "pages": sorted(pages)}


# ----------------------------------------------------------------- course page
def _section(lines, names):
    for i, l in enumerate(lines):
        if heading_key(l) in names:
            out = []
            for l2 in lines[i + 1:]:
                if heading_key(l2) in ALL_HEADINGS or EDU_YEAR_RE.search(l2):
                    break
                out.append(l2)
            text = "\n".join(out).strip()
            if text:
                return text
    return ""


def _role_near(a):
    node = a
    for _ in range(3):
        node = node.parent
        if node is None:
            break
        if len(node.find_all("a", href=PEOPLE_HREF)) > 1:
            break
        txt = clean(node.get_text(" "))
        if len(txt) > 200:
            break
        low = txt.lower()
        if "director" in low:
            return "director"
        if any(w in low for w in ("assistant", "lecturer", "teaching", "instructor", "tutor")):
            return "assistant"
    return None


def parse_people(root, page_url):
    people = {}
    last_label = None
    for node in root.descendants:
        if isinstance(node, NavigableString):
            if node.parent is not None and node.parent.name == "a":
                continue
            t = heading_key(str(node))
            if t in ROLE_LABELS:
                last_label = ROLE_LABELS[t]
        elif isinstance(node, Tag) and node.name == "a" and node.get("href"):
            full = urljoin(page_url, node["href"].strip())
            m = PEOPLE_HREF.search(urlparse(full).path)
            if not m or m.group(1) in people:
                continue
            pid = m.group(1)
            role = _role_near(node) or last_label or ("director" if not people else "assistant")
            people[pid] = {"rawId": pid, "url": canon_url(full),
                           "shortName": clean(node.get_text(" ")), "role": role}
    return list(people.values())


def parse_education_rows(root, page_url):
    """'<Program>, Lecture, [Area], Compulsory|Elective, 2nd year' rows of a course page."""
    raw = []
    for el in root.find_all(["li", "tr"]):
        if el.find(["li", "tr"]):
            continue
        if el.name == "tr":
            cells = [clean(c.get_text(" ")) for c in el.find_all(["td", "th"])]
            text = ", ".join(c for c in cells if c)
        else:
            text = re.sub(r"\s+,", ",", clean(el.get_text(" ")))
        m = EDU_YEAR_RE.search(text)
        if not m:
            continue
        pid, purl, name = None, "", ""
        anchor = el.find("a", href=EDU_HREF)
        if anchor is not None:
            ref = education_ref_from_url(urljoin(page_url, anchor["href"]))
            if ref:
                pid, purl = ref
            name = clean(anchor.get_text(" "))
        if not name:
            name = text.split(",")[0].strip()
        raw.append((name, pid, purl, text, int(m.group(1))))
    if not raw:  # fallback: plain text lines
        for line in text_lines(root):
            m = EDU_YEAR_RE.search(line)
            if m:
                raw.append((line.split(",")[0].strip(), None, "", line, int(m.group(1))))
    best = {}
    for name, pid, purl, text, year in raw:
        if not name:
            continue
        key = norm_program_name(name)
        ctype = "compulsory" if COMPULSORY_RE.search(text) else "elective"
        cand = {"programId": pid, "name": display_program_name(name), "type": ctype,
                "studyYear": year, "url": purl}
        cur = best.get(key)
        better = (cur is None or year < cur["studyYear"] or
                  (year == cur["studyYear"] and ctype == "compulsory" and cur["type"] != "compulsory"))
        if better:
            if cur is not None and not cand["programId"]:
                cand["programId"], cand["url"] = cur["programId"], cur["url"]
            best[key] = cand
    return list(best.values())


def _find_semester(full):
    m = (re.search(r"\b(fall|autumn|spring)\s+semester\b", full, re.I)
         or re.search(r"\bsemester\b\W{0,5}(fall|autumn|spring)\b", full, re.I))
    if not m:
        return None
    return "spring" if m.group(1).lower() == "spring" else "fall"




def _find_ects(root):
    for item in root.select(".section_item"):
        label = item.select_one(".rowinfo")
        if label and clean(label.get_text(" ")).lower() == "ects":
            value = item.find("strong")
            if value:
                match = re.search(r"\d+(?:[.,]\d+)?", value.get_text(" "))
                if match:
                    try:
                        ects = float(match.group().replace(",", "."))
                        return ects if 0 < ects <= 60 else None
                    except ValueError:
                        return None
    return None


def _find_language(full):
    m = re.search(r"\blanguages?\b\W{0,5}(english|italian|french|german|spanish)", full, re.I)
    return lang_code(m.group(1)) if m else None


def parse_prerequisites(root):
    prerequisites = []

    for heading in root.find_all(["h2", "h3"]):
        if heading_key(heading.get_text(" ")) not in {
            "prerequisite", "prerequisites"
        }:
            continue

        section = heading.find_next("div", class_="section_item")
        if section is None:
            continue

        for item in section.select("ul.results_links > li"):
            course_link = item.find(
                "a", href=re.compile(r"/courses/")
            )

            if course_link is None:
                continue

            course_name = clean(course_link.get_text(" "))
            professor_names = [
            clean(a.get_text(" "))
            for a in item.find_all(
                "a", href=re.compile(r"/people/")
            )
            if clean(a.get_text(" "))
        ]

        remaining = item.get_text(" ", strip=True)

        for link in item.find_all("a"):
            remaining = remaining.replace(
                link.get_text(" ", strip=True), "", 1
            )

        year_match = re.search(
            r"\b(?:SA|SP)\s*\d{4}(?:\s*[-/]\s*\d{4})?\b",
            remaining,
            re.I
        )

        if year_match:
            extra_text = remaining[:year_match.start()]
            extra_names = [
                clean(name)
                for name in extra_text.split(",")
                if clean(name)
            ]
            professor_names.extend(extra_names)

            # Remove linked course and professor names before parsing
            # the remaining text for the academic year.
            remaining = item.get_text(" ", strip=True)

            for link in item.find_all("a"):
                remaining = remaining.replace(
                    link.get_text(" ", strip=True), "", 1
                )

            year_match = re.search(
                r"\b(?:SA|SP)\s*\d{4}(?:\s*[-/]\s*\d{4})?\b",
                remaining,
                re.I
            )

            prerequisites.append({
                "courseName": course_name,
                "professorNames": professor_names,
                "academicYear": (
                    clean(year_match.group(0)) if year_match else None
                )
            })

    return prerequisites


def parse_course(html, url):
    soup = make_soup(html)
    root = main_root(soup)
    m = COURSE_HREF.search(urlparse(url).path)
    cid = m.group(1) if m else None
    h1 = root.find("h1") or soup.find("h1")
    if h1 is not None:
        title = clean(h1.get_text(" "))
    else:
        title = clean(soup.title.get_text(" ")) if soup.title else ""
    lines = text_lines(root)
    full = " ".join(lines)
    people = parse_people(root, url)
    programs = parse_education_rows(root, url)
    prerequisites = parse_prerequisites(root)

    description = _section(lines, H_DESC)
    if not description:  # lead paragraphs between the title and the first heading
        names = {norm(p["shortName"]) for p in people}
        start = lines.index(title) + 1 if title in lines else 0
        lead = []
        for l in lines[start:]:
            k = heading_key(l)
            if k in ROLE_LABELS or norm(l) in names:
                continue
            if k in ALL_HEADINGS or EDU_YEAR_RE.search(l):
                break
            lead.append(l)
        description = "\n".join(x for x in lead if len(x) > 40)

    return {
        "rawId": cid,
        "title": title,
        "semester": _find_semester(full),
        "ects": _find_ects(root),
        "language": _find_language(full),
        "description": description,
        "objectives": _section(lines, H_OBJ),
        "examInfo": _section(lines, H_EXAM),
        "url": canon_url(url),
        "people": people,
        "programs": programs,
        "prerequisites": prerequisites,
    }


# ----------------------------------------------------------------- program page
def parse_program(html, url):
    soup = make_soup(html)
    root = main_root(soup)
    ref = education_ref_from_url(url)
    pid = ref[0] if ref else None
    h1 = root.find("h1") or soup.find("h1")
    raw_name = clean(h1.get_text(" ")) if h1 is not None else (clean(soup.title.get_text(" ")) if soup.title else "")
    name = display_program_name(raw_name)
    n = norm(name)
    level = "bachelor" if n.startswith("bachelor") else "master"
    lines = text_lines(root)
    full = " ".join(lines)

    ects = None
    m = re.search(r"(\d{2,3})\s*ECTS\b", full, re.I)
    if m and 60 <= int(m.group(1)) <= 240:
        ects = int(m.group(1))
    if ects is None:
        ects = 180 if level == "bachelor" else 120          # documented default

    duration = None
    m = re.search(r"\b(\d{1,2})\s*semesters?\b", full, re.I)
    if m and 1 <= int(m.group(1)) <= 12:
        duration = int(m.group(1))
    else:
        m = re.search(r"\b(\d)\s*years?\b", full, re.I)
        if m and 1 <= int(m.group(1)) <= 6:
            duration = int(m.group(1)) * 2
    if duration is None:
        duration = max(2, ects // 30)

    lm = re.search(r"\blanguages?\b\W{0,5}(english|italian|french|german|spanish)", full, re.I)
    language = lang_code(lm.group(1)) if lm else "en"        # Faculty programmes are taught in English

    description = _section(lines, H_PROG)
    if not description:
        long_lines = [l for l in lines if len(l) > 100]
        description = long_lines[0] if long_lines else ""

    return {"rawId": pid, "name": name, "level": level, "ects": ects,
            "durationSemesters": duration, "language": language, "description": description}


# ----------------------------------------------------------------- person page
_POSITION_RE = re.compile(
    r"^(full professor|associate professor|assistant professor|professor|"
    r"senior lecturer|lecturer|researcher|postdoc|post-doctoral researcher|"
    r"teaching assistant|adjunct professor|visiting professor|"
    r"research fellow|research associate|phd student|doctoral student)$",
    re.I
)


def split_name(full, short=None, slug=None):
    """Return (lastName, firstName). USI lists teachers as 'Surname F.' on course pages."""
    full = clean(full)
    if not full:
        return "", ""
    if short:
        m = re.match(r"^(.*?)\s+((?:[^\W\d_]\.\s?)+)$", short.strip())
        if m:
            sur = m.group(1)
            fl, sl = full.lower(), sur.lower()
            if fl.startswith(sl + " "):
                return full[:len(sur)], full[len(sur):].strip()
            if fl.endswith(" " + sl):
                return full[-len(sur):], full[:-len(sur)].strip()
    parts = full.replace(",", " ").split()
    if len(parts) == 1:
        return parts[0], ""
    if slug:
        toks = slug.split("-")
        if toks and norm(parts[0]) == toks[0]:
            return parts[0], " ".join(parts[1:])
        if toks and norm(parts[-1]) == toks[0]:
            return parts[-1], " ".join(parts[:-1])
    return parts[-1], " ".join(parts[:-1])


def parse_person(html, url, short_name=None):
    soup = make_soup(html)
    root = main_root(soup)
    path = urlparse(url).path
    m = PEOPLE_HREF.search(path)
    pid = m.group(1) if m else None
    slug = None
    if m:
        rest = [x for x in path[m.end():].split("/") if x]
        slug = rest[0] if rest else None
    h1 = root.find("h1") or soup.find("h1")
    full_name = clean(h1.get_text(" ")) if h1 is not None else ""
    last, first = split_name(full_name, short_name, slug)
    lines = text_lines(root)
    start = lines.index(full_name) + 1 if full_name in lines else 0
    position = ""
    for l in lines[start:start + 25]:
        candidate = clean(l).strip(" ,.;:")
        if _POSITION_RE.fullmatch(candidate):
            position = candidate
            break
    return {"rawId": pid, "firstName": first, "lastName": last,
            "position": position, "profileUrl": canon_url(url)}