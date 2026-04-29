from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any
from urllib.parse import urljoin
import re

import requests
from bs4 import BeautifulSoup
from urllib3.exceptions import InsecureRequestWarning

import urllib3

from backend.services.pdf_parser import parse_pdf_bytes


DEFAULT_PORTAL_URL = "https://esprit-tn.com/esponline/Online/default.aspx"
DEFAULT_TIMETABLE_URL = "https://esprit-tn.com/esponline/Etudiants/Emplois.aspx"
PORTAL_BASE = "https://esprit-tn.com/esponline/"

TIME_TOKEN_PATTERN = r"\d{1,2}\s*(?::|[hH])\s*:?\s*\d{2}"
TIME_TOKEN_RE = re.compile(TIME_TOKEN_PATTERN)
TIME_RANGE_RE = re.compile(
    rf"(?P<start>{TIME_TOKEN_PATTERN})\s*[-\u2013\u2014]\s*(?P<end>{TIME_TOKEN_PATTERN})"
)
ROOM_RE = re.compile(r"\b(?:[A-Z]{1,4}\d{2,4}|Atelier[A-Z0-9]*|LABO[A-Z0-9]*)\b", re.IGNORECASE)
DATE_RE = re.compile(r"\b(?P<d>\d{1,2})[/-](?P<m>\d{1,2})(?:[/-](?P<y>\d{2,4}))?\b")
URL_RE = re.compile(r"https?://[^\s'\"<>]+", re.IGNORECASE)
DOPOSTBACK_RE = re.compile(r"__doPostBack\(['\"]([^'\"]+)['\"],\s*['\"]([^'\"]*)['\"]", re.IGNORECASE)
ALERT_RE = re.compile(r"alert\(['\"](?P<message>.+?)['\"]\)", re.IGNORECASE | re.DOTALL)
ONLINE_RE = re.compile(r"\ben\s*ligne\b|online", re.IGNORECASE)
GROUP_RE = re.compile(r"\b\d+[A-Z]{1,4}\d*\b", re.IGNORECASE)
PAGE_RE = re.compile(r"\bpage\s+(?P<page>\d+)\b", re.IGNORECASE)

STUDENT_ID_FIELD = "ctl00$ContentPlaceHolder1$TextBox3"
STUDENT_PASSWORD_FIELD = "ctl00$ContentPlaceHolder1$TextBox7"
STUDENT_ROBOT_FIELD = "ctl00$ContentPlaceHolder1$chkImage"
STUDENT_NEXT_BUTTON_FIELD = "ctl00$ContentPlaceHolder1$Button3"
STUDENT_LOGIN_BUTTON_FIELD = "ctl00$ContentPlaceHolder1$ButtonEtudiant"

_BROWSER_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "fr-FR,fr;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}

DAY_NAMES = {
    "lundi": "Lundi",
    "mardi": "Mardi",
    "mercredi": "Mercredi",
    "jeudi": "Jeudi",
    "vendredi": "Vendredi",
    "samedi": "Samedi",
    "dimanche": "Dimanche",
}


@dataclass
class ScrapeResult:
    bookings: list[dict[str, Any]]
    rooms: list[str]
    source_url: str
    warnings: list[str]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _is_blocked_page(text: str) -> bool:
    lowered = text.lower()
    return "the url you requested has been blocked" in lowered or (
        "client ip" in lowered and "erreur" in lowered
    )


def _is_login_page(soup: BeautifulSoup) -> bool:
    """Return True when the response still looks like an Esprit login form."""
    return bool(
        soup.select_one("input[type='password']")
        or _input_exists(soup, STUDENT_ID_FIELD)
        or "espace etudiant" in soup.get_text(" ", strip=True).lower()
    )


def _has_login_error(soup: BeautifulSoup) -> bool:
    keywords = (
        "mot de passe incorrect",
        "identifiant incorrect",
        "cin incorrect",
        "invalid",
        "erreur de connexion",
        "login failed",
    )

    # ASP.NET pages keep hidden validator text in the DOM, so alerts are the
    # most reliable signal for a real failed submission.
    for script in soup.select("script"):
        script_text = script.get_text(" ", strip=True)
        alert_match = ALERT_RE.search(script_text)
        if alert_match and any(kw in alert_match.group("message").lower() for kw in keywords):
            return True

    visible_text: list[str] = []
    for node in soup.find_all(["span", "label", "div", "p"]):
        node_id = (node.get("id") or "").lower()
        style = (node.get("style") or "").replace(" ", "").lower()
        if "requiredfieldvalidator" in node_id:
            continue
        if "display:none" in style or "visibility:hidden" in style:
            continue
        text = node.get_text(" ", strip=True)
        if text:
            visible_text.append(text.lower())

    joined = " ".join(visible_text)
    return any(kw in joined for kw in keywords)


def _field_name_matches(name: str, keywords: tuple[str, ...]) -> bool:
    lowered = name.lower()
    return any(k in lowered for k in keywords)


def _input_exists(soup: BeautifulSoup, name_or_id: str) -> bool:
    return _input_name(soup, name_or_id) is not None


