from __future__ import annotations

import io
import logging
import re
from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Any

import pdfplumber


logger = logging.getLogger(__name__)

DAY_LABELS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"]
DAY_TO_INDEX = {label: index for index, label in enumerate(DAY_LABELS)}
WEEK_RANGE_RE = re.compile(
    r"(?P<start>\d{2}/\d{2}/\d{4})\s*-\s*(?P<end>\d{2}/\d{2}/\d{4})"
)
DAY_HEADER_RE = re.compile(
    r"(?P<day>lundi|mardi|mercredi|jeudi|vendredi|samedi)\s+(?P<day_date>\d{1,2}/\d{1,2})(?:/\d{4})?",
    re.IGNORECASE,
)
DAY_ONLY_RE = re.compile(r"^(lundi|mardi|mercredi|jeudi|vendredi|samedi)$", re.IGNORECASE)
DAY_DATE_ONLY_RE = re.compile(r"^\d{1,2}/\d{1,2}(?:/\d{4})?$")
TIME_RE = re.compile(r"(?P<start>\d{1,2}[:h]\d{2})\s*[-–]\s*(?P<end>\d{1,2}[:h]\d{2})")
# Require at least 2-digit number to reduce false positives (e.g. TD2, TP3)
ROOM_RE = re.compile(r"\b[A-Z]{1,4}\d{2,4}\b")
GROUP_RE = re.compile(r"\b\d+[A-Z]{1,12}\d*\b")
ONLINE_RE = re.compile(r"en\s*ligne", re.IGNORECASE)


@dataclass
class ExtractionResult:
    bookings: list[dict[str, Any]]
    rooms: list[str]
    ignored_online_sessions: int
    total_pages_read: int


# ---------------------------------------------------------------------------
# Text helpers
# ---------------------------------------------------------------------------

def _normalize_spaces(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace("\xa0", " ")).strip()


def _normalize_time(value: str) -> str:
    """Convert '9h5', '09h00', '9:00', '09:00' → 'HH:MM'."""
    normalized = value.replace("h", ":")
    try:
        hour, minute = normalized.split(":")
        return f"{int(hour):02d}:{int(minute):02d}"
    except (ValueError, IndexError):
        return normalized


def _parse_date(value: str) -> date:
    return datetime.strptime(value, "%d/%m/%Y").date()


def _times_are_valid(start: str, end: str) -> bool:
    """Return True only if start < end (guards against inverted/corrupt times)."""
    try:
        s_parts = start.split(":")
        e_parts = end.split(":")
        s_min = int(s_parts[0]) * 60 + int(s_parts[1])
        e_min = int(e_parts[0]) * 60 + int(e_parts[1])
        return s_min < e_min
    except (ValueError, IndexError):
        return False


# ---------------------------------------------------------------------------
# Date / day helpers
# ---------------------------------------------------------------------------

def _infer_day_date(day_fragment: str, week_start: date, week_end: date) -> tuple[str, str]:
    day_number, month_number = [int(part) for part in day_fragment.split("/")[:2]]
    candidates = [date(week_start.year, month_number, day_number)]
    if week_end.year != week_start.year:
        candidates.append(date(week_end.year, month_number, day_number))
    for candidate in candidates:
        if week_start <= candidate <= week_end:
            day_name = DAY_LABELS[candidate.weekday()] if candidate.weekday() < len(DAY_LABELS) else ""
            return candidate.isoformat(), day_name.capitalize()
    best = min(candidates, key=lambda c: abs((c - week_start).days))
    day_name = DAY_LABELS[best.weekday()] if best.weekday() < len(DAY_LABELS) else ""
    return best.isoformat(), day_name.capitalize()


def _extract_week_range(text: str) -> tuple[date, date]:
    match = WEEK_RANGE_RE.search(text)
    if match:
        return _parse_date(match.group("start")), _parse_date(match.group("end"))
    logger.warning("No week range found in page — dates will be unreliable for this page.")
    today = datetime.now(timezone.utc).date()
    return today, today


# ---------------------------------------------------------------------------
# Group name helpers
# ---------------------------------------------------------------------------

def _extract_group_name(lines: list[str]) -> str:
    for line in lines:
        if "emploi du temps" in line.lower():
            suffix = _normalize_spaces(re.sub(r"(?i)emploi du temps", "", line)).strip("-: ")
            if suffix:
                match = GROUP_RE.search(suffix)
                if match:
                    return match.group(0)
                return suffix
    for line in lines[:20]:
        if any(label in line.lower() for label in DAY_LABELS):
            continue
        match = GROUP_RE.search(line)
        if match:
            return match.group(0)
    return "Unknown group"


