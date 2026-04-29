from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.routers.availability import create_availability_router
from backend.routers.conflicts import create_conflicts_router
from backend.routers.rooms import create_rooms_router
from backend.routers.scrape import create_scrape_router
from backend.services.storage import StorageManager


BASE_DIR = Path(__file__).resolve().parent
STORAGE_FILE = BASE_DIR / "storage" / "data.json"

storage_manager = StorageManager(STORAGE_FILE)

app = FastAPI(
    title="Classroom Availability Finder",
    description="Scrape Esprit timetable rooms and find room availability.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(create_availability_router(storage_manager))
app.include_router(create_rooms_router(storage_manager))
app.include_router(create_conflicts_router(storage_manager))
app.include_router(create_scrape_router(storage_manager))


@app.get("/")
def root() -> dict[str, str]:
    return {"message": "Classroom Availability Finder API is running."}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
