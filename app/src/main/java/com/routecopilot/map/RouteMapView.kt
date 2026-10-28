package com.routecopilot.map

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.routecopilot.data.GeoPoint
import com.routecopilot.data.RouteStop
import com.routecopilot.tracking.TripPoint
import org.json.JSONArray
import org.json.JSONObject

private val MapBackground = Color(0xFFF4F7FB)
private val MapMuted = Color(0xFF64748B)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouteMapView(
    stops: List<RouteStop>,
    coordinates: Map<String, GeoPoint>,
    courierLocation: GeoPoint? = null,
    trackPoints: List<TripPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val mapped = stops.mapNotNull { stop ->
        val point = coordinates[stop.id] ?: return@mapNotNull null
        stop to point
    }

    if (mapped.isEmpty() && courierLocation == null && trackPoints.isEmpty()) {
        Box(
            modifier = modifier.background(MapBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Preparando mapa...",
                color = MapMuted,
                fontSize = 12.sp
            )
        }
        return
    }

    val markersJson = JSONArray().apply {
        mapped.forEach { (stop, point) ->
            put(JSONObject().apply {
                put("id", stop.id)
                put("n", stop.stopNumber)
                put("lat", point.latitude)
                put("lon", point.longitude)
                put("address", stop.address)
                put("bairro", stop.bairro)
                put("occurrence", stop.hasOccurrence)
                put("count", stop.activePackages.size)
            })
        }
    }.toString()

    val trackJson = JSONArray().apply {
        trackPoints.forEach { point ->
            put(
                JSONArray().apply {
                    put(point.latitude)
                    put(point.longitude)
                }
            )
        }
    }.toString()

    val courierJson = courierLocation?.let {
        JSONObject().apply {
            put("lat", it.latitude)
            put("lon", it.longitude)
        }.toString()
    } ?: "null"

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://routecopilot.local/",
                mapHtml(
                    markersJson = markersJson,
                    trackJson = trackJson,
                    courierJson = courierJson
                ),
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

private fun mapHtml(
    markersJson: String,
    trackJson: String,
    courierJson: String
): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=yes">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<style>
html,body,#map{height:100%;width:100%;margin:0;background:#f4f7fb;overflow:hidden}
.leaflet-container{font-family:system-ui,-apple-system,sans-serif;background:#f4f7fb}
.stop-marker{
  width:30px;height:30px;border-radius:50%;display:flex;align-items:center;justify-content:center;
  color:#fff;font-weight:800;font-size:12px;border:3px solid #fff;box-shadow:0 2px 8px rgba(15,23,42,.25)
}
.normal{background:#2563eb}
.occurrence{background:#ef4444}
.courier{
  width:18px;height:18px;border-radius:50%;background:#f97316;border:4px solid #fff;
  box-shadow:0 2px 10px rgba(15,23,42,.32)
}
.hub{
  width:18px;height:18px;border-radius:5px;background:#16a34a;border:3px solid #fff;
  box-shadow:0 2px 8px rgba(15,23,42,.28)
}
.legend{
  position:absolute;z-index:999;left:10px;bottom:10px;background:rgba(255,255,255,.94);
  border-radius:12px;padding:8px 10px;font-size:11px;color:#334155;box-shadow:0 2px 10px rgba(15,23,42,.12)
}
.legend div{margin:2px 0}
.line-blue{display:inline-block;width:18px;height:4px;background:#2563eb;border-radius:4px;margin-right:6px;vertical-align:middle}
.line-orange{display:inline-block;width:18px;height:4px;background:#f97316;border-radius:4px;margin-right:6px;vertical-align:middle}
.leaflet-popup-content-wrapper,.leaflet-popup-tip{background:#fff;color:#0f172a}
</style>
</head>
<body>
<div id="map"></div>
<div class="legend">
  <div><span class="line-blue"></span>Rota planejada</div>
  <div><span class="line-orange"></span>Trajeto realizado</div>
</div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const items=$markersJson;
const track=$trackJson;
const courier=$courierJson;

const map=L.map('map',{zoomControl:true,attributionControl:true});
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
  maxZoom:19,
  attribution:'© OpenStreetMap'
}).addTo(map);

const bounds=[];

items.forEach(item=>{
  const cls=item.occurrence?'occurrence':'normal';
  const icon=L.divIcon({
    className:'',
    html:`<div class="stop-marker ${cls}">${item.n}</div>`,
    iconSize:[30,30],
    iconAnchor:[15,15]
  });

  L.marker([item.lat,item.lon],{icon})
    .addTo(map)
    .bindPopup(
      `<b>Parada ${item.n}</b><br>${escapeHtml(item.address)}<br>${escapeHtml(item.bairro)}<br>${item.count} pedido(s)`
    );

  bounds.push([item.lat,item.lon]);
});

if(track.length>0){
  const hubIcon=L.divIcon({
    className:'',
    html:'<div class="hub"></div>',
    iconSize:[24,24],
    iconAnchor:[12,12]
  });

  L.marker(track[0],{icon:hubIcon})
    .addTo(map)
    .bindPopup('Início da rota / hub');

  L.polyline(track,{
    color:'#f97316',
    weight:5,
    opacity:.92
  }).addTo(map);

  track.forEach(point=>bounds.push(point));
}

if(courier){
  const courierIcon=L.divIcon({
    className:'',
    html:'<div class="courier"></div>',
    iconSize:[26,26],
    iconAnchor:[13,13]
  });

  L.marker([courier.lat,courier.lon],{
    icon:courierIcon,
    zIndexOffset:1000
  }).addTo(map).bindPopup('Motorista');

  bounds.push([courier.lat,courier.lon]);
}

const plannedPoints=items.map(item=>[item.lat,item.lon]);

if(plannedPoints.length>1){
  const chunks=[];
  const chunkSize=20;

  for(let i=0;i<plannedPoints.length-1;i+=chunkSize-1){
    chunks.push(plannedPoints.slice(i,Math.min(i+chunkSize,plannedPoints.length)));
  }

  chunks.forEach(chunk=>{
    const coords=chunk.map(p=>`${p[1]},${p[0]}`).join(';');

    fetch(
      `https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson&steps=false`
    )
      .then(r=>r.ok?r.json():Promise.reject())
      .then(data=>{
        const geometry=data?.routes?.[0]?.geometry;
        if(geometry){
          L.geoJSON(geometry,{
            style:{color:'#2563eb',weight:4,opacity:.76}
          }).addTo(map);
        }else{
          L.polyline(chunk,{
            color:'#2563eb',
            weight:4,
            opacity:.72,
            dashArray:'8 7'
          }).addTo(map);
        }
      })
      .catch(()=>{
        L.polyline(chunk,{
          color:'#2563eb',
          weight:4,
          opacity:.72,
          dashArray:'8 7'
        }).addTo(map);
      });
  });
}

if(bounds.length===1){
  map.setView(bounds[0],16);
}else if(bounds.length>1){
  map.fitBounds(bounds,{padding:[28,28]});
}else{
  map.setView([-1.4558,-48.4902],12);
}

function escapeHtml(str){
  return String(str||'').replace(/[&<>"']/g,m=>({
    '&':'&amp;',
    '<':'&lt;',
    '>':'&gt;',
    '"':'&quot;',
    "'":'&#039;'
  }[m]));
}
</script>
</body>
</html>
""".trimIndent()