def _build_group_name(text_lines: list[str]) -> str:
    result = _extract_group_name(text_lines)
    return result if result else "Unknown group"


# ---------------------------------------------------------------------------
# Room / course helpers
# ---------------------------------------------------------------------------

def _extract_room_candidates(text: str) -> list[str]:
    # Replace "/" with space so "M109/M111" → "M109 M111"
    matches = ROOM_RE.findall(text.replace("/", " "))
    unique: list[str] = []
    for room in matches:
        normalized = room.upper().strip()
        if ONLINE_RE.search(normalized):
            continue
        if normalized not in unique:
            unique.append(normalized)
    return unique


def _clean_course_name(text: str) -> str:
    cleaned = TIME_RE.sub(" ", text)
    cleaned = ROOM_RE.sub(" ", cleaned)
    cleaned = re.sub(
        r"\b(?:CM|TD|TP|Cours|Cours magistral|Travaux dirigés|Travaux pratiques)\b",
        " ",
        cleaned,
        flags=re.IGNORECASE,
    )
    return _normalize_spaces(cleaned).strip("-: ")


def _is_metadata_line(text: str) -> bool:
    lower = text.lower()
    return bool(
        WEEK_RANGE_RE.search(text)
        or any(label in lower for label in DAY_LABELS)
        or "année" in lower
        or "semestre" in lower
        or "emploi du temps" in lower
    )


# ---------------------------------------------------------------------------
# Line grouping
# ---------------------------------------------------------------------------

def _group_words_by_line(words: list[dict[str, Any]], y_tolerance: float = 3.5) -> list[dict[str, Any]]:
    ordered = sorted(words, key=lambda w: (round(w["top"], 1), w["x0"]))
    grouped: list[dict[str, Any]] = []
    current_words: list[dict[str, Any]] = []
    current_top: float | None = None

    for word in ordered:
        if current_top is None or abs(word["top"] - current_top) <= y_tolerance:
            current_words.append(word)
            current_top = word["top"] if current_top is None else min(current_top, word["top"])
            continue
        grouped.append({
            "top": current_top,
            "bottom": max(w["bottom"] for w in current_words),
            "words": current_words,
            "text": _normalize_spaces(" ".join(w["text"] for w in current_words)),
        })
        current_words = [word]
        current_top = word["top"]

    if current_words:
        grouped.append({
            "top": current_top,
            "bottom": max(w["bottom"] for w in current_words),
            "words": current_words,
            "text": _normalize_spaces(" ".join(w["text"] for w in current_words)),
        })
    return grouped


# ---------------------------------------------------------------------------
# Column-based parser (primary strategy)
# ---------------------------------------------------------------------------

def _extract_split_day_headers(
    grouped_lines: list[dict[str, Any]],
    week_start: date,
    week_end: date,
) -> list[dict[str, Any]]:
    """Handle PDFs where weekdays and dates are printed on separate rows."""
    for line_index, line in enumerate(grouped_lines):
        day_words = [
            word for word in line["words"]
            if DAY_ONLY_RE.match(_normalize_spaces(word["text"]).lower())
        ]
        if len(day_words) < 3:
            continue

        date_words: list[dict[str, Any]] = []
        for candidate_line in grouped_lines[line_index + 1:line_index + 4]:
            date_words = [
                word for word in candidate_line["words"]
                if DAY_DATE_ONLY_RE.match(_normalize_spaces(word["text"]))
            ]
            if len(date_words) >= min(3, len(day_words)):
                break

        if not date_words:
            continue

        headers: list[dict[str, Any]] = []
        used_dates: set[int] = set()
        for day_word in sorted(day_words, key=lambda item: item["x0"]):
            day_center = (day_word["x0"] + day_word["x1"]) / 2
            nearest_index, nearest_date = min(
                (
                    (index, date_word)
                    for index, date_word in enumerate(date_words)
                    if index not in used_dates
                ),
                key=lambda item: abs(((item[1]["x0"] + item[1]["x1"]) / 2) - day_center),
            )
            used_dates.add(nearest_index)
            day_date_iso, inferred_label = _infer_day_date(_normalize_spaces(nearest_date["text"]), week_start, week_end)
            headers.append({
                "day_name": inferred_label or _normalize_spaces(day_word["text"]).capitalize(),
                "date": day_date_iso,
                "x0": (day_word["x0"] + day_word["x1"]) / 2,
                "y": max(line["top"], nearest_date["top"]),
            })

        return headers

    return []


