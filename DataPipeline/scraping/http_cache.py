"""Polite HTTP fetcher with an on-disk cache in data/raw/ (scrape once, parse many times)."""
import hashlib
import logging
import re
import time
from urllib import robotparser

import requests

from . import config

log = logging.getLogger("fetch")

_session = requests.Session()
_session.headers.update({"User-Agent": config.USER_AGENT, "Accept-Language": "en"})
_last_request = 0.0
_force = False
_fresh = set()
_robots = None


def set_force(value: bool):
    global _force
    _force = value


def _cache_path(url: str):
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    slug = re.sub(r"[^a-zA-Z0-9]+", "_", url.replace(config.BASE_URL, ""))[:80].strip("_")
    return config.RAW / f"{slug or 'root'}__{digest}.html"


def _allowed(url: str) -> bool:
    global _robots
    if _robots is None:
        rp = robotparser.RobotFileParser()
        try:
            r = _session.get(config.BASE_URL + "/robots.txt", timeout=config.TIMEOUT_SECONDS)
            if r.status_code == 200:
                rp.parse(r.text.splitlines())
                _robots = rp
            else:
                _robots = False
        except requests.RequestException:
            _robots = False
    if _robots is False:
        return True
    return _robots.can_fetch(config.USER_AGENT, url)


def _throttle():
    global _last_request
    wait = config.REQUEST_DELAY_SECONDS - (time.time() - _last_request)
    if wait > 0:
        time.sleep(wait)
    _last_request = time.time()


def fetch(url: str, force: bool = False):
    """Return the page HTML, or None if it cannot be fetched."""
    path = _cache_path(url)
    want_fresh = (force or _force) and url not in _fresh
    if path.exists() and not want_fresh:
        return path.read_text(encoding="utf-8")
    if not _allowed(url):
        log.warning("Blocked by robots.txt: %s", url)
        return None
    config.RAW.mkdir(parents=True, exist_ok=True)
    for attempt in range(1, config.MAX_RETRIES + 1):
        _throttle()
        try:
            r = _session.get(url, timeout=config.TIMEOUT_SECONDS)
        except requests.RequestException as e:
            log.warning("Request error (%s) attempt %d: %s", e.__class__.__name__, attempt, url)
            time.sleep(2 ** attempt)
            continue
        if r.status_code == 200:
            r.encoding = "utf-8"
            path.write_text(r.text, encoding="utf-8")
            _fresh.add(url)
            log.info("Downloaded %s", url)
            return r.text
        if r.status_code in (404, 410):
            log.info("Not found (%s): %s", r.status_code, url)
            return None
        if r.status_code == 429 or r.status_code >= 500:
            log.warning("HTTP %s attempt %d: %s", r.status_code, attempt, url)
            time.sleep(5 * attempt)
            continue
        log.warning("HTTP %s: %s", r.status_code, url)
        return None
    log.error("Giving up: %s", url)
    return None