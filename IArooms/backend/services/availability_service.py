from __future__ import annotations

from datetime import datetime
from functools import lru_cache
from typing import Any


STANDARD_TIME_SLOTS = [
    {"label": "09:00 - 10:30", "start_time": "09:00", "end_time": "10:30"},
    {"label": "10:45 - 12:15", "start_time": "10:45", "end_time": "12:15"},
    {"label": "13:30 - 15:00", "start_time": "13:30", "end_time": "15:00"},
    {"label": "15:15 - 16:45", "start_time": "15:15", "end_time": "16:45"},
]

FRENCH_DAY_NAMES = ["Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"]


@lru_cache(maxsize=256)
def _to_minutes(value: str) -> int:
    parsed = datetime.strptime(value, "%H:%M")
    return parsed.hour * 60 + parsed.minute


def _overlaps(booking: dict[str, Any], start_time: str, end_time: str) -> bool:
    """
    A room is occupied when:
        booking.start_time < selected_end_time
        AND booking.end_time > selected_start_time
    (Strict inequalities — adjacent slots do NOT overlap.)
    """
    return (
        _to_minutes(booking["start_time"]) < _to_minutes(end_time)
        and _to_minutes(booking["end_time"]) > _to_minutes(start_time)
    )


def _overlaps_minutes(booking: dict[str, Any], start_minutes: int, end_minutes: int) -> bool:
    return (
        _to_minutes(booking.get("start_time", "00:00")) < end_minutes
        and _to_minutes(booking.get("end_time", "00:00")) > start_minutes
    )


def _room_matches(room_name: str, room_filter: str | None, building_filter: str | None) -> bool:
    room_upper = room_name.upper()
    if room_filter and room_filter.strip().upper() not in room_upper:
        return False
    if building_filter and not room_upper.startswith(building_filter.strip().upper()):
        return False
    return True


def get_availability(
    bookings: list[dict[str, Any]],
    rooms: list[str],
    *,
    date: str,
    start_time: str,
    end_time: str,
    room_filter: str | None = None,
    building_filter: str | None = None,
) -> dict[str, Any]:
    """Compute room availability for one time interval."""
    filtered_rooms = [r for r in rooms if _room_matches(r, room_filter, building_filter)]
    start_minutes = _to_minutes(start_time)
    end_minutes = _to_minutes(end_time)

    if not bookings:
        return {
            "date": date,
            "start_time": start_time,
            "end_time": end_time,
            "empty_rooms": sorted(filtered_rooms),
            "occupied_rooms": [],
        }

    occupied_rooms: list[dict[str, Any]] = []
    for row in bookings:
        if row.get("date") != date:
            continue
        room_name = str(row.get("room_name", ""))
        if not _room_matches(room_name, room_filter, building_filter):
            continue
        if not _overlaps_minutes(row, start_minutes, end_minutes):
            continue

        occupied_rooms.append({
            "room": row.get("room_name", "Unknown"),
            "group": row.get("group_name", "Unknown"),
            "course": row.get("course_name", "Unknown"),
            "start_time": row.get("start_time", "00:00"),
            "end_time": row.get("end_time", "00:00"),
            "source_page": row.get("source_page", 0),
        })

    occupied_room_names = {item["room"] for item in occupied_rooms}
    empty_rooms = [r for r in filtered_rooms if r not in occupied_room_names]

    return {
        "date": date,
        "start_time": start_time,
        "end_time": end_time,
        "empty_rooms": sorted(empty_rooms),
        "occupied_rooms": occupied_rooms,
    }


def get_day_availability(
    bookings: list[dict[str, Any]],
    rooms: list[str],
    *,
    date: str,
    room_filter: str | None = None,
    building_filter: str | None = None,
) -> dict[str, Any]:
    day_name = FRENCH_DAY_NAMES[datetime.strptime(date, "%Y-%m-%d").weekday()]
    day_rows = []
    for slot in STANDARD_TIME_SLOTS:
        availability = get_availability(
            bookings,
            rooms,
            date=date,
            start_time=slot["start_time"],
            end_time=slot["end_time"],
            room_filter=room_filter,
            building_filter=building_filter,
        )
        day_rows.append({
            "date": date,
            "day_name": day_name,
            "slot": slot["label"],
            "start_time": slot["start_time"],
            "end_time": slot["end_time"],
            "empty_count": len(availability["empty_rooms"]),
            "occupied_count": len(availability["occupied_rooms"]),
            "empty_rooms": availability["empty_rooms"],
            "occupied_rooms": availability["occupied_rooms"],
        })
    return {"date": date, "slots": day_rows}
