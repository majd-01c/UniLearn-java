from __future__ import annotations

from fastapi import APIRouter


def create_rooms_router(storage_manager) -> APIRouter:
    router = APIRouter()

    @router.get("/rooms")
    def rooms() -> dict[str, object]:
        state = storage_manager.load_state()
        return {"rooms": state.rooms}

    @router.get("/bookings")
    def bookings() -> dict[str, object]:
        state = storage_manager.load_state()
        return {"bookings": [booking.model_dump() for booking in state.bookings]}

    @router.get("/summary")
    def summary() -> dict[str, object]:
        return storage_manager.get_summary()

    return router
