from __future__ import annotations

import json
from pathlib import Path
from threading import Lock
from typing import Any

from backend.models.schemas import Booking, ConflictRecord, StorageState, UploadMetadata


class StorageManager:
    def __init__(self, storage_path: Path) -> None:
        self.storage_path = storage_path
        self._lock = Lock()
        self.storage_path.parent.mkdir(parents=True, exist_ok=True)
        if not self.storage_path.exists():
            self._write_state(StorageState())

    def load_state(self) -> StorageState:
        with self._lock:
            return self._read_state()

    def save_state(
        self,
        *,
        metadata: UploadMetadata,
        bookings: list[dict[str, Any]],
        rooms: list[str],
        conflicts: list[dict[str, Any]],
        extra: dict[str, Any] | None = None,
    ) -> StorageState:
        state = StorageState(
            metadata=metadata,
            bookings=[Booking(**booking) for booking in bookings],
            rooms=sorted({room for room in rooms if room}),
            conflicts=[ConflictRecord(**conflict) for conflict in conflicts],
            extra=extra or {},
        )
        with self._lock:
            self._write_state(state)
        return state

    def get_bookings(self) -> list[dict[str, Any]]:
        return [booking.model_dump() for booking in self.load_state().bookings]

    def get_rooms(self) -> list[str]:
        return self.load_state().rooms

    def get_conflicts(self) -> list[dict[str, Any]]:
        return [conflict.model_dump() for conflict in self.load_state().conflicts]

    def get_metadata(self) -> dict[str, Any]:
        return self.load_state().metadata.model_dump()

    def get_summary(self) -> dict[str, Any]:
        state = self.load_state()
        return {
            "metadata": state.metadata.model_dump(),
            "rooms": len(state.rooms),
            "bookings": len(state.bookings),
            "conflicts": len(state.conflicts),
        }

    def _read_state(self) -> StorageState:
        raw = json.loads(self.storage_path.read_text(encoding="utf-8"))
        metadata = UploadMetadata(**raw.get("metadata", {}))
        bookings = [Booking(**booking) for booking in raw.get("bookings", [])]
        rooms = [room for room in raw.get("rooms", []) if room]
        conflicts = [ConflictRecord(**conflict) for conflict in raw.get("conflicts", [])]
        extra = raw.get("extra", {})
        return StorageState(metadata=metadata, bookings=bookings, rooms=rooms, conflicts=conflicts, extra=extra)

    def _write_state(self, state: StorageState) -> None:
        payload = {
            "metadata": state.metadata.model_dump(),
            "bookings": [booking.model_dump() for booking in state.bookings],
            "rooms": state.rooms,
            "conflicts": [conflict.model_dump() for conflict in state.conflicts],
            "extra": state.extra,
        }
        self.storage_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
