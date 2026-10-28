package com.routecopilot.map

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.routecopilot.data.GeoPoint
import com.routecopilot.data.PackageStatus
import com.routecopilot.data.RouteStop
import org.json.JSONArray
import org.json.JSONObject

private val MapBackground = Color(0xFF0B1526)
private val MapMuted = Color(0xFF94A3B8)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouteMapView(
    stops: List<RouteStop>,
    coordinates: Map<String, GeoPoint>,
    modifier: Modifier = Modifier
) {
    val mapped = stops.mapNotNull { stop ->
        val point = coordinates[stop.id] ?: return@mapNotNull null
        stop to point
    }

    if (mapped.isEmpty()) {
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
            val occurrence = stop.hasOccurrence
            put(JSONObject().apply {
                put("id", stop.id)
                put("n", stop.stopNumber)
                put("lat", point.latitude)
                put("lon", point.longitude)
                put("address", stop.address)
                put("bairro", stop.bairro)
                put("occurrence", occurrence)
                put("count", stop.activePackages.size)
            })
        }
    }.toString()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://routecopilot.local/",
                mapHtml(markersJson),
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

private fun mapHtml(markersJson: String): String = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=yes">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<style>
html,body,#map{height:100%;width:100%;margin:0;background:#0b1526;overflow:hidden}
.leaflet-container{font-family:system-ui,-apple-system,sans-serif;background:#0b1526}
.stop-marker{width:30px;height:30px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:12px;border:3px solid #fff;box-shadow:0 2px 8px rgba(0,0,0,.35)}
.normal{background:#2563eb}.occurrence{background:#f97316}
.leaflet-popup-content-wrapper,.leaflet-popup-tip{background:#111c2e;color:#f8fafc}
</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const items=$markersJson;
const map=L.map('map',{zoomControl:true,attributionControl:true});
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
const points=[];
items.forEach(item=>{
  const cls=item.occurrence?'occurrence':'normal';
  const icon=L.divIcon({className:'',html:`<div class="stop-marker ${'$'}{cls}">${'$'}{item.n}</div>`,iconSize:[30,30],iconAnchor:[15,15]});
  L.marker([item.lat,item.lon],{icon}).addTo(map)
    .bindPopup(`<b>Parada ${'$'}{item.n}</b><br>${'$'}{item.address}<br>${'$'}{item.bairro}<br>${'$'}{item.count} pedido(s)`);
  points.push([item.lat,item.lon]);
});
if(points.length===1){map.setView(points[0],16);} else {map.fitBounds(points,{padding:[22,22]});}
if(points.length>1){
  const coords=points.map(p=>`${'$'}{p[1]},${'$'}{p[0]}`).join(';');
  fetch(`https://router.project-osrm.org/route/v1/driving/${'$'}{coords}?overview=full&geometries=geojson`)
    .then(r=>r.ok?r.json():Promise.reject())
    .then(data=>{
      const geometry=data?.routes?.[0]?.geometry;
      if(geometry){L.geoJSON(geometry,{style:{color:'#38bdf8',weight:4,opacity:.85}}).addTo(map);}
      else{L.polyline(points,{color:'#38bdf8',weight:4,opacity:.8}).addTo(map);}
    })
    .catch(()=>L.polyline(points,{color:'#38bdf8',weight:4,opacity:.8}).addTo(map));
}
</script>
</body>
</html>
""".trimIndent()
