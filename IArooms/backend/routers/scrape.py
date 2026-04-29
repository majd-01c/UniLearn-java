from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from backend.models.schemas import UploadMetadata
from backend.services.conflict_service import detect_conflicts
from backend.services.esprit_scraper import scrape_esprit_timetable


class EspritScrapeRequest(BaseModel):
    student_id: str | None = Field(default=None, description="Student identifier used on Esprit portal")
    password: str | None = Field(default=None, description="Optional portal password if required")
    captcha: str | None = Field(default=None, description="Optional captcha/robot field")
    extra_fields: dict[str, str] | None = Field(
        default=None,
        description="Optional ASP.NET form fields to force in login payload",
    )
    timeout_seconds: float = Field(default=20.0, ge=5.0, le=90.0)


def create_scrape_router(storage_manager) -> APIRouter:
    router = APIRouter(prefix="/scrape", tags=["scrape"])

    @router.post("/esprit")
    def scrape_esprit(payload: EspritScrapeRequest) -> dict[str, Any]:
        try:
            result = scrape_esprit_timetable(
                student_id=payload.student_id,
                password=payload.password,
                captcha=payload.captcha,
                extra_fields=payload.extra_fields,
                timeout_seconds=payload.timeout_seconds,
            )
        except RuntimeError as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

        conflicts = detect_conflicts(result.bookings)
        metadata = UploadMetadata(
            total_pages_read=0,
            total_bookings_extracted=len(result.bookings),
            total_physical_rooms_found=len(result.rooms),
            ignored_online_sessions=0,
        )

        storage_manager.save_state(
            metadata=metadata,
            bookings=result.bookings,
            rooms=result.rooms,
            conflicts=conflicts,
            extra={
                "source": "esprit-website",
                "source_url": result.source_url,
                "warnings": result.warnings,
                "student_id": payload.student_id,
            },
        )

        return {
            **metadata.model_dump(),
            "conflicts": len(conflicts),
            "source_url": result.source_url,
            "warnings": result.warnings,
        }

    return router