def _parse_day_columns(
    page: Any,           # pdfplumber Page
    week_start: date,
    week_end: date,
) -> list[dict[str, Any]]:
    """
    Parse timetable columns from a pdfplumber page.
    Returns a list with one element: {"bookings": [...], "ignored_online_sessions": int}
    Returns empty list if no day headers are detected (triggers linear fallback).
    """
    words = page.extract_words(keep_blank_chars=False) or []
    grouped_lines = _group_words_by_line(words)
    header_candidates: list[dict[str, Any]] = []

    for line in grouped_lines:
        match = DAY_HEADER_RE.search(line["text"])
        if not match:
            continue
        day_date_iso, day_label = _infer_day_date(match.group("day_date"), week_start, week_end)
        header_candidates.append({
            "day_name": day_label or match.group("day").capitalize(),
            "date": day_date_iso,
            "x0": min(w["x0"] for w in line["words"]),
            "y": line["top"],
        })

    if not header_candidates:
        header_candidates = _extract_split_day_headers(grouped_lines, week_start, week_end)

    if not header_candidates:
        return []

    header_candidates = sorted(header_candidates, key=lambda h: h["x0"])
    page_width = float(page.width or 1000)
    column_ranges: list[tuple[float, float, dict[str, Any]]] = []
    for i, header in enumerate(header_candidates):
        if i == 0:
            next_gap = header_candidates[i + 1]["x0"] - header["x0"] if len(header_candidates) > 1 else page_width
            x_start = max(0.0, header["x0"] - next_gap / 2)
        else:
            x_start = (header_candidates[i - 1]["x0"] + header["x0"]) / 2

        if i == len(header_candidates) - 1:
            previous_gap = header["x0"] - header_candidates[i - 1]["x0"] if i > 0 else page_width
            x_end = min(page_width, header["x0"] + previous_gap / 2)
        else:
            x_end = (header["x0"] + header_candidates[i + 1]["x0"]) / 2
        column_ranges.append((x_start, x_end, header))

    bookings: list[dict[str, Any]] = []
    ignored_online_sessions = 0

    for x_start, x_end, header in column_ranges:
        col_words = [
            w for w in words
            if x_start <= (w["x0"] + w["x1"]) / 2 <= x_end and w["top"] > header["y"] + 3
        ]
        col_lines = _group_words_by_line(col_words)
        pending_context: list[str] = []

        for line in col_lines:
            line_text = _normalize_spaces(line["text"])
            if not line_text or _is_metadata_line(line_text):
                continue

            if ONLINE_RE.search(line_text):
                ignored_online_sessions += 1
                pending_context.clear()
                continue

            time_match = TIME_RE.search(line_text)
            if time_match:
                candidate_text = _normalize_spaces(
                    " ".join(pending_context + [line_text[: time_match.start()], line_text[time_match.end():]])
                )

                if ONLINE_RE.search(candidate_text):
                    ignored_online_sessions += 1
                    pending_context.clear()
                    continue

                start_time = _normalize_time(time_match.group("start"))
                end_time = _normalize_time(time_match.group("end"))

                if not _times_are_valid(start_time, end_time):
                    logger.warning(
                        "Skipping booking with invalid time range %s-%s on page %d",
                        start_time, end_time, page.page_number,
                    )
                    pending_context.clear()
                    continue

                room_candidates = _extract_room_candidates(candidate_text)
                course_name = _clean_course_name(candidate_text) or "Unknown course"

                if not room_candidates:
                    room_candidates = ["Unspecified"]

                for room_name in room_candidates:
                    if ONLINE_RE.search(room_name):
                        ignored_online_sessions += 1
                        continue
                    bookings.append({
                        "group_name": "",           # filled by caller
                        "course_name": course_name,
                        "room_name": room_name,
                        "date": header["date"],
                        "day_name": header["day_name"],
                        "start_time": start_time,
                        "end_time": end_time,
                        "source_page": page.page_number,   # pdfplumber: 1-based
                        "raw_text": line_text,
                    })

                pending_context.clear()
                continue

            pending_context.append(line_text)
            if len(pending_context) > 4:
                pending_context.pop(0)

    return [{"bookings": bookings, "ignored_online_sessions": ignored_online_sessions}]


# ---------------------------------------------------------------------------
# Linear fallback parser (used when column detection finds no day headers)
# ---------------------------------------------------------------------------

