"""
Debug tool: show what the scraper sees on ONE page.

  python -m scraping.recon https://search.usi.ch/en/courses/35265626/user-experience-design
  python -m scraping.recon https://search.usi.ch/en/educations/30/bachelor-of-science-in-informatics/courses
  python -m scraping.recon https://search.usi.ch/en/educations/30/bachelor-of-science-in-informatics
"""
import json
import logging
import sys
from urllib.parse import urlparse

from . import parsers as P
from .http_cache import fetch


def main():
    logging.basicConfig(level=logging.INFO)
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    url = P.canon_url(sys.argv[1])
    html = fetch(url)
    if html is None:
        alt = url.replace("/en/", "/", 1)
        print("Not reachable, trying", alt)
        html = fetch(alt)
        url = alt
    if html is None:
        print("Could not download the page.")
        return 2
    soup = P.make_soup(html)
    root = P.main_root(soup)
    h1 = root.find("h1")
    print("URL:", url)
    print("h1:", P.clean(h1.get_text(" ")) if h1 else None)
    links = list(P.iter_links(soup, url))
    print("links -> courses:", sum(1 for _, u in links if P.COURSE_HREF.search(urlparse(u).path)),
          "| educations:", sum(1 for _, u in links if P.EDU_HREF.search(urlparse(u).path)),
          "| people:", sum(1 for _, u in links if P.PEOPLE_HREF.search(urlparse(u).path)))
    print("\n--- first 70 text lines ---")
    for l in P.text_lines(root)[:70]:
        print("  ", l[:150])
    path = urlparse(url).path
    print("\n--- parsed ---")
    if P.PEOPLE_HREF.search(path):
        print(json.dumps(P.parse_person(html, url), indent=2, ensure_ascii=False))
    elif P.COURSE_HREF.search(path):
        print(json.dumps(P.parse_course(html, url), indent=2, ensure_ascii=False)[:6000])
    elif P.EDU_HREF.search(path) and path.rstrip("/").endswith("/courses"):
        res = P.parse_listing(html, url)
        print("courses:", len(res["courses"]), "| next pages:", res["pages"])
        print(json.dumps(res["courses"][:8], indent=2, ensure_ascii=False))
    elif P.EDU_HREF.search(path):
        print(json.dumps(P.parse_program(html, url), indent=2, ensure_ascii=False))
    else:
        res = P.parse_listing(html, url)
        print("courses:", len(res["courses"]), "| next pages:", res["pages"])
        print(json.dumps(res["courses"][:8], indent=2, ensure_ascii=False))
        print("education links:", json.dumps(P.extract_education_refs(html, url)[:10], indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())