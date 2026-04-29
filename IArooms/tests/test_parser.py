"""
Unit tests for the Classroom Availability Finder backend.

Run with:
    cd unilearn
    python -m pytest IArooms/tests/ -v
"""
from __future__ import annotations

from datetime import date

import pytest

from backend.services.pdf_parser import (
    _clean_course_name,
    _extract_room_candidates,
    _extract_week_range,
    _infer_day_date,
    _normalize_time,
    _times_are_valid,
)
from backend.services.availability_service import _overlaps
from backend.services.conflict_service import detect_conflicts


# ===========================================================================
# _normalize_time
# ===========================================================================

class TestNormalizeTime:
    def test_colon_zero_padded(self):
        assert _normalize_time("09:00") == "09:00"

    def test_colon_single_digit_hour(self):
        assert _normalize_time("9:00") == "09:00"

    def test_h_separator(self):
        assert _normalize_time("09h00") == "09:00"

    def test_h_separator_single_digit_hour(self):
        assert _normalize_time("9h00") == "09:00"

    def test_h_separator_single_digit_minute(self):
        # Was broken with old zfill approach — must now be "09:05"
        assert _normalize_time("9h5") == "09:05"

    def test_end_of_day(self):
        assert _normalize_time("16:45") == "16:45"

    def test_h_end_of_day(self):
        assert _normalize_time("16h45") == "16:45"


# ===========================================================================
# _times_are_valid
# ===========================================================================

class TestTimesAreValid:
    def test_valid_range(self):
        assert _times_are_valid("09:00", "12:15") is True

    def test_equal_times_invalid(self):
        assert _times_are_valid("09:00", "09:00") is False

    def test_inverted_invalid(self):
        assert _times_are_valid("12:15", "09:00") is False

    def test_adjacent_slots_valid(self):
        assert _times_are_valid("10:30", "10:45") is True


# ===========================================================================
# _infer_day_date
# ===========================================================================

class TestInferDayDate:
    WS = date(2026, 4, 27)   # Monday
    WE = date(2026, 5, 2)    # Saturday

    def test_lundi(self):
        iso, label = _infer_day_date("27/4", self.WS, self.WE)
        assert iso == "2026-04-27"
        assert label == "Lundi"

    def test_mardi(self):
        iso, label = _infer_day_date("28/4", self.WS, self.WE)
        assert iso == "2026-04-28"
        assert label == "Mardi"

    def test_mercredi(self):
        iso, label = _infer_day_date("29/4", self.WS, self.WE)
        assert iso == "2026-04-29"
        assert label == "Mercredi"

    def test_jeudi(self):
        iso, label = _infer_day_date("30/4", self.WS, self.WE)
        assert iso == "2026-04-30"
        assert label == "Jeudi"

    def test_vendredi(self):
        iso, label = _infer_day_date("1/5", self.WS, self.WE)
        assert iso == "2026-05-01"
        assert label == "Vendredi"

    def test_samedi(self):
        iso, label = _infer_day_date("2/5", self.WS, self.WE)
        assert iso == "2026-05-02"
        assert label == "Samedi"


# ===========================================================================
# _extract_week_range
# ===========================================================================

class TestExtractWeekRange:
    def test_standard_format(self):
        ws, we = _extract_week_range("Semaine : 26/04/2026 - 02/05/2026")
        assert ws == date(2026, 4, 26)
        assert we == date(2026, 5, 2)

    def test_no_range_returns_today(self):
        from datetime import datetime, timezone
        ws, we = _extract_week_range("No dates here")
        today = datetime.now(timezone.utc).date()
        assert ws == today
        assert we == today


# ===========================================================================
# _extract_room_candidates
# ===========================================================================

class TestExtractRoomCandidates:
    def test_single_room(self):
        assert _extract_room_candidates("A42") == ["A42"]

    def test_multi_room_comma(self):
        result = _extract_room_candidates("M109, M111")
        assert "M109" in result
        assert "M111" in result
        assert len(result) == 2

    def test_multi_room_slash(self):
        result = _extract_room_candidates("M109/M111")
        assert "M109" in result
        assert "M111" in result

    def test_online_ignored(self):
        assert _extract_room_candidates("En ligne") == []

    def test_mixed_online_and_room(self):
        # "En ligne" should be excluded, physical room kept
        result = _extract_room_candidates("A42 En ligne")
        assert "A42" in result
        assert all("ligne" not in r.lower() for r in result)

    def test_no_rooms(self):
        assert _extract_room_candidates("Some random text without rooms") == []


