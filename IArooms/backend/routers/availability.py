from __future__ import annotations

from fastapi import APIRouter, Query

from backend.services.availability_service import get_availability, get_day_availability


def create_availability_router(storage_manager) -> APIRouter:
    router = APIRouter()

    @router.get("/availability")
    def availability(
        date: str = Query(..., description="ISO date YYYY-MM-DD"),
        start_time: str = Query(..., description="HH:MM"),
        end_time: str = Query(..., description="HH:MM"),
        room: str | None = Query(default=None),
        building: str | None = Query(default=None),
    ) -> dict[str, object]:
        state = storage_manager.load_state()
        # Convert pydantic Booking objects to plain dicts for service processing.
        bookings = [b.model_dump() for b in state.bookings]
        return get_availability(
            bookings,
            state.rooms,
            date=date,
            start_time=start_time,
            end_time=end_time,
            room_filter=room,
            building_filter=building,
        )

    @router.get("/availability/day")
    def availability_by_day(
        date: str = Query(..., description="ISO date YYYY-MM-DD"),
        room: str | None = Query(default=None),
        building: str | None = Query(default=None),
    ) -> dict[str, object]:
        state = storage_manager.load_state()
        bookings = [b.model_dump() for b in state.bookings]
        return get_day_availability(
            bookings,
            state.rooms,
            date=date,
            room_filter=room,
            building_filter=building,
        )

    return router
