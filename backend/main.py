from fastapi import FastAPI, HTTPException
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import sqlite3
import time
from pathlib import Path

DB = Path(__file__).with_name("tracking.db")
app = FastAPI(title="RouteCopilot Tracking")


def connection():
    conn = sqlite3.connect(DB)
    conn.execute(
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
        )
        """
    )

    # Compatibilidade com banco criado por versão anterior.
    columns = {
        row[1]
        for row in conn.execute("PRAGMA table_info(tracking)").fetchall()
    }

    if "remaining_stops" not in columns:
        conn.execute(
            "ALTER TABLE tracking ADD COLUMN remaining_stops INTEGER DEFAULT 0"
        )

    conn.commit()
    return conn


class TrackUpdate(BaseModel):
    courier_lat: float
    courier_lon: float
    destination_lat: float | None = None
    destination_lon: float | None = None
    eta_minutes: int
    remaining_stops: int = 0
    status: str = "PENDING"


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
            int(time.time()),
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

    return {
        "courier_lat": row[0],
        "courier_lon": row[1],
        "destination_lat": row[2],
        "destination_lon": row[3],
        "eta_minutes": row[4],
        "remaining_stops": row[5] or 0,
        "status": row[6],
        "updated_at": row[7],
    }


@app.get("/r/{token}", response_class=HTMLResponse)
def tracking_page(token: str):
    # Essa página recebe somente o token daquela entrega.
    # Não expõe AT, BR, outros clientes ou a rota completa.
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

.back{{
  position:absolute;z-index:1000;top:18px;left:16px;
  width:43px;height:43px;border-radius:24px;background:#fff;
  display:flex;align-items:center;justify-content:center;
  box-shadow:0 2px 10px rgba(0,0,0,.14);
  color:#111;font-size:25px;text-decoration:none
}}

.sheet{{
  position:absolute;z-index:1000;left:0;right:0;bottom:0;
  background:white;border-radius:26px 26px 0 0;
  padding:28px 22px 24px 22px;
  box-shadow:0 -4px 18px rgba(0,0,0,.08)
}}

.handle{{
  width:62px;height:4px;background:#bbb;border-radius:99px;
  position:absolute;top:8px;left:50%;transform:translateX(-50%)
}}

.status{{color:#16a56b;font-size:14px;font-weight:700;margin-bottom:9px}}
.arrival{{font-size:20px;font-weight:800;margin-bottom:10px}}
.detail{{font-size:14px;line-height:1.4;color:#23272b}}
.updated{{font-size:11px;color:#85909a;margin-top:10px}}

.courier-wrap{{width:38px;height:38px;filter:drop-shadow(0 3px 4px rgba(0,0,0,.22))}}
.destination-wrap{{width:32px;height:42px;filter:drop-shadow(0 3px 4px rgba(0,0,0,.18))}}

.loading{{
  position:absolute;z-index:999;top:80px;left:18px;right:18px;
  background:rgba(255,255,255,.94);padding:11px 13px;border-radius:12px;
  box-shadow:0 2px 10px rgba(0,0,0,.08);font-size:13px;color:#53606a
}}
</style>
</head>

<body>
<a class="back" href="javascript:history.back()">←</a>
<div id="loading" class="loading">Localizando o entregador...</div>
<div id="map"></div>

<div class="sheet">
  <div class="handle"></div>
  <div id="status" class="status">Está chegando</div>
  <div id="arrival" class="arrival">Calculando previsão...</div>
  <div id="detail" class="detail">Acompanhe a aproximação do entregador em tempo real.</div>
  <div id="updated" class="updated"></div>
</div>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const token={token!r};

const map=L.map('map',{{zoomControl:false}}).setView([-1.4558,-48.4902],13);
L.tileLayer('https://tile.openstreetmap.org/{{z}}/{{x}}/{{y}}.png',{{
  maxZoom:19,
  attribution:'© OpenStreetMap'
}}).addTo(map);

const orangeBox=`
<div class="courier-wrap">
<svg viewBox="0 0 64 64" width="38" height="38" xmlns="http://www.w3.org/2000/svg">
  <path d="M9 20 31 8l24 12-23 13L9 20Z" fill="#ff9b52" stroke="#5f310d" stroke-width="2"/>
  <path d="M9 20v25l23 12V33L9 20Z" fill="#f97316" stroke="#5f310d" stroke-width="2"/>
  <path d="M55 20v25L32 57V33l23-13Z" fill="#fb8b34" stroke="#5f310d" stroke-width="2"/>
  <path d="M22 14 45 26" stroke="#fff2e8" stroke-width="4"/>
  <path d="M18 35h10v8H18z" fill="#fff" stroke="#5f310d" stroke-width="1.5"/>
</svg>
</div>`;

const blackPin=`
<div class="destination-wrap">
<svg viewBox="0 0 40 52" width="32" height="42" xmlns="http://www.w3.org/2000/svg">
  <path d="M20 1C9.5 1 1 9.5 1 20c0 14.2 19 31 19 31s19-16.8 19-31C39 9.5 30.5 1 20 1Z" fill="#171717" stroke="#fff" stroke-width="2"/>
  <circle cx="20" cy="20" r="6" fill="#fff"/>
</svg>
</div>`;

const courierIcon=L.divIcon({{className:'',html:orangeBox,iconSize:[38,38],iconAnchor:[19,19]}});
const destinationIcon=L.divIcon({{className:'',html:blackPin,iconSize:[32,42],iconAnchor:[16,41]}});

let courier=null;
let destination=null;
let line=null;
let firstFit=true;
let lastRouteFetch=0;
let lastRouteKey='';

function arrivalText(minutes){{
  const dt=new Date(Date.now()+Math.max(1,minutes)*60000);
  const hh=String(dt.getHours()).padStart(2,'0');
  const mm=String(dt.getMinutes()).padStart(2,'0');
  return 'Chegará antes das '+hh+'h'+mm;
}}

function detailText(d){{
  if(d.status==='DELIVERED') return 'Entrega concluída.';
  if(d.status==='OCCURRENCE') return 'A entrega possui uma ocorrência registrada.';

  const n=Number(d.remaining_stops||0);
  if(n<=0) return 'Seu endereço é a próxima parada.';
  if(n===1) return 'Falta 1 parada antes do seu endereço.';
  return 'Faltam '+n+' paradas antes do seu endereço.';
}}

function statusText(d){{
  if(d.status==='DELIVERED') return 'Entregue';
  if(d.status==='OCCURRENCE') return 'Atualização da entrega';
  if(Number(d.remaining_stops||0)<=0) return 'Está chegando';
  return 'Em rota';
}}

function fit(a,b){{
  if(!firstFit) return;
  firstFit=false;

  if(b){{
    map.fitBounds(L.latLngBounds([a,b]),{{paddingTopLeft:[45,85],paddingBottomRight:[45,210]}});
  }}else{{
    map.setView(a,15);
  }}
}}

async function drawRoadRoute(a,b){{
  if(!b) return;

  const now=Date.now();
  const key=a[0].toFixed(4)+','+a[1].toFixed(4)+'|'+b[0].toFixed(4)+','+b[1].toFixed(4);

  // Evita consultar o roteador a cada refresh de 5 s se o entregador quase não se moveu.
  if(key===lastRouteKey && now-lastRouteFetch<15000) return;

  lastRouteKey=key;
  lastRouteFetch=now;

  try{{
    const coords=a[1]+','+a[0]+';'+b[1]+','+b[0];
    const url='https://router.project-osrm.org/route/v1/driving/'+coords+'?overview=full&geometries=geojson&steps=false';
    const response=await fetch(url,{{cache:'no-store'}});
    if(!response.ok) throw new Error('route');

    const data=await response.json();
    const route=data.routes && data.routes[0];
    if(!route) throw new Error('no-route');

    if(line) line.remove();
    line=L.geoJSON(route.geometry,{{
      style:{{color:'#1267E3',weight:5,opacity:.78}}
    }}).addTo(map);
  }}catch(e){{
    if(line) line.remove();
    line=L.polyline([a,b],{{
      color:'#94A3B8',weight:3,opacity:.75,dashArray:'6 7'
    }}).addTo(map);
  }}
}}

async function refresh(){{
  try{{
    const response=await fetch('/api/track/'+encodeURIComponent(token),{{cache:'no-store'}});
    if(!response.ok) return;

    const d=await response.json();
    document.getElementById('loading').style.display='none';

    const a=[d.courier_lat,d.courier_lon];

    if(!courier){{
      courier=L.marker(a,{{icon:courierIcon,zIndexOffset:500}}).addTo(map).bindPopup('Entregador');
    }}else{{
      courier.setLatLng(a);
    }}

    let b=null;
    if(d.destination_lat!=null && d.destination_lon!=null){{
      b=[d.destination_lat,d.destination_lon];

      if(!destination){{
        destination=L.marker(b,{{icon:destinationIcon,zIndexOffset:400}}).addTo(map).bindPopup('Seu endereço');
      }}else{{
        destination.setLatLng(b);
      }}

      await drawRoadRoute(a,b);
    }}

    fit(a,b);

    document.getElementById('status').textContent=statusText(d);
    document.getElementById('arrival').textContent=arrivalText(Number(d.eta_minutes||1));
    document.getElementById('detail').textContent=detailText(d);

    const seconds=Math.max(0,Math.round(Date.now()/1000-Number(d.updated_at||0)));
    document.getElementById('updated').textContent=
      seconds<12 ? 'Atualizado agora' : 'Atualizado há '+seconds+' s';

  }}catch(e){{}}
}}

refresh();
setInterval(refresh,5000);
</script>
</body>
</html>"""


@app.get("/health")
def health():
    return {"ok": True}