def _linear_fallback(
    page: Any,           # pdfplumber Page
    week_start: date,
    week_end: date,
) -> tuple[list[dict[str, Any]], int]:
    text = page.extract_text(layout=True) or ""
    lines = [_normalize_spaces(line) for line in text.splitlines() if _normalize_spaces(line)]
    current_day_name = ""
    current_day_date = ""
    bookings: list[dict[str, Any]] = []
    ignored_online_sessions = 0
    pending_context: list[str] = []

    for line in lines:
        day_match = DAY_HEADER_RE.search(line)
        if day_match:
            current_day_date, current_day_name = _infer_day_date(
                day_match.group("day_date"), week_start, week_end
            )
            pending_context.clear()
            continue

        if not current_day_date or _is_metadata_line(line):
            continue

        if ONLINE_RE.search(line):
            ignored_online_sessions += 1
            pending_context.clear()
            continue

        time_match = TIME_RE.search(line)
        if not time_match:
            pending_context.append(line)
            if len(pending_context) > 4:
                pending_context.pop(0)
            continue

        candidate_text = _normalize_spaces(
            " ".join(pending_context + [line[: time_match.start()], line[time_match.end():]])
        )

        if ONLINE_RE.search(candidate_text):
            ignored_online_sessions += 1
            pending_context.clear()
            continue

        start_time = _normalize_time(time_match.group("start"))
        end_time = _normalize_time(time_match.group("end"))

        if not _times_are_valid(start_time, end_time):
            logger.warning(
                "Skipping booking with invalid time range %s-%s (page %d)",
                start_time, end_time, page.page_number,
            )
            pending_context.clear()
            continue

        room_candidates = _extract_room_candidates(candidate_text)
        course_name = _clean_course_name(candidate_text) or "Unknown course"

        if not room_candidates:
            room_candidates = ["Unspecified"]

        for room_name in room_candidates:
            if ONLINE_RE.search(room_name):
                ignored_online_sessions += 1
                continue
            bookings.append({
                "group_name": "",
                "course_name": course_name,
                "room_name": room_name,
                "date": current_day_date,
                "day_name": current_day_name,
                "start_time": start_time,
                "end_time": end_time,
                "source_page": page.page_number,   # pdfplumber: 1-based
                "raw_text": line,
            })

        pending_context.clear()

    return bookings, ignored_online_sessions


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

def parse_pdf_bytes(pdf_bytes: bytes) -> ExtractionResult:
    bookings: list[dict[str, Any]] = []
    rooms: set[str] = set()
    ignored_online_sessions = 0
    total_pages_read = 0

    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        total_pages_read = len(pdf.pages)

        for plumber_page in pdf.pages:
            page_text = plumber_page.extract_text(layout=True) or ""
            text_lines = [
                _normalize_spaces(line) for line in page_text.splitlines() if _normalize_spaces(line)
            ]
            group_name = _build_group_name(text_lines)
            week_start, week_end = _extract_week_range(page_text)

            parsed_columns = _parse_day_columns(plumber_page, week_start, week_end)

            if parsed_columns:
                for parsed in parsed_columns:
                    ignored_online_sessions += parsed["ignored_online_sessions"]
                    for booking in parsed["bookings"]:
                        booking["group_name"] = group_name
                        # Safety guard — should already be filtered, but never trust blindly
                        if ONLINE_RE.search(booking.get("room_name", "")):
                            # Do NOT increment counter here — already counted inside _parse_day_columns
                            continue
                        bookings.append(booking)
                        rooms.add(booking["room_name"])
            else:
                logger.warning(
                    "Page %d: no day columns detected — using linear fallback.",
                    plumber_page.page_number,
                )
                fallback_bookings, fallback_ignored = _linear_fallback(plumber_page, week_start, week_end)
                ignored_online_sessions += fallback_ignored
                for booking in fallback_bookings:
                    booking["group_name"] = group_name
                    if ONLINE_RE.search(booking.get("room_name", "")):
                        continue
                    bookings.append(booking)
                    rooms.add(booking["room_name"])

    bookings.sort(
        key=lambda item: (item["date"], item["start_time"], item["room_name"], item["group_name"])
    )

    return ExtractionResult(
        bookings=bookings,
        rooms=sorted(room for room in rooms if room and not ONLINE_RE.search(room)),
        ignored_online_sessions=ignored_online_sessions,
        total_pages_read=total_pages_read,
    )
