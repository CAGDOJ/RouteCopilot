from __future__ import annotations

import json
import os
import sqlite3
import time
import urllib.error
import urllib.request
import uuid
from datetime import date
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from pydantic import BaseModel, Field

DB = Path(__file__).with_name("routecopilot.db")

ASAAS_API_KEY = os.getenv("ASAAS_API_KEY", "").strip()
ASAAS_BASE_URL = os.getenv(
    "ASAAS_BASE_URL",
    "https://api-sandbox.asaas.com/v3",
).rstrip("/")
ASAAS_WEBHOOK_TOKEN = os.getenv("ASAAS_WEBHOOK_TOKEN", "").strip()
ADMIN_TOKEN = os.getenv("ROUTECOPILOT_ADMIN_TOKEN", "").strip()

DEFAULT_ORIGINS = ",".join(
    [
        "https://cagdoj.github.io",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
    ]
)
ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv("ROUTECOPILOT_ALLOWED_ORIGINS", DEFAULT_ORIGINS).split(",")
    if origin.strip()
]

PRICE_PER_ROUTE_CENTS = 500

app = FastAPI(title="RouteCopilot API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "OPTIONS"],
    allow_headers=["*"],
)


def connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB, timeout=20)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")

    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS tracking (
            token TEXT PRIMARY KEY,
            courier_lat REAL,
            courier_lon REAL,
            destination_lat REAL,
            destination_lon REAL,
            eta_minutes INTEGER,
            remaining_stops INTEGER DEFAULT 0,
            status TEXT,
            updated_at INTEGER
        );

        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            device_id TEXT UNIQUE NOT NULL,
            name TEXT NOT NULL,
            email TEXT NOT NULL DEFAULT '',
            mobile_phone TEXT NOT NULL DEFAULT '',
            cpf_last4 TEXT NOT NULL DEFAULT '',
            asaas_customer_id TEXT,
            credits INTEGER NOT NULL DEFAULT 0,
            blocked INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS payments (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            provider_payment_id TEXT UNIQUE,
            route_credits INTEGER NOT NULL,
            value_cents INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'PENDING',
            pix_payload TEXT,
            pix_qr_base64 TEXT,
            expiration_date TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS activated_routes (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            at_code TEXT NOT NULL,
            activated_at INTEGER NOT NULL,
            UNIQUE(user_id, at_code),
            FOREIGN KEY(user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS route_runs (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            at_code TEXT NOT NULL,
            started_at INTEGER NOT NULL,
            finished_at INTEGER,
            distance_meters REAL NOT NULL DEFAULT 0,
            total_packages INTEGER NOT NULL DEFAULT 0,
            delivered_packages INTEGER NOT NULL DEFAULT 0,
            occurrences INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS webhook_events (
            event_id TEXT PRIMARY KEY,
            received_at INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS credit_adjustments (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            delta INTEGER NOT NULL,
            reason TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            FOREIGN KEY(user_id) REFERENCES users(id)
        );
        """
    )

    conn.commit()
    return conn


def now_ts() -> int:
    return int(time.time())


def require_admin(token: str | None) -> None:
    if not ADMIN_TOKEN:
        raise HTTPException(503, "Painel administrativo ainda não foi configurado.")

    if token != ADMIN_TOKEN:
        raise HTTPException(401, "Token administrativo inválido.")


def normalize_digits(value: str) -> str:
    return "".join(ch for ch in value if ch.isdigit())


def asaas_request(
    method: str,
    path: str,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    if not ASAAS_API_KEY:
        raise HTTPException(
            503,
            "PIX ainda não foi configurado no servidor. Defina ASAAS_API_KEY.",
        )

    url = f"{ASAAS_BASE_URL}/{path.lstrip('/')}"

    body = None
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")

    request = urllib.request.Request(
        url=url,
        data=body,
        method=method.upper(),
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "RouteCopilot/1.0",
            "access_token": ASAAS_API_KEY,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        raise HTTPException(
            502,
            f"Falha no provedor PIX ({exc.code}): {raw[:500]}",
        ) from exc
    except Exception as exc:
        raise HTTPException(502, f"Falha de comunicação com o provedor PIX: {exc}") from exc


def ensure_asaas_customer(
    conn: sqlite3.Connection,
    user_id: str,
    name: str,
    cpf_cnpj: str,
    mobile_phone: str,
    email: str,
) -> str:
    row = conn.execute(
        "SELECT asaas_customer_id FROM users WHERE id=?",
        (user_id,),
    ).fetchone()

    if row and row["asaas_customer_id"]:
        return str(row["asaas_customer_id"])

    payload: dict[str, Any] = {
        "name": name,
        "cpfCnpj": normalize_digits(cpf_cnpj),
        "mobilePhone": normalize_digits(mobile_phone),
        "externalReference": user_id,
        "notificationDisabled": True,
    }

    if email.strip():
        payload["email"] = email.strip()

    customer = asaas_request("POST", "/customers", payload)
    customer_id = str(customer.get("id") or "").strip()

    if not customer_id:
        raise HTTPException(502, "O provedor PIX não retornou o ID do cliente.")

    conn.execute(
        """
        UPDATE users
        SET asaas_customer_id=?, updated_at=?
        WHERE id=?
        """,
        (customer_id, now_ts(), user_id),
    )
    conn.commit()

    return customer_id


class TrackUpdate(BaseModel):
    courier_lat: float
    courier_lon: float
    destination_lat: float | None = None
    destination_lon: float | None = None
    eta_minutes: int
    remaining_stops: int = 0
    status: str = "PENDING"


class AccountRegister(BaseModel):
    device_id: str = Field(min_length=4, max_length=200)
    name: str = Field(min_length=2, max_length=120)
    cpf_cnpj: str = Field(min_length=11, max_length=18)
    mobile_phone: str = Field(min_length=8, max_length=30)
    email: str = Field(default="", max_length=180)


class PixPurchase(BaseModel):
    user_id: str
    route_credits: int = Field(ge=1, le=100)


class RouteActivation(BaseModel):
    user_id: str
    at_code: str = Field(min_length=5, max_length=80)


class RouteRunStart(BaseModel):
    user_id: str
    at_code: str
    started_at: int


class RouteRunFinish(BaseModel):
    user_id: str
    at_code: str
    started_at: int
    finished_at: int
    distance_meters: float = Field(ge=0)
    total_packages: int = Field(ge=0)
    delivered_packages: int = Field(ge=0)
    occurrences: int = Field(ge=0)


class CreditAdjustment(BaseModel):
    delta: int = Field(ge=-1000, le=1000)
    reason: str = Field(min_length=2, max_length=180)


@app.get("/health")
def health():
    return {
        "ok": True,
        "pix_configured": bool(ASAAS_API_KEY),
        "admin_configured": bool(ADMIN_TOKEN),
        "environment": "sandbox"
        if "sandbox" in ASAAS_BASE_URL
        else "production",
    }


@app.post("/api/track/{token}")
def update_tracking(token: str, body: TrackUpdate):
    conn = connection()

    conn.execute(
        """
        INSERT INTO tracking(
            token,
            courier_lat,
            courier_lon,
            destination_lat,
            destination_lon,
            eta_minutes,
            remaining_stops,
            status,
            updated_at
        )
        VALUES(?,?,?,?,?,?,?,?,?)
        ON CONFLICT(token) DO UPDATE SET
            courier_lat=excluded.courier_lat,
            courier_lon=excluded.courier_lon,
            destination_lat=excluded.destination_lat,
            destination_lon=excluded.destination_lon,
            eta_minutes=excluded.eta_minutes,
            remaining_stops=excluded.remaining_stops,
            status=excluded.status,
            updated_at=excluded.updated_at
        """,
        (
            token,
            body.courier_lat,
            body.courier_lon,
            body.destination_lat,
            body.destination_lon,
            max(1, body.eta_minutes),
            max(0, body.remaining_stops),
            body.status,
            now_ts(),
        ),
    )

    conn.commit()
    conn.close()

    return {"ok": True}


@app.get("/api/track/{token}")
def get_tracking(token: str):
    conn = connection()

    row = conn.execute(
        """
        SELECT
            courier_lat,
            courier_lon,
            destination_lat,
            destination_lon,
            eta_minutes,
            remaining_stops,
            status,
            updated_at
        FROM tracking
        WHERE token=?
        """,
        (token,),
    ).fetchone()

    conn.close()

    if not row:
        raise HTTPException(404, "tracking not found")

    return dict(row)


@app.post("/api/account/register")
def register_account(body: AccountRegister):
    digits = normalize_digits(body.cpf_cnpj)

    if len(digits) not in (11, 14):
        raise HTTPException(400, "CPF/CNPJ inválido.")

    now = now_ts()

    with connection() as conn:
        existing = conn.execute(
            "SELECT * FROM users WHERE device_id=?",
            (body.device_id.strip(),),
        ).fetchone()

        if existing:
            user_id = str(existing["id"])

            conn.execute(
                """
                UPDATE users
                SET name=?, email=?, mobile_phone=?, cpf_last4=?, updated_at=?
                WHERE id=?
                """,
                (
                    body.name.strip(),
                    body.email.strip(),
                    normalize_digits(body.mobile_phone),
                    digits[-4:],
                    now,
                    user_id,
                ),
            )
        else:
            user_id = uuid.uuid4().hex

            conn.execute(
                """
                INSERT INTO users(
                    id,
                    device_id,
                    name,
                    email,
                    mobile_phone,
                    cpf_last4,
                    credits,
                    blocked,
                    created_at,
                    updated_at
                )
                VALUES(?,?,?,?,?,?,0,0,?,?)
                """,
                (
                    user_id,
                    body.device_id.strip(),
                    body.name.strip(),
                    body.email.strip(),
                    normalize_digits(body.mobile_phone),
                    digits[-4:],
                    now,
                    now,
                ),
            )

        conn.commit()

        customer_id = ensure_asaas_customer(
            conn=conn,
            user_id=user_id,
            name=body.name.strip(),
            cpf_cnpj=digits,
            mobile_phone=body.mobile_phone,
            email=body.email,
        )

        row = conn.execute(
            "SELECT id, name, email, mobile_phone, credits, blocked FROM users WHERE id=?",
            (user_id,),
        ).fetchone()

    return {
        "ok": True,
        "user": dict(row),
        "provider_customer_ready": bool(customer_id),
    }


@app.get("/api/account/{user_id}")
def account(user_id: str):
    with connection() as conn:
        row = conn.execute(
            """
            SELECT id, name, email, mobile_phone, credits, blocked, created_at, updated_at
            FROM users
            WHERE id=?
            """,
            (user_id,),
        ).fetchone()

    if not row:
        raise HTTPException(404, "Usuário não encontrado.")

    return dict(row)


@app.post("/api/billing/pix")
def create_pix(body: PixPurchase):
    with connection() as conn:
        user = conn.execute(
            "SELECT * FROM users WHERE id=?",
            (body.user_id,),
        ).fetchone()

        if not user:
            raise HTTPException(404, "Usuário não encontrado.")

        if int(user["blocked"] or 0) == 1:
            raise HTTPException(403, "Conta bloqueada.")

        customer_id = str(user["asaas_customer_id"] or "").strip()

        if not customer_id:
            raise HTTPException(
                409,
                "Cadastro financeiro incompleto. Faça o cadastro da conta novamente.",
            )

        value_cents = body.route_credits * PRICE_PER_ROUTE_CENTS
        local_payment_id = uuid.uuid4().hex

        payment = asaas_request(
            "POST",
            "/payments",
            {
                "customer": customer_id,
                "billingType": "PIX",
                "value": value_cents / 100.0,
                "dueDate": date.today().isoformat(),
                "description": (
                    f"RouteCopilot - {body.route_credits} crédito(s) de rota"
                ),
                "externalReference": local_payment_id,
            },
        )

        provider_payment_id = str(payment.get("id") or "").strip()

        if not provider_payment_id:
            raise HTTPException(502, "O provedor PIX não retornou o ID da cobrança.")

        qr = asaas_request(
            "GET",
            f"/payments/{provider_payment_id}/pixQrCode",
        )

        now = now_ts()

        conn.execute(
            """
            INSERT INTO payments(
                id,
                user_id,
                provider_payment_id,
                route_credits,
                value_cents,
                status,
                pix_payload,
                pix_qr_base64,
                expiration_date,
                created_at,
                updated_at
            )
            VALUES(?,?,?,?,?,'PENDING',?,?,?,?,?)
            """,
            (
                local_payment_id,
                body.user_id,
                provider_payment_id,
                body.route_credits,
                value_cents,
                qr.get("payload"),
                qr.get("encodedImage"),
                qr.get("expirationDate"),
                now,
                now,
            ),
        )
        conn.commit()

    return {
        "payment_id": local_payment_id,
        "provider_payment_id": provider_payment_id,
        "status": "PENDING",
        "route_credits": body.route_credits,
        "value_cents": value_cents,
        "pix_payload": qr.get("payload"),
        "pix_qr_base64": qr.get("encodedImage"),
        "expiration_date": qr.get("expirationDate"),
    }


@app.get("/api/billing/payment/{payment_id}")
def payment_status(payment_id: str):
    with connection() as conn:
        row = conn.execute(
            """
            SELECT id, user_id, route_credits, value_cents, status,
                   pix_payload, pix_qr_base64, expiration_date,
                   created_at, updated_at
            FROM payments
            WHERE id=?
            """,
            (payment_id,),
        ).fetchone()

    if not row:
        raise HTTPException(404, "Pagamento não encontrado.")

    return dict(row)


@app.post("/api/webhooks/asaas")
def asaas_webhook(
    payload: dict[str, Any],
    asaas_access_token: str | None = Header(default=None, alias="asaas-access-token"),
):
    if not ASAAS_WEBHOOK_TOKEN:
        raise HTTPException(503, "Webhook PIX ainda não foi configurado.")

    if asaas_access_token != ASAAS_WEBHOOK_TOKEN:
        raise HTTPException(401, "Webhook não autorizado.")

    event_id = str(payload.get("id") or "").strip()
    event_name = str(payload.get("event") or "").strip()
    payment = payload.get("payment") or {}

    if not event_id:
        raise HTTPException(400, "Evento sem ID.")

    with connection() as conn:
        already = conn.execute(
            "SELECT 1 FROM webhook_events WHERE event_id=?",
            (event_id,),
        ).fetchone()

        if already:
            return {"ok": True, "duplicate": True}

        conn.execute(
            "INSERT INTO webhook_events(event_id, received_at) VALUES(?,?)",
            (event_id, now_ts()),
        )

        if (
            event_name == "PAYMENT_RECEIVED"
            and str(payment.get("billingType") or "") == "PIX"
            and str(payment.get("status") or "") == "RECEIVED"
        ):
            provider_payment_id = str(payment.get("id") or "").strip()

            local = conn.execute(
                """
                SELECT id, user_id, route_credits, status
                FROM payments
                WHERE provider_payment_id=?
                """,
                (provider_payment_id,),
            ).fetchone()

            if local and str(local["status"]) != "RECEIVED":
                conn.execute(
                    """
                    UPDATE payments
                    SET status='RECEIVED', updated_at=?
                    WHERE id=?
                    """,
                    (now_ts(), local["id"]),
                )

                conn.execute(
                    """
                    UPDATE users
                    SET credits = credits + ?, updated_at=?
                    WHERE id=?
                    """,
                    (
                        int(local["route_credits"]),
                        now_ts(),
                        local["user_id"],
                    ),
                )

        conn.commit()

    return {"ok": True}


@app.post("/api/routes/activate")
def activate_route(body: RouteActivation):
    at_code = body.at_code.strip().upper()

    with connection() as conn:
        conn.execute("BEGIN IMMEDIATE")

        user = conn.execute(
            "SELECT credits, blocked FROM users WHERE id=?",
            (body.user_id,),
        ).fetchone()

        if not user:
            conn.rollback()
            raise HTTPException(404, "Usuário não encontrado.")

        if int(user["blocked"] or 0) == 1:
            conn.rollback()
            raise HTTPException(403, "Conta bloqueada.")

        existing = conn.execute(
            """
            SELECT id, activated_at
            FROM activated_routes
            WHERE user_id=? AND at_code=?
            """,
            (body.user_id, at_code),
        ).fetchone()

        if existing:
            conn.commit()
            return {
                "ok": True,
                "already_activated": True,
                "credit_consumed": False,
                "credits": int(user["credits"]),
            }

        credits = int(user["credits"])

        if credits <= 0:
            conn.rollback()
            raise HTTPException(402, "Saldo de rotas insuficiente.")

        conn.execute(
            "UPDATE users SET credits=credits-1, updated_at=? WHERE id=?",
            (now_ts(), body.user_id),
        )

        conn.execute(
            """
            INSERT INTO activated_routes(id, user_id, at_code, activated_at)
            VALUES(?,?,?,?)
            """,
            (
                uuid.uuid4().hex,
                body.user_id,
                at_code,
                now_ts(),
            ),
        )

        remaining = credits - 1
        conn.commit()

    return {
        "ok": True,
        "already_activated": False,
        "credit_consumed": True,
        "credits": remaining,
    }


@app.post("/api/routes/run/start")
def route_run_start(body: RouteRunStart):
    run_id = uuid.uuid4().hex

    with connection() as conn:
        conn.execute(
            """
            INSERT INTO route_runs(id, user_id, at_code, started_at)
            VALUES(?,?,?,?)
            """,
            (
                run_id,
                body.user_id,
                body.at_code.strip().upper(),
                body.started_at,
            ),
        )
        conn.commit()

    return {"ok": True, "run_id": run_id}


@app.post("/api/routes/run/finish")
def route_run_finish(body: RouteRunFinish):
    with connection() as conn:
        existing = conn.execute(
            """
            SELECT id
            FROM route_runs
            WHERE user_id=? AND at_code=? AND started_at=?
            ORDER BY started_at DESC
            LIMIT 1
            """,
            (
                body.user_id,
                body.at_code.strip().upper(),
                body.started_at,
            ),
        ).fetchone()

        if existing:
            run_id = str(existing["id"])
            conn.execute(
                """
                UPDATE route_runs
                SET finished_at=?, distance_meters=?, total_packages=?,
                    delivered_packages=?, occurrences=?
                WHERE id=?
                """,
                (
                    body.finished_at,
                    body.distance_meters,
                    body.total_packages,
                    body.delivered_packages,
                    body.occurrences,
                    run_id,
                ),
            )
        else:
            run_id = uuid.uuid4().hex
            conn.execute(
                """
                INSERT INTO route_runs(
                    id, user_id, at_code, started_at, finished_at,
                    distance_meters, total_packages, delivered_packages, occurrences
                )
                VALUES(?,?,?,?,?,?,?,?,?)
                """,
                (
                    run_id,
                    body.user_id,
                    body.at_code.strip().upper(),
                    body.started_at,
                    body.finished_at,
                    body.distance_meters,
                    body.total_packages,
                    body.delivered_packages,
                    body.occurrences,
                ),
            )

        conn.commit()

    return {"ok": True, "run_id": run_id}


@app.get("/api/admin/dashboard")
def admin_dashboard(
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
):
    require_admin(x_admin_token)

    with connection() as conn:
        users = int(conn.execute("SELECT COUNT(*) FROM users").fetchone()[0])
        active_users = int(
            conn.execute("SELECT COUNT(*) FROM users WHERE blocked=0").fetchone()[0]
        )
        sold_credits = int(
            conn.execute(
                "SELECT COALESCE(SUM(route_credits),0) FROM payments WHERE status='RECEIVED'"
            ).fetchone()[0]
        )
        revenue_cents = int(
            conn.execute(
                "SELECT COALESCE(SUM(value_cents),0) FROM payments WHERE status='RECEIVED'"
            ).fetchone()[0]
        )
        routes = int(
            conn.execute("SELECT COUNT(*) FROM activated_routes").fetchone()[0]
        )

    return {
        "users": users,
        "active_users": active_users,
        "sold_credits": sold_credits,
        "activated_routes": routes,
        "revenue_cents": revenue_cents,
    }


@app.get("/api/admin/users")
def admin_users(
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
):
    require_admin(x_admin_token)

    with connection() as conn:
        rows = conn.execute(
            """
            SELECT id, name, email, mobile_phone, cpf_last4,
                   credits, blocked, created_at, updated_at
            FROM users
            ORDER BY created_at DESC
            LIMIT 500
            """
        ).fetchall()

    return {"items": [dict(row) for row in rows]}


@app.get("/api/admin/payments")
def admin_payments(
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
):
    require_admin(x_admin_token)

    with connection() as conn:
        rows = conn.execute(
            """
            SELECT p.id, p.user_id, u.name, p.route_credits, p.value_cents,
                   p.status, p.created_at, p.updated_at
            FROM payments p
            JOIN users u ON u.id=p.user_id
            ORDER BY p.created_at DESC
            LIMIT 500
            """
        ).fetchall()

    return {"items": [dict(row) for row in rows]}


@app.post("/api/admin/users/{user_id}/credits")
def admin_adjust_credits(
    user_id: str,
    body: CreditAdjustment,
    x_admin_token: str | None = Header(default=None, alias="X-Admin-Token"),
):
    require_admin(x_admin_token)

    with connection() as conn:
        conn.execute("BEGIN IMMEDIATE")
        user = conn.execute(
            "SELECT credits FROM users WHERE id=?",
            (user_id,),
        ).fetchone()

        if not user:
            conn.rollback()
            raise HTTPException(404, "Usuário não encontrado.")

        next_credits = max(0, int(user["credits"]) + body.delta)

        conn.execute(
            "UPDATE users SET credits=?, updated_at=? WHERE id=?",
            (next_credits, now_ts(), user_id),
        )

        conn.execute(
            """
            INSERT INTO credit_adjustments(id, user_id, delta, reason, created_at)
            VALUES(?,?,?,?,?)
            """,
            (
                uuid.uuid4().hex,
                user_id,
                body.delta,
                body.reason.strip(),
                now_ts(),
            ),
        )

        conn.commit()

    return {"ok": True, "credits": next_credits}


@app.get("/r/{token}", response_class=HTMLResponse)
def tracking_page(token: str):
    return f"""<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>Acompanhe sua entrega</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>
html,body{{height:100%;margin:0;font-family:Arial,Helvetica,sans-serif;background:#f7f9fc;color:#101418}}
#map{{height:100%;width:100%;background:#eef2f4}}
.leaflet-control-attribution{{font-size:8px}}
.sheet{{position:absolute;z-index:1000;left:0;right:0;bottom:0;background:white;border-radius:26px 26px 0 0;padding:26px 22px;box-shadow:0 -4px 18px rgba(0,0,0,.08)}}
.status{{color:#16a34a;font-size:14px;font-weight:700;margin-bottom:8px}}
.arrival{{font-size:20px;font-weight:800;margin-bottom:10px}}
.detail{{font-size:14px;line-height:1.4;color:#334155}}
.updated{{font-size:11px;color:#8491a0;margin-top:10px}}
.courier{{width:20px;height:20px;border-radius:50%;background:#f97316;border:4px solid white;box-shadow:0 2px 8px rgba(0,0,0,.25)}}
.destination{{width:22px;height:22px;border-radius:7px;background:#2563eb;border:4px solid white;box-shadow:0 2px 8px rgba(0,0,0,.2)}}
</style>
</head>
<body>
<div id="map"></div>
<div class="sheet">
  <div id="status" class="status">A caminho</div>
  <div id="arrival" class="arrival">Calculando previsão...</div>
  <div id="detail" class="detail">Acompanhe somente a sua entrega.</div>
  <div id="updated" class="updated"></div>
</div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const token={token!r};
const map=L.map('map',{{zoomControl:true}}).setView([-1.4558,-48.4902],13);
L.tileLayer('https://tile.openstreetmap.org/{{z}}/{{x}}/{{y}}.png',{{maxZoom:19,attribution:'© OpenStreetMap'}}).addTo(map);
let courier=null;
let destination=null;
let firstFit=true;

async function refresh(){{
  try{{
    const response=await fetch('/api/track/'+encodeURIComponent(token),{{cache:'no-store'}});
    if(!response.ok) return;
    const d=await response.json();
    const a=[d.courier_lat,d.courier_lon];

    if(!courier){{
      const icon=L.divIcon({{className:'',html:'<div class="courier"></div>',iconSize:[28,28],iconAnchor:[14,14]}});
      courier=L.marker(a,{{icon,zIndexOffset:500}}).addTo(map);
    }}else{{
      courier.setLatLng(a);
    }}

    let b=null;
    if(d.destination_lat!=null && d.destination_lon!=null){{
      b=[d.destination_lat,d.destination_lon];
      if(!destination){{
        const icon=L.divIcon({{className:'',html:'<div class="destination"></div>',iconSize:[30,30],iconAnchor:[15,15]}});
        destination=L.marker(b,{{icon,zIndexOffset:400}}).addTo(map);
      }}else{{
        destination.setLatLng(b);
      }}
    }}

    if(firstFit){{
      firstFit=false;
      if(b) map.fitBounds(L.latLngBounds([a,b]),{{padding:[40,120]}});
      else map.setView(a,16);
    }}

    const remaining=Number(d.remaining_stops||0);
    document.getElementById('status').textContent=
      d.status==='DELIVERED'?'Entregue':
      d.status==='OCCURRENCE'?'Atualização da entrega':
      remaining<=0?'Está chegando':'Em rota';

    document.getElementById('arrival').textContent=
      d.status==='DELIVERED'?'Entrega concluída':
      'Previsão aproximada: '+Math.max(1,Number(d.eta_minutes||1))+' min';

    document.getElementById('detail').textContent=
      d.status==='DELIVERED'?'Obrigado. O acompanhamento foi encerrado.':
      d.status==='OCCURRENCE'?'Existe uma ocorrência registrada para esta entrega.':
      remaining<=0?'Seu endereço é a próxima parada.':
      remaining===1?'Falta 1 parada antes do seu endereço.':
      'Faltam '+remaining+' paradas antes do seu endereço.';

    const seconds=Math.max(0,Math.round(Date.now()/1000-Number(d.updated_at||0)));
    document.getElementById('updated').textContent=
      seconds<12?'Atualizado agora':'Atualizado há '+seconds+' s';
  }}catch(e){{}}
}}

refresh();
setInterval(refresh,5000);
</script>
</body>
</html>"""
