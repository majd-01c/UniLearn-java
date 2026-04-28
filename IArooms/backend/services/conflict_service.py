from __future__ import annotations

from datetime import datetime
from itertools import combinations
from typing import Any


def _to_minutes(value: str) -> int:
    parsed = datetime.strptime(value, "%H:%M")
    return parsed.hour * 60 + parsed.minute


def _overlap_window(first: dict[str, Any], second: dict[str, Any]) -> tuple[str, str] | None:
    start = max(_to_minutes(first["start_time"]), _to_minutes(second["start_time"]))
    end = min(_to_minutes(first["end_time"]), _to_minutes(second["end_time"]))
    if start >= end:
        return None
    return f"{start // 60:02d}:{start % 60:02d}", f"{end // 60:02d}:{end % 60:02d}"


def detect_conflicts(bookings: list[dict[str, Any]]) -> list[dict[str, Any]]:
    conflicts: list[dict[str, Any]] = []
    grouped: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for booking in bookings:
        grouped.setdefault((booking["room_name"], booking["date"]), []).append(booking)

    for (room_name, date), room_bookings in grouped.items():
        for first, second in combinations(room_bookings, 2):
            if first["group_name"] == second["group_name"] and first["course_name"] == second["course_name"]:
                continue
            overlap = _overlap_window(first, second)
            if overlap is None:
                continue
            conflicts.append(
                {
                    "room": room_name,
                    "date": date,
                    "day_name": first["day_name"],
                    "overlap_start": overlap[0],
                    "overlap_end": overlap[1],
                    "booking_a": {
                        "room": first["room_name"],
                        "group": first["group_name"],
                        "course": first["course_name"],
                        "date": first["date"],
                        "day_name": first["day_name"],
                        "start_time": first["start_time"],
                        "end_time": first["end_time"],
                        "source_page": first["source_page"],
                    },
                    "booking_b": {
                        "room": second["room_name"],
                        "group": second["group_name"],
                        "course": second["course_name"],
                        "date": second["date"],
                        "day_name": second["day_name"],
                        "start_time": second["start_time"],
                        "end_time": second["end_time"],
                        "source_page": second["source_page"],
                    },
                }
            )
    return conflicts
