from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import sqlite3
import time
from pathlib import Path

DB = Path(__file__).with_name("tracking.db")
app = FastAPI(title="RouteCopilot Tracking")

def db():
    conn = sqlite3.connect(DB)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS tracking (
            token TEXT PRIMARY KEY,
            courier_lat REAL,
            courier_lon REAL,
            destination_lat REAL,
            destination_lon REAL,
            eta_minutes INTEGER,
            status TEXT,
            updated_at INTEGER
        )
    """)
    conn.commit()
    return conn

class TrackUpdate(BaseModel):
    courier_lat: float
    courier_lon: float
    destination_lat: float | None = None
    destination_lon: float | None = None
    eta_minutes: int
    status: str = "PENDING"

@app.post("/api/track/{token}")
def update_tracking(token: str, body: TrackUpdate):
    conn = db()
    conn.execute("""
        INSERT INTO tracking(
            token,courier_lat,courier_lon,destination_lat,destination_lon,
            eta_minutes,status,updated_at
        ) VALUES(?,?,?,?,?,?,?,?)
        ON CONFLICT(token) DO UPDATE SET
            courier_lat=excluded.courier_lat,
            courier_lon=excluded.courier_lon,
            destination_lat=excluded.destination_lat,
            destination_lon=excluded.destination_lon,
            eta_minutes=excluded.eta_minutes,
            status=excluded.status,
            updated_at=excluded.updated_at
    """, (
        token, body.courier_lat, body.courier_lon,
        body.destination_lat, body.destination_lon,
        body.eta_minutes, body.status, int(time.time())
    ))
    conn.commit()
    conn.close()
    return {"ok": True}

@app.get("/api/track/{token}")
def get_tracking(token: str):
    conn = db()
    row = conn.execute("""
        SELECT courier_lat,courier_lon,destination_lat,destination_lon,
               eta_minutes,status,updated_at
        FROM tracking WHERE token=?
    """, (token,)).fetchone()
    conn.close()
    if not row:
        raise HTTPException(404, "tracking not found")
    return {
        "courier_lat": row[0],
        "courier_lon": row[1],
        "destination_lat": row[2],
        "destination_lon": row[3],
        "eta_minutes": row[4],
        "status": row[5],
        "updated_at": row[6],
    }

@app.get("/r/{token}", response_class=HTMLResponse)
def tracking_page(token: str):
    # A página recebe só o token daquela parada. Não expõe AT, BR, telefone,
    # outras paradas nem a rota completa.
    return f"""<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Sua entrega</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>
body{{margin:0;background:#08111F;color:#F8FAFC;font-family:Arial,sans-serif}}
header{{padding:18px}}
#eta{{font-size:28px;font-weight:800}}
#status{{color:#94A3B8;margin-top:4px}}
#map{{height:72vh}}
</style>
</head>
<body>
<header>
<div style="color:#38BDF8;font-weight:800">ROUTECOPILOT</div>
<div id="eta">Carregando...</div>
<div id="status">Sua entrega está a caminho</div>
</header>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const token={token!r};
const map=L.map('map').setView([-1.4558,-48.4902],13);
L.tileLayer('https://tile.openstreetmap.org/{{z}}/{{x}}/{{y}}.png',{{maxZoom:19,attribution:'© OpenStreetMap'}}).addTo(map);
let courier=null,destination=null,line=null;
async function refresh(){{
  try{{
    const r=await fetch('/api/track/'+encodeURIComponent(token),{{cache:'no-store'}});
    if(!r.ok) return;
    const d=await r.json();
    document.getElementById('eta').textContent='Previsão: ~ '+d.eta_minutes+' min';
    document.getElementById('status').textContent =
      d.status==='DELIVERED' ? 'Entrega concluída' :
      d.status==='OCCURRENCE' ? 'Não foi possível concluir a entrega' :
      'Sua entrega está a caminho';
    const a=[d.courier_lat,d.courier_lon];
    if(!courier) courier=L.marker(a).addTo(map).bindPopup('Entregador');
    else courier.setLatLng(a);
    if(d.destination_lat!=null && d.destination_lon!=null){{
      const b=[d.destination_lat,d.destination_lon];
      if(!destination) destination=L.marker(b).addTo(map).bindPopup('Sua parada');
      else destination.setLatLng(b);
      if(line) line.remove();
      line=L.polyline([a,b],{{color:'#38BDF8',weight:5}}).addTo(map);
      map.fitBounds(L.latLngBounds([a,b]),{{padding:[40,40]}});
    }} else {{
      map.setView(a,15);
    }}
  }}catch(e){{}}
}}
refresh();
setInterval(refresh,10000);
</script>
</body>
</html>"""

@app.get("/health")
def health():
    return {"ok": True}