# ===========================================================================
# _clean_course_name
# ===========================================================================

class TestCleanCourseName:
    def test_strips_room(self):
        result = _clean_course_name("FUNDAMENTALS OF MATH 2 A42")
        assert "A42" not in result
        assert "FUNDAMENTALS OF MATH 2" in result

    def test_strips_time(self):
        result = _clean_course_name("MATH 2 09:00-12:15")
        assert "09:00" not in result
        assert "12:15" not in result

    def test_strips_td_tp_labels(self):
        result = _clean_course_name("TD MATH 2")
        assert result.strip() == "MATH 2"


# ===========================================================================
# _overlaps (availability logic)
# ===========================================================================

class TestOverlaps:
    def _b(self, start, end):
        return {"start_time": start, "end_time": end}

    def test_booking_fully_inside_slot(self):
        assert _overlaps(self._b("09:00", "12:15"), "09:00", "10:30") is True

    def test_booking_spans_slot(self):
        assert _overlaps(self._b("09:00", "12:15"), "10:45", "12:15") is True

    def test_no_overlap_booking_before(self):
        assert _overlaps(self._b("13:30", "16:45"), "09:00", "10:30") is False

    def test_no_overlap_booking_after(self):
        assert _overlaps(self._b("13:30", "16:45"), "09:00", "12:15") is False

    def test_no_overlap_adjacent_slots(self):
        # Booking ends at 10:30 — slot starts at 10:45 → strict inequality → no overlap
        assert _overlaps(self._b("09:00", "10:30"), "10:45", "12:15") is False

    def test_no_overlap_exact_boundary(self):
        # Booking ends exactly when slot starts
        assert _overlaps(self._b("09:00", "10:45"), "10:45", "12:15") is False

    def test_single_minute_overlap(self):
        assert _overlaps(self._b("10:00", "10:46"), "10:45", "12:15") is True


# ===========================================================================
# detect_conflicts
# ===========================================================================

def _make_booking(room, group, course, date_str, start, end, page=1):
    return {
        "room_name": room,
        "group_name": group,
        "course_name": course,
        "date": date_str,
        "day_name": "Lundi",
        "start_time": start,
        "end_time": end,
        "source_page": page,
    }


class TestDetectConflicts:
    DATE = "2026-04-27"

    def test_conflict_same_room_overlapping(self):
        b1 = _make_booking("A42", "1A1", "MATH", self.DATE, "09:00", "12:15")
        b2 = _make_booking("A42", "2A1", "PHYSICS", self.DATE, "10:00", "11:00")
        conflicts = detect_conflicts([b1, b2])
        assert len(conflicts) == 1
        assert conflicts[0]["room"] == "A42"

    def test_no_conflict_different_rooms(self):
        b1 = _make_booking("A42", "1A1", "MATH", self.DATE, "09:00", "12:15")
        b2 = _make_booking("A43", "2A1", "PHYSICS", self.DATE, "09:00", "12:15")
        assert detect_conflicts([b1, b2]) == []

    def test_no_conflict_adjacent_slots(self):
        b1 = _make_booking("A42", "1A1", "MATH", self.DATE, "09:00", "10:30")
        b2 = _make_booking("A42", "2A1", "PHYSICS", self.DATE, "10:45", "12:15")
        assert detect_conflicts([b1, b2]) == []

    def test_no_conflict_different_dates(self):
        b1 = _make_booking("A42", "1A1", "MATH", "2026-04-27", "09:00", "12:15")
        b2 = _make_booking("A42", "2A1", "PHYSICS", "2026-04-28", "09:00", "12:15")
        assert detect_conflicts([b1, b2]) == []

    def test_no_conflict_same_group_and_course(self):
        # Duplicate booking for same group/course must be ignored (not a real conflict)
        b1 = _make_booking("A42", "1A1", "MATH", self.DATE, "09:00", "12:15")
        b2 = _make_booking("A42", "1A1", "MATH", self.DATE, "09:00", "12:15")
        assert detect_conflicts([b1, b2]) == []

    def test_conflict_overlap_window_correct(self):
        b1 = _make_booking("A42", "1A1", "MATH", self.DATE, "09:00", "11:00")
        b2 = _make_booking("A42", "2A1", "PHYSICS", self.DATE, "10:00", "12:00")
        conflicts = detect_conflicts([b1, b2])
        assert len(conflicts) == 1
        assert conflicts[0]["overlap_start"] == "10:00"
        assert conflicts[0]["overlap_end"] == "11:00"
