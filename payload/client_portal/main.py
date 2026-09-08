from __future__ import annotations

import json
import sqlite3
import time
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parent
DB = ROOT / "routecopilot_client.db"
HTML = (ROOT / "static" / "delivery.html").read_text(encoding="utf-8")

app = FastAPI(title="RouteCopilot Client Portal")


def db() -> sqlite3.Connection:
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    con.execute(
        """
        CREATE TABLE IF NOT EXISTS deliveries (
            token TEXT PRIMARY KEY,
            br TEXT NOT NULL,
            recipient_name TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'A_CAMINHO',
            pause_reason TEXT NOT NULL DEFAULT '',
            preference TEXT NOT NULL DEFAULT '',
            neighbor_name TEXT NOT NULL DEFAULT '',
            neighbor_phone TEXT NOT NULL DEFAULT '',
            has_keyword INTEGER NOT NULL DEFAULT 0,
            keyword TEXT NOT NULL DEFAULT '',
            confirmed_at INTEGER,
            driver_lat REAL,
            driver_lon REAL,
            destination_lat REAL,
            destination_lon REAL,
            updated_at INTEGER NOT NULL
        )
        """
    )
    con.commit()
    return con


class ConfirmRequest(BaseModel):
    preference: Literal["HANDS", "NEIGHBOR", "PORTER", "PORCH_MAILBOX", "NOBODY_AVAILABLE"]
    neighbor_name: str = Field(default="", max_length=80)
    neighbor_phone: str = Field(default="", max_length=30)
    has_keyword: bool = False
    keyword: str = Field(default="", max_length=60)


class DeliveryUpsert(BaseModel):
    br: str
    recipient_name: str = ""
    destination_lat: float | None = None
    destination_lon: float | None = None


class DriverState(BaseModel):
    status: Literal["A_CAMINHO", "PROXIMO", "PAUSADO", "ENTREGUE", "OCORRENCIA"]
    pause_reason: str = ""
    driver_lat: float | None = None
    driver_lon: float | None = None


@app.get("/health")
def health():
    return {"ok": True}


@app.put("/api/delivery/{token}")
def upsert_delivery(token: str, body: DeliveryUpsert):
    now = int(time.time())
    with db() as con:
        con.execute(
            """
            INSERT INTO deliveries(token, br, recipient_name, destination_lat, destination_lon, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(token) DO UPDATE SET
              br=excluded.br,
              recipient_name=excluded.recipient_name,
              destination_lat=excluded.destination_lat,
              destination_lon=excluded.destination_lon,
              updated_at=excluded.updated_at
            """,
            (token, body.br, body.recipient_name, body.destination_lat, body.destination_lon, now),
        )
        con.commit()
    return {"ok": True}


@app.get("/api/delivery/{token}")
def delivery(token: str):
    with db() as con:
        row = con.execute("SELECT * FROM deliveries WHERE token=?", (token,)).fetchone()
    if not row:
        raise HTTPException(404, "Entrega não encontrada")
    data = dict(row)
    data["has_keyword"] = bool(data["has_keyword"])
    return data


@app.post("/api/delivery/{token}/confirm")
def confirm(token: str, body: ConfirmRequest):
    now = int(time.time())
    if body.preference == "NEIGHBOR" and not body.neighbor_name.strip():
        raise HTTPException(400, "Informe o nome do vizinho")
    if body.has_keyword and not body.keyword.strip():
        raise HTTPException(400, "Informe a palavra-chave")

    with db() as con:
        exists = con.execute("SELECT 1 FROM deliveries WHERE token=?", (token,)).fetchone()
        if not exists:
            raise HTTPException(404, "Entrega não encontrada")
        con.execute(
            """
            UPDATE deliveries SET
              preference=?, neighbor_name=?, neighbor_phone=?, has_keyword=?, keyword=?,
              confirmed_at=?, updated_at=?
            WHERE token=?
            """,
            (
                body.preference,
                body.neighbor_name.strip(),
                body.neighbor_phone.strip(),
                1 if body.has_keyword else 0,
                body.keyword.strip(),
                now,
                now,
                token,
            ),
        )
        con.commit()
    return {"ok": True, "confirmed_at": now}


@app.post("/api/delivery/{token}/driver-state")
def driver_state(token: str, body: DriverState):
    now = int(time.time())
    with db() as con:
        exists = con.execute("SELECT 1 FROM deliveries WHERE token=?", (token,)).fetchone()
        if not exists:
            raise HTTPException(404, "Entrega não encontrada")
        con.execute(
            """
            UPDATE deliveries SET status=?, pause_reason=?, driver_lat=?, driver_lon=?, updated_at=?
            WHERE token=?
            """,
            (body.status, body.pause_reason, body.driver_lat, body.driver_lon, now, token),
        )
        con.commit()
    return {"ok": True}


@app.get("/t/{token}", response_class=HTMLResponse)
def tracking_page(token: str):
    # O token é lido pelo JavaScript da própria página.
    return HTML.replace("__TOKEN__", json.dumps(token))
