from __future__ import annotations

from fastapi import APIRouter


def create_conflicts_router(storage_manager) -> APIRouter:
    router = APIRouter()

    @router.get("/conflicts")
    def conflicts() -> dict[str, object]:
        state = storage_manager.load_state()
        return {"conflicts": [conflict.model_dump() for conflict in state.conflicts]}

    return router