def _input_name(soup: BeautifulSoup, name_or_id: str) -> str | None:
    suffix = name_or_id.split("$")[-1]
    tag = soup.find("input", attrs={"name": name_or_id})
    if not tag:
        for candidate in soup.find_all("input"):
            candidate_id = (candidate.get("id") or "").strip()
            candidate_name = (candidate.get("name") or "").strip()
            if candidate_id == suffix or candidate_id.endswith("_" + suffix) or candidate_name.endswith("$" + suffix):
                tag = candidate
                break
    if tag is None:
        return None
    name = (tag.get("name") or "").strip()
    return name or None


def _normalize_spaces(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\xa0", " ")).strip()


def _normalize_time(value: str) -> str:
    match = TIME_TOKEN_RE.search(value)
    if not match:
        return _normalize_spaces(value)
    normalized = re.sub(r"\s+", "", match.group(0)).replace("H", "h")
    normalized = normalized.replace("h:", "h").replace("h", ":")
    hour, minute = normalized.split(":")
    return f"{int(hour):02d}:{int(minute):02d}"


def _extract_time_tokens(value: str) -> list[str]:
    return [_normalize_time(match.group(0)) for match in TIME_TOKEN_RE.finditer(value)]


def _times_are_valid(start: str, end: str) -> bool:
    try:
        start_hour, start_minute = [int(part) for part in start.split(":")]
        end_hour, end_minute = [int(part) for part in end.split(":")]
    except (ValueError, IndexError):
        return False
    return start_hour * 60 + start_minute < end_hour * 60 + end_minute


def _try_parse_date(value: str) -> str | None:
    match = DATE_RE.search(value)
    if not match:
        return None
    day = int(match.group("d"))
    month = int(match.group("m"))
    year_raw = match.group("y")
    if not year_raw:
        year = datetime.now().year
    else:
        year = int(year_raw)
        if year < 100:
            year += 2000
    if day < 1 or month < 1 or month > 12:
        return None
    try:
        return datetime(year, month, day).date().isoformat()
    except ValueError:
        return None


def _normalize_date(value: str) -> str:
    return _try_parse_date(value) or datetime.now().date().isoformat()


def _infer_day_name(text: str, iso_date: str) -> str:
    lowered = text.lower()
    for key, label in DAY_NAMES.items():
        if key in lowered:
            return label
    weekday = datetime.strptime(iso_date, "%Y-%m-%d").weekday()
    labels = ["Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"]
    return labels[weekday]


def _extract_page_number(text: str, default: int = 1) -> int:
    match = PAGE_RE.search(text)
    if not match:
        return default
    return max(1, int(match.group("page")))


def _is_ignorable_session_text(text: str) -> bool:
    normalized = _normalize_spaces(text)
    if not normalized:
        return True
    if ONLINE_RE.search(normalized):
        return True
    compact = re.sub(r"[^A-Za-z]", "", normalized).lower()
    return compact in {"pause", "pauses"}


def _extract_hidden_inputs(soup: BeautifulSoup) -> dict[str, str]:
    hidden: dict[str, str] = {}
    for tag in soup.select("input[type='hidden'][name]"):
        name = (tag.get("name") or "").strip()
        if name:
            hidden[name] = (tag.get("value") or "").strip()
    return hidden


def _choose_login_form(soup: BeautifulSoup):
    forms = soup.find_all("form")
    if not forms:
        return None
    scored: list[tuple[int, Any]] = []
    for form in forms:
        score = 0
        names = [(tag.get("name") or "").strip().lower() for tag in form.select("input[name]")]
        types = [(tag.get("type") or "text").strip().lower() for tag in form.select("input[name]")]
        if "password" in types:
            score += 3
        if "text" in types or "email" in types:
            score += 1
        if any(_field_name_matches(n, ("ident", "login", "user", "matric", "code", "etud")) for n in names):
            score += 2
        if any(_field_name_matches(n, ("pass", "motdepasse", "pwd")) for n in names):
            score += 2
        scored.append((score, form))
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[0][1]


def _resolve_form_action(soup: BeautifulSoup, page_url: str) -> str:
    form = _choose_login_form(soup)
    action = (form.get("action") if form else "") or page_url
    return urljoin(page_url, action)


def _base_form_payload(soup: BeautifulSoup) -> dict[str, str]:
    payload = _extract_hidden_inputs(soup)
    form = _choose_login_form(soup) or soup

    for tag in form.select("input[name]"):
        input_type = (tag.get("type") or "text").strip().lower()
        name = (tag.get("name") or "").strip()
        if not name or input_type in {"hidden", "submit", "button", "image"}:
            continue
        if input_type in {"radio", "checkbox"}:
            if tag.has_attr("checked"):
                payload[name] = (tag.get("value") or "on").strip() or "on"
            continue
        payload.setdefault(name, (tag.get("value") or "").strip())

    for select in form.select("select[name]"):
        name = (select.get("name") or "").strip()
        if not name:
            continue
        selected = select.select_one("option[selected]") or select.select_one("option")
        payload[name] = (selected.get("value") if selected else "") or ""

    for textarea in form.select("textarea[name]"):
        name = (textarea.get("name") or "").strip()
        if name:
            payload.setdefault(name, textarea.get_text(strip=True))

    return payload


def _button_value(soup: BeautifulSoup, name_or_id: str, default: str) -> str:
    name = _input_name(soup, name_or_id)
    if not name:
        return default
    button = soup.find("input", attrs={"name": name})
    return ((button.get("value") if button else "") or default).strip()


def _build_student_robot_payload(soup: BeautifulSoup, *, student_id: str) -> dict[str, str]:
    payload = _base_form_payload(soup)
    id_field = _input_name(soup, STUDENT_ID_FIELD) or STUDENT_ID_FIELD
    robot_field = _input_name(soup, STUDENT_ROBOT_FIELD) or STUDENT_ROBOT_FIELD

    payload[id_field] = student_id
    payload[robot_field] = "on"
    payload["__EVENTTARGET"] = robot_field
    payload["__EVENTARGUMENT"] = ""
    return payload


def _build_student_next_payload(
    soup: BeautifulSoup,
    *,
    student_id: str,
    password: str | None = None,
) -> dict[str, str]:
    payload = _base_form_payload(soup)
    id_field = _input_name(soup, STUDENT_ID_FIELD)
    robot_field = _input_name(soup, STUDENT_ROBOT_FIELD)
    password_field = _input_name(soup, STUDENT_PASSWORD_FIELD)
    login_button_field = _input_name(soup, STUDENT_LOGIN_BUTTON_FIELD)
    next_button_field = _input_name(soup, STUDENT_NEXT_BUTTON_FIELD)

    if id_field:
        payload[id_field] = student_id
    if robot_field:
        payload[robot_field] = "on"
    if password and password_field:
        payload[password_field] = password
    button_field = login_button_field if password and password_field and login_button_field else next_button_field
    if not button_field:
        button_field = STUDENT_LOGIN_BUTTON_FIELD if password and password_field else STUDENT_NEXT_BUTTON_FIELD
    payload["__EVENTTARGET"] = ""
    payload["__EVENTARGUMENT"] = ""
    payload[button_field] = _button_value(soup, button_field, "Suivant")
    return payload


def _build_login_payload(
    soup: BeautifulSoup,
    *,
    student_id: str,
    password: str | None,
    captcha: str | None,
    extra_fields: dict[str, str] | None,
) -> dict[str, str]:
    if _input_exists(soup, STUDENT_ID_FIELD):
        payload = _build_student_next_payload(soup, student_id=student_id, password=password)
        if extra_fields:
            payload.update({str(k): str(v) for k, v in extra_fields.items()})
        return payload

    payload = _extract_hidden_inputs(soup)

    form = _choose_login_form(soup)
    if form is None:
        form = soup

    # Seed all visible inputs with their default values
    for tag in form.select("input[name]"):
        input_type = (tag.get("type") or "text").lower()
        name = (tag.get("name") or "").strip()
        if not name:
            continue
        if input_type in {"radio", "checkbox"}:
            if tag.has_attr("checked"):
                payload[name] = (tag.get("value") or "on").strip() or "on"
            continue
        if input_type in {"hidden", "text", "email", "password", "submit", "button", "image"}:
            payload.setdefault(name, (tag.get("value") or "").strip())

    for select in form.select("select[name]"):
        name = (select.get("name") or "").strip()
        if not name:
            continue
        selected = select.select_one("option[selected]") or select.select_one("option")
        payload[name] = (selected.get("value") if selected else "") or ""

    for textarea in form.select("textarea[name]"):
        name = (textarea.get("name") or "").strip()
        if name:
            payload.setdefault(name, textarea.get_text(strip=True))

    # --- Fill credentials ---
    text_filled = False
    submit_candidates: list[tuple[str, str]] = []

    for tag in form.select("input[name]"):
        input_type = (tag.get("type") or "text").lower()
        name = (tag.get("name") or "").strip()
        if not name:
            continue
        lowered = name.lower()

        if input_type in {"submit", "button", "image"}:
            submit_candidates.append((name, (tag.get("value") or "").strip()))
            continue

        if input_type == "password" or any(k in lowered for k in ["pass", "motdepasse", "pwd"]):
            if password:
                payload[name] = password
            continue

        if any(k in lowered for k in ["captcha", "robot", "code"]):
            if captcha:
                payload[name] = captcha
            continue

        if input_type in {"text", "email"} and not text_filled:
            payload[name] = student_id
            text_filled = True

    if not text_filled:
        payload["identifiant"] = student_id

    # --- Pick the submit button ---
    if submit_candidates:
        preferred = next(
            (
                (f, v) for f, v in submit_candidates
                if _field_name_matches(f, ("connect", "submit", "login", "btn"))
                or "connect" in v.lower()
            ),
            submit_candidates[0],
        )
        payload[preferred[0]] = preferred[1] or "Se Connecter"

    # --- Handle ASP.NET LinkButton (__doPostBack) ---
    # If no real submit button found, scan for __doPostBack anchors
    if not submit_candidates:
        page_html = str(form)
        for m in DOPOSTBACK_RE.finditer(page_html):
            event_target = m.group(1).replace("\\", "")
            event_arg = m.group(2).replace("\\", "")
            anchor_text = ""
            # Try to find the anchor containing this call
            for a in form.select("a[href]"):
                href = a.get("href", "")
                if event_target in href or "doPostBack" in href:
                    anchor_text = a.get_text(strip=True).lower()
                    break
            if any(k in anchor_text or k in event_target.lower()
                   for k in ("connect", "login", "valider", "ok", "entr")):
                payload["__EVENTTARGET"] = event_target
                payload["__EVENTARGUMENT"] = event_arg
                # Remove any submit button name so ASP.NET uses __EVENTTARGET
                for f, _ in submit_candidates:
                    payload.pop(f, None)
                break

    if extra_fields:
        payload.update({str(k): str(v) for k, v in extra_fields.items()})

    return payload


# ---------------------------------------------------------------------------
# Timetable-page helpers
# ---------------------------------------------------------------------------

def _extract_pdf_links(soup: BeautifulSoup, page_url: str) -> list[str]:
    links: list[str] = []

    def _add(candidate: str) -> None:
        if not candidate:
            return
        lowered_candidate = candidate.strip().lower()
        if lowered_candidate.startswith(("javascript:", "mailto:", "#")):
            return
        absolute = urljoin(page_url, candidate)
        lowered = absolute.lower()
        if any(t in lowered for t in (".pdf", "emploi", "bulletin", "download", "report", "ashx")) \
                and absolute not in links:
            links.append(absolute)

    for anchor in soup.select("a[href]"):
        _add((anchor.get("href") or "").strip())
    for node in soup.select("iframe[src], frame[src], embed[src], object[data]"):
        _add((node.get("src") or node.get("data") or "").strip())
    for script in soup.select("script"):
        for m in URL_RE.findall(script.get_text(" ", strip=True)):
            _add(m)
    for m in URL_RE.findall(soup.get_text(" ", strip=True)):
        _add(m)

    return links


def _extract_pdf_postbacks(soup: BeautifulSoup) -> list[tuple[str, str]]:
    postbacks: list[tuple[str, str]] = []

    for anchor in soup.select("a[href]"):
        href = (anchor.get("href") or "").strip()
        match = DOPOSTBACK_RE.search(href)
        if not match:
            continue

        target = match.group(1).replace("\\", "")
        argument = match.group(2).replace("\\", "")
        text = _normalize_spaces(anchor.get_text(" ", strip=True)).lower()
        row_text = _normalize_spaces(anchor.find_parent("tr").get_text(" ", strip=True)).lower() if anchor.find_parent("tr") else ""
        target_lower = target.lower()
        is_preview = any(token in target_lower or token in text for token in ("browse", "preview", "apercu", "aperçu"))

        if (
            "download" in target_lower
            or "lnkdownload" in target_lower
            or "télécharger" in text
            or "telecharger" in text
            or "download" in text
            or (".pdf" in row_text and not is_preview)
        ):
            candidate = (target, argument)
            if candidate not in postbacks:
                postbacks.append(candidate)

    return postbacks


def _apply_fallback_group(bookings: list[dict[str, Any]], group_name: str) -> None:
    for booking in bookings:
        parsed_group = _normalize_spaces(str(booking.get("group_name") or ""))
        if not parsed_group or parsed_group.lower() in {"unknown group", "esprit"}:
            booking["group_name"] = group_name or "ESPRIT"


def _find_emplois_link(soup: BeautifulSoup, page_url: str) -> str | None:
    """After login, the landing page may contain a nav link to Emplois.aspx."""
    for anchor in soup.select("a[href]"):
        href = (anchor.get("href") or "").strip()
        if "emploi" in href.lower() or "emplois" in href.lower():
            return urljoin(page_url, href)
    return None


def _diagnose_empty_html_response(soup: BeautifulSoup | None, page_url: str) -> str:
    if soup is None:
        return "No HTML page was available for timetable parsing."

    title = _normalize_spaces(soup.title.get_text(" ", strip=True)) if soup.title else ""
    text = soup.get_text(" ", strip=True).lower()
    if _is_login_page(soup):
        return (
            "Esprit returned the login page after the scrape attempt. "
            "The robot checkbox was submitted automatically, but the portal did not issue an authenticated timetable session."
        )
    if "emploi" in text or "seance" in text or "séance" in text:
        return (
            "Esprit returned a timetable-like HTML page, but no dated physical-room booking rows were detected. "
            f"Last page URL: {page_url}"
        )
    if title:
        return f"Esprit returned an unexpected HTML page titled '{title}'. Last page URL: {page_url}"
    return f"Esprit returned an unexpected HTML page. Last page URL: {page_url}"


def _is_pdf_response(response: requests.Response) -> bool:
    ct = (response.headers.get("Content-Type") or "").lower()
    if "application/pdf" in ct:
        return True
    return response.content[:4] == b"%PDF"


def _extract_room_candidates(text: str) -> list[str]:
    matches = ROOM_RE.findall(text.replace("/", " "))
    unique: list[str] = []
    for room in matches:
        normalized = _normalize_spaces(room).upper()
        if not normalized or ONLINE_RE.search(normalized):
            continue
        if normalized not in unique:
            unique.append(normalized)
    return unique


def _clean_course_name(text: str) -> str:
    cleaned = TIME_RANGE_RE.sub(" ", text)
    cleaned = TIME_TOKEN_RE.sub(" ", cleaned)
    cleaned = DATE_RE.sub(" ", cleaned)
    cleaned = ROOM_RE.sub(" ", cleaned)
    cleaned = re.sub(r"\b(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\b", " ", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\b(?:salle|classe|groupe|enseignant|professeur|page)\b\s*:?", " ", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"[/|;]+", " ", cleaned)
    return _normalize_spaces(cleaned).strip("-: ")


def _extract_group_name_from_html(soup: BeautifulSoup, fallback: str) -> str:
    lines = [
        _normalize_spaces(line)
        for line in soup.get_text("\n", strip=True).splitlines()
        if _normalize_spaces(line)
    ]

    for index, line in enumerate(lines):
        match = re.search(r"emploi\s+du\s+temps\s*:?\s*(?P<group>[A-Z0-9]+)?", line, re.IGNORECASE)
        if not match:
            continue
        group = match.group("group")
        if group and GROUP_RE.search(group):
            return group.upper()
        for following in lines[index + 1:index + 4]:
            group_match = GROUP_RE.search(following)
            if group_match:
                return group_match.group(0).upper()

    return fallback


def _table_cells(row: Any) -> list[dict[str, Any]]:
    cells: list[dict[str, Any]] = []
    for cell in row.find_all(["th", "td"], recursive=False):
        try:
            colspan = int(cell.get("colspan", 1) or 1)
        except ValueError:
            colspan = 1
        cells.append({
            "text": _normalize_spaces(cell.get_text(" ", strip=True)),
            "colspan": max(1, colspan),
        })
    return cells


def _time_slots_from_header(cells: list[dict[str, Any]]) -> list[dict[str, Any]]:
    time_columns: list[tuple[int, str]] = []
    column = 0
    for cell in cells:
        tokens = _extract_time_tokens(cell["text"])
        for token in tokens:
            time_columns.append((column, token))
        column += int(cell["colspan"])

    slots: list[dict[str, Any]] = []
    for index in range(0, len(time_columns) - 1, 2):
        start_col, start = time_columns[index]
        end_col, end = time_columns[index + 1]
        if _times_are_valid(start, end):
            slots.append({
                "start": start,
                "end": end,
                "start_col": start_col,
                "end_col": end_col,
            })
    return slots


def _slot_for_cell(
    *,
    start_col: int,
    end_col: int,
    content_index: int,
    slots: list[dict[str, Any]],
) -> dict[str, Any] | None:
    for slot in slots:
        if start_col <= slot["end_col"] and end_col >= slot["start_col"]:
            return slot
    if content_index < len(slots):
        return slots[content_index]
    return None


def _header_index(headers: list[str], keywords: tuple[str, ...]) -> int | None:
    for index, header in enumerate(headers):
        if any(keyword in header for keyword in keywords):
            return index
    return None


def _extract_bookings_from_html(soup: BeautifulSoup, *, group_name: str) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    seen: set[tuple[str, str, str, str, str, str]] = set()
    effective_group = _extract_group_name_from_html(soup, group_name)

    def add_booking(
        *,
        raw_text: str,
        date_iso: str,
        start_time: str,
        end_time: str,
        day_hint: str | None = None,
        course_hint: str | None = None,
        room_hint: str | None = None,
        source_page: int = 1,
    ) -> None:
        if not _times_are_valid(start_time, end_time):
            return
        if _is_ignorable_session_text(raw_text):
            return

        room_matches = _extract_room_candidates(room_hint or raw_text)
        if not room_matches:
            return

        course_name = _clean_course_name(course_hint or raw_text) or "Unknown course"
        day_name = day_hint or _infer_day_name(raw_text, date_iso)
        source_page = _extract_page_number(raw_text, default=source_page)

        for room_name in room_matches:
            key = (effective_group, course_name, room_name, date_iso, start_time, end_time)
            if key in seen:
                continue
            seen.add(key)
            candidates.append({
                "group_name": effective_group,
                "course_name": course_name,
                "room_name": room_name,
                "date": date_iso,
                "day_name": day_name,
                "start_time": start_time,
                "end_time": end_time,
                "source_page": source_page,
                "raw_text": raw_text,
            })

    # Strategy 1: conventional grid/table rows with named columns.
    for table in soup.select("table"):
        rows = table.select("tr")
        if not rows:
            continue

        header_cells: list[str] = []
        header_row_index: int | None = None
        for index, row in enumerate(rows[:3]):
            cell_texts = [_normalize_spaces(cell.get_text(" ", strip=True)).lower() for cell in row.find_all(["th", "td"])]
            header_score = sum(
                any(keyword in text for keyword in ("date", "jour", "heure", "debut", "début", "fin", "salle", "local", "matiere", "matière", "module", "cours"))
                for text in cell_texts
            )
            if header_score >= 2:
                header_cells = cell_texts
                header_row_index = index
                break

        if header_row_index is not None:
            date_idx = _header_index(header_cells, ("date", "jour"))
            start_idx = _header_index(header_cells, ("debut", "début", "start"))
            end_idx = _header_index(header_cells, ("fin", "end"))
            time_idx = _header_index(header_cells, ("heure", "horaire", "seance", "séance"))
            room_idx = _header_index(header_cells, ("salle", "local", "room"))
            course_idx = _header_index(header_cells, ("matiere", "matière", "module", "cours", "libelle", "libellé"))

            for row in rows[header_row_index + 1:]:
                cells = [_normalize_spaces(cell.get_text(" ", strip=True)) for cell in row.find_all(["td", "th"])]
                if not cells:
                    continue
                row_text = _normalize_spaces(" ".join(cells))
                if _is_ignorable_session_text(row_text):
                    continue

                date_iso = _try_parse_date(cells[date_idx] if date_idx is not None and date_idx < len(cells) else row_text)
                if not date_iso:
                    continue

                start_time = end_time = None
                if start_idx is not None and end_idx is not None and start_idx < len(cells) and end_idx < len(cells):
                    start_tokens = _extract_time_tokens(cells[start_idx])
                    end_tokens = _extract_time_tokens(cells[end_idx])
                    if start_tokens and end_tokens:
                        start_time, end_time = start_tokens[0], end_tokens[0]
                if (not start_time or not end_time) and time_idx is not None and time_idx < len(cells):
                    time_match = TIME_RANGE_RE.search(cells[time_idx])
                    if time_match:
                        start_time = _normalize_time(time_match.group("start"))
                        end_time = _normalize_time(time_match.group("end"))
                if not start_time or not end_time:
                    tokens = _extract_time_tokens(row_text)
                    if len(tokens) >= 2:
                        start_time, end_time = tokens[0], tokens[1]
                if not start_time or not end_time:
                    continue

                add_booking(
                    raw_text=row_text,
                    date_iso=date_iso,
                    day_hint=_infer_day_name(row_text, date_iso),
                    start_time=start_time,
                    end_time=end_time,
                    room_hint=cells[room_idx] if room_idx is not None and room_idx < len(cells) else row_text,
                    course_hint=cells[course_idx] if course_idx is not None and course_idx < len(cells) else row_text,
                )

    # Strategy 2: timetable matrix where time slots live in a header row and
    # booking cells only contain course/room text.
    for table in soup.select("table"):
        active_slots: list[dict[str, Any]] = []
        current_date: str | None = None
        current_day: str | None = None

        for row in table.select("tr"):
            cells = _table_cells(row)
            if not cells:
                continue
            row_text = _normalize_spaces(" ".join(cell["text"] for cell in cells))
            header_slots = _time_slots_from_header(cells)
            if header_slots and not _extract_room_candidates(row_text):
                active_slots = header_slots
                continue

            parsed_date = _try_parse_date(row_text)
            if parsed_date:
                current_date = parsed_date
                current_day = _infer_day_name(row_text, parsed_date)

            if not active_slots or not current_date:
                continue

            column = 0
            content_index = 0
            for cell in cells:
                text = cell["text"]
                start_col = column
                end_col = column + int(cell["colspan"]) - 1
                column += int(cell["colspan"])

                if _is_ignorable_session_text(text):
                    continue
                if _try_parse_date(text) and not _extract_room_candidates(text):
                    continue
                if _extract_time_tokens(text) and not _extract_room_candidates(text):
                    continue

                slot = _slot_for_cell(
                    start_col=start_col,
                    end_col=end_col,
                    content_index=content_index,
                    slots=active_slots,
                )
                if not slot:
                    continue

                time_match = TIME_RANGE_RE.search(text)
                start_time = _normalize_time(time_match.group("start")) if time_match else slot["start"]
                end_time = _normalize_time(time_match.group("end")) if time_match else slot["end"]
                add_booking(
                    raw_text=row_text,
                    date_iso=current_date,
                    day_hint=current_day,
                    start_time=start_time,
                    end_time=end_time,
                    course_hint=text,
                    room_hint=text,
                )
                content_index += 1

    # Strategy 3: loose text fragments where every booking carries its own
    # date/time/room text, with day/date context remembered between lines.
    texts = (
        [_normalize_spaces(tr.get_text(" ", strip=True)) for tr in soup.select("tr")]
        + [_normalize_spaces(n.get_text(" ", strip=True)) for n in soup.select("div, li, p, td")]
        + [_normalize_spaces(line) for line in soup.get_text("\n", strip=True).splitlines()]
    )

    current_date: str | None = None
    current_day: str | None = None
    for text in texts:
        if not text:
            continue
        parsed_date = _try_parse_date(text)
        if parsed_date:
            current_date = parsed_date
            current_day = _infer_day_name(text, parsed_date)

        time_match = TIME_RANGE_RE.search(text)
        if time_match:
            start_time = _normalize_time(time_match.group("start"))
            end_time = _normalize_time(time_match.group("end"))
        else:
            tokens = _extract_time_tokens(text)
            if len(tokens) < 2:
                continue
            start_time, end_time = tokens[0], tokens[1]

        date_iso = parsed_date or current_date or datetime.now().date().isoformat()
        add_booking(
            raw_text=text,
            date_iso=date_iso,
            day_hint=current_day if current_date == date_iso else None,
            start_time=start_time,
            end_time=end_time,
        )

    candidates.sort(key=lambda x: (x["date"], x["start_time"], x["room_name"]))
    return candidates


def _try_pdf_links(
    session: requests.Session,
    soup: BeautifulSoup,
    page_url: str,
    group_name: str,
    timeout: float,
    warnings: list[str],
) -> list[dict[str, Any]]:
    for link in _extract_pdf_links(soup, page_url):
        try:
            r = session.get(link, timeout=timeout)
            r.raise_for_status()
            if not _is_pdf_response(r):
                continue
            extraction = parse_pdf_bytes(r.content)
            if extraction.bookings:
                _apply_fallback_group(extraction.bookings, group_name)
                return extraction.bookings
        except requests.RequestException as exc:
            warnings.append(f"Unable to fetch PDF {link}: {exc}")

    for event_target, event_argument in _extract_pdf_postbacks(soup):
        try:
            payload = _extract_hidden_inputs(soup)
            payload["__EVENTTARGET"] = event_target
            payload["__EVENTARGUMENT"] = event_argument

            r = session.post(
                _resolve_form_action(soup, page_url),
                data=payload,
                timeout=timeout,
                headers={"Referer": page_url},
                allow_redirects=True,
            )
            r.raise_for_status()
            if not _is_pdf_response(r):
                continue
            extraction = parse_pdf_bytes(r.content)
            if extraction.bookings:
                _apply_fallback_group(extraction.bookings, group_name)
                return extraction.bookings
        except requests.RequestException as exc:
            warnings.append(f"Unable to fetch PDF postback {event_target}: {exc}")
    return []


def _post_aspnet_form(
    session: requests.Session,
    url: str,
    payload: dict[str, str],
    *,
    referer: str,
    timeout_seconds: float,
) -> requests.Response:
    response = session.post(
        url,
        data=payload,
        timeout=timeout_seconds,
        headers={"Referer": referer},
        allow_redirects=True,
    )
    response.raise_for_status()
    if _is_blocked_page(response.text):
        raise RuntimeError("Esprit portal blocked this IP during login POST.")
    return response


def _run_student_login_flow(
    session: requests.Session,
    login_resp: requests.Response,
    login_soup: BeautifulSoup,
    *,
    student_id: str,
    password: str | None,
    captcha: str | None,
    extra_fields: dict[str, str] | None,
    timeout_seconds: float,
) -> tuple[requests.Response, BeautifulSoup]:
    current_resp = login_resp
    current_soup = login_soup
    submit_url = _resolve_form_action(current_soup, current_resp.url or DEFAULT_PORTAL_URL)

    # The live Esprit student tab first requires checking the robot image
    # checkbox, which makes the real "Suivant" submit button appear.
    if _input_exists(current_soup, STUDENT_ROBOT_FIELD) and not _input_exists(current_soup, STUDENT_NEXT_BUTTON_FIELD):
        payload = _build_student_robot_payload(current_soup, student_id=student_id)
        if extra_fields:
            payload.update({str(k): str(v) for k, v in extra_fields.items()})
        current_resp = _post_aspnet_form(
            session,
            submit_url,
            payload,
            referer=current_resp.url or DEFAULT_PORTAL_URL,
            timeout_seconds=timeout_seconds,
        )
        current_soup = BeautifulSoup(current_resp.text, "html.parser")
        submit_url = _resolve_form_action(current_soup, current_resp.url or submit_url)

    if _input_exists(current_soup, STUDENT_NEXT_BUTTON_FIELD):
        payload = _build_student_next_payload(current_soup, student_id=student_id, password=password)
        if captcha and not _input_exists(current_soup, STUDENT_ROBOT_FIELD):
            payload["captcha"] = captcha
        if extra_fields:
            payload.update({str(k): str(v) for k, v in extra_fields.items()})
        current_resp = _post_aspnet_form(
            session,
            submit_url,
            payload,
            referer=current_resp.url or DEFAULT_PORTAL_URL,
            timeout_seconds=timeout_seconds,
        )
        current_soup = BeautifulSoup(current_resp.text, "html.parser")
        submit_url = _resolve_form_action(current_soup, current_resp.url or submit_url)

    # Some valid IDs produce a second page that asks for the password after the
    # first "Suivant". Submit that final student credential page when present.
    if password and _input_exists(current_soup, STUDENT_PASSWORD_FIELD) and (
        _input_exists(current_soup, STUDENT_LOGIN_BUTTON_FIELD)
        or _input_exists(current_soup, STUDENT_NEXT_BUTTON_FIELD)
    ):
        payload = _build_student_next_payload(current_soup, student_id=student_id, password=password)
        if extra_fields:
            payload.update({str(k): str(v) for k, v in extra_fields.items()})
        current_resp = _post_aspnet_form(
            session,
            submit_url,
            payload,
            referer=current_resp.url or DEFAULT_PORTAL_URL,
            timeout_seconds=timeout_seconds,
        )
        current_soup = BeautifulSoup(current_resp.text, "html.parser")

    return current_resp, current_soup


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def scrape_esprit_timetable(
    *,
    student_id: str | None = None,
    password: str | None = None,
    captcha: str | None = None,
    extra_fields: dict[str, str] | None = None,
    timeout_seconds: float = 20.0,
) -> ScrapeResult:
    warnings: list[str] = []
    urllib3.disable_warnings(InsecureRequestWarning)

    session = requests.Session()
    session.verify = False
    session.headers.update(_BROWSER_HEADERS)

    bookings: list[dict[str, Any]] = []
    final_url: str = DEFAULT_TIMETABLE_URL
    emplois_soup: BeautifulSoup | None = None

    effective_group = student_id or "ESPRIT"

    # -----------------------------------------------------------------------
    # Path A: credentials provided → login first, then navigate to timetable
    # -----------------------------------------------------------------------
    if student_id:
        try:
            # 1. Load the login page
            login_resp = session.get(DEFAULT_PORTAL_URL, timeout=timeout_seconds)
            login_resp.raise_for_status()
            if _is_blocked_page(login_resp.text):
                raise RuntimeError("Esprit portal blocked this IP on the login page.")

            login_soup = BeautifulSoup(login_resp.text, "html.parser")
            if _input_exists(login_soup, STUDENT_ID_FIELD):
                post_resp, post_soup = _run_student_login_flow(
                    session,
                    login_resp,
                    login_soup,
                    student_id=student_id,
                    password=password,
                    captcha=captcha,
                    extra_fields=extra_fields,
                    timeout_seconds=timeout_seconds,
                )
            else:
                submit_url = _resolve_form_action(login_soup, login_resp.url or DEFAULT_PORTAL_URL)
                payload = _build_login_payload(
                    login_soup,
                    student_id=student_id,
                    password=password,
                    captcha=captcha,
                    extra_fields=extra_fields,
                )
                post_resp = _post_aspnet_form(
                    session,
                    submit_url,
                    payload,
                    referer=login_resp.url or DEFAULT_PORTAL_URL,
                    timeout_seconds=timeout_seconds,
                )
                post_soup = BeautifulSoup(post_resp.text, "html.parser")

            if _has_login_error(post_soup):
                warnings.append("Login failed: the portal returned an authentication error. "
                                 "Check your Student ID and password.")
                # Still continue — fall through to unauthenticated attempt
            elif _is_login_page(post_soup):
                warnings.append("Login did not succeed (still on login page). "
                                 "The portal may require a captcha or the credentials were wrong.")
            else:
                # 3. Login succeeded — try to go directly to Emplois.aspx
                emplois_url = DEFAULT_TIMETABLE_URL

                # Check if the landing page has a direct nav link to Emplois.aspx
                nav_link = _find_emplois_link(post_soup, post_resp.url)
                if nav_link:
                    emplois_url = nav_link

                emp_resp = session.get(
                    emplois_url,
                    timeout=timeout_seconds,
                    headers={"Referer": post_resp.url},
                )
                emp_resp.raise_for_status()

                if _is_pdf_response(emp_resp):
                    extraction = parse_pdf_bytes(emp_resp.content)
                    bookings = extraction.bookings
                    _apply_fallback_group(bookings, effective_group)
                    final_url = emp_resp.url
                else:
                    if _is_blocked_page(emp_resp.text):
                        raise RuntimeError("Esprit portal blocked this IP on the timetable page.")

                    emplois_soup = BeautifulSoup(emp_resp.text, "html.parser")
                    final_url = emp_resp.url

                    # 4a. Try HTML extraction
                    bookings = _extract_bookings_from_html(emplois_soup, group_name=effective_group)

                    # 4b. Fallback: look for embedded PDF on timetable page
                    if not bookings:
                        bookings = _try_pdf_links(
                            session, emplois_soup, final_url, effective_group, timeout_seconds, warnings
                        )

        except requests.RequestException as exc:
            warnings.append(f"Login flow network error: {exc}")
        except RuntimeError as exc:
            warnings.append(str(exc))

    # -----------------------------------------------------------------------
    # Path B: no credentials, or login/scrape failed → direct unauthenticated
    # -----------------------------------------------------------------------
    if not bookings:
        try:
            emp_resp = session.get(DEFAULT_TIMETABLE_URL, timeout=timeout_seconds)
            emp_resp.raise_for_status()

            if _is_pdf_response(emp_resp):
                extraction = parse_pdf_bytes(emp_resp.content)
                bookings = extraction.bookings
                _apply_fallback_group(bookings, effective_group)
                final_url = emp_resp.url
            else:
                if _is_blocked_page(emp_resp.text):
                    raise RuntimeError("Esprit portal blocked this IP on the timetable page (unauthenticated).")

                emplois_soup = BeautifulSoup(emp_resp.text, "html.parser")
                final_url = emp_resp.url

                bookings = _extract_bookings_from_html(emplois_soup, group_name=effective_group)

                if not bookings:
                    bookings = _try_pdf_links(
                        session, emplois_soup, final_url, effective_group, timeout_seconds, warnings
                    )

        except requests.RequestException as exc:
            raise RuntimeError(f"Unable to reach Esprit timetable page: {exc}") from exc

    # -----------------------------------------------------------------------
    # Final report
    # -----------------------------------------------------------------------
    if not bookings:
        warnings.append(_diagnose_empty_html_response(emplois_soup, final_url))

    rooms = sorted({b["room_name"] for b in bookings if b.get("room_name")})
    return ScrapeResult(bookings=bookings, rooms=rooms, source_url=final_url, warnings=warnings)
