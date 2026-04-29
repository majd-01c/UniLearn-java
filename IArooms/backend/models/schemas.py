from __future__ import annotations

from typing import Any, Optional

from pydantic import BaseModel, Field


class Booking(BaseModel):
    group_name: str = Field(..., description="Group or class name")
    course_name: str = Field(..., description="Course or module name")
    room_name: str = Field(..., description="Physical room name")
    date: str = Field(..., description="ISO date YYYY-MM-DD")
    day_name: str = Field(..., description="French day label")
    start_time: str = Field(..., description="HH:MM")
    end_time: str = Field(..., description="HH:MM")
    source_page: int = Field(..., ge=1)
    raw_text: Optional[str] = Field(default=None, description="Original text fragment")


class AvailabilityQuery(BaseModel):
    date: str
    start_time: str
    end_time: str
    room: Optional[str] = None
    building: Optional[str] = None


class OccupiedRoom(BaseModel):
    room: str
    group: str
    course: str
    start_time: str
    end_time: str
    source_page: int


class AvailabilityResponse(BaseModel):
    date: str
    start_time: str
    end_time: str
    empty_rooms: list[str]
    occupied_rooms: list[OccupiedRoom]


class DaySlotAvailability(BaseModel):
    date: str
    day_name: str
    slot: str
    start_time: str
    end_time: str
    empty_count: int
    occupied_count: int
    empty_rooms: list[str]
    occupied_rooms: list[OccupiedRoom]


class UploadMetadata(BaseModel):
    total_pages_read: int
    total_bookings_extracted: int
    total_physical_rooms_found: int
    ignored_online_sessions: int


class ConflictBookingSide(BaseModel):
    room: str
    group: str
    course: str
    date: str
    day_name: str
    start_time: str
    end_time: str
    source_page: int


class ConflictRecord(BaseModel):
    room: str
    date: str
    day_name: str
    overlap_start: str
    overlap_end: str
    booking_a: ConflictBookingSide
    booking_b: ConflictBookingSide


class SummaryResponse(BaseModel):
    metadata: UploadMetadata
    rooms: int
    bookings: int
    conflicts: int


class StorageState(BaseModel):
    metadata: UploadMetadata = Field(default_factory=lambda: UploadMetadata(
        total_pages_read=0,
        total_bookings_extracted=0,
        total_physical_rooms_found=0,
        ignored_online_sessions=0,
    ))
    bookings: list[Booking] = Field(default_factory=list)
    rooms: list[str] = Field(default_factory=list)
    conflicts: list[ConflictRecord] = Field(default_factory=list)
    extra: dict[str, Any] = Field(default_factory=dict)
