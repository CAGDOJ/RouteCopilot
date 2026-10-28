package com.routecopilot.map

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.routecopilot.route.DeliveryStop
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouteMapScreen(
    stops: List<DeliveryStop>,
    courierLat: Double?,
    courierLon: Double?,
    modifier: Modifier = Modifier
) {
    val points = JSONArray()

    stops
        .sortedBy { it.copilotOrder ?: Int.MAX_VALUE }
        .filter { it.latitude != null && it.longitude != null }
        .forEach { stop ->
            points.put(
                JSONObject().apply {
                    put("lat", stop.latitude)
                    put("lon", stop.longitude)
                    put("order", stop.copilotOrder ?: 0)
                    put("address", stop.address ?: "Parada")
                }
            )
        }

    val courier =
        if (courierLat != null && courierLon != null) {
            """{lat:$courierLat,lon:$courierLon}"""
        } else {
            "null"
        }

    val html = buildHtml(points.toString(), courier)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                loadDataWithBaseURL(
                    "https://routecopilot.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://routecopilot.local/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

private fun buildHtml(
    pointsJson: String,
    courierJson: String
): String =
    """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>
html,body,#map{height:100%;margin:0;background:#F7F9FC}
.leaflet-control-attribution{font-size:8px}
.pin{
  width:30px;height:30px;border-radius:15px;
  background:#1267E3;color:white;
  display:flex;align-items:center;justify-content:center;
  font-weight:800;border:2px solid white;
  box-shadow:0 2px 8px rgba(0,0,0,.28)
}
.courier{
  width:18px;height:18px;border-radius:10px;
  background:#FF6A1A;border:3px solid white;
  box-shadow:0 2px 8px rgba(0,0,0,.28)
}
#summary{
  position:absolute;z-index:999;left:12px;right:12px;top:12px;
  background:white;border-radius:14px;padding:10px 12px;
  box-shadow:0 3px 14px rgba(0,0,0,.18);
  font-family:Arial,sans-serif;font-size:13px;color:#0F172A
}
</style>
</head>
<body>
<div id="summary">Rota RouteCopilot • calculando trajeto...</div>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const stops=$pointsJson;
const courier=$courierJson;
const map=L.map('map',{zoomControl:true});
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
  maxZoom:19,
  attribution:'© OpenStreetMap'
}).addTo(map);

const all=[];
if(courier){
  all.push([courier.lat,courier.lon]);
  const courierIcon=L.divIcon({
    className:'',
    html:'<div class="courier"></div>',
    iconSize:[24,24],
    iconAnchor:[12,12]
  });
  L.marker([courier.lat,courier.lon],{icon:courierIcon})
    .addTo(map)
    .bindPopup('Entregador');
}

stops.forEach(s=>{
  all.push([s.lat,s.lon]);
  const icon=L.divIcon({
    className:'',
    html:'<div class="pin">'+s.order+'</div>',
    iconSize:[30,30],
    iconAnchor:[15,15]
  });
  L.marker([s.lat,s.lon],{icon})
    .addTo(map)
    .bindPopup('<b>Parada '+s.order+'</b><br>'+escapeHtml(s.address));
});

if(all.length>1){
  map.fitBounds(L.latLngBounds(all),{padding:[35,35]});
}else if(all.length===1){
  map.setView(all[0],16);
}else{
  map.setView([-1.4558,-48.4902],12);
  document.getElementById('summary').textContent='Nenhum endereço geocodificado ainda.';
}

async function drawRoadRoute(){
  if(all.length<2) return;

  const chunks=[];
  const chunkSize=20;

  for(let i=0;i<all.length-1;i+=chunkSize-1){
    chunks.push(all.slice(i,Math.min(i+chunkSize,all.length)));
  }

  let totalDistance=0;
  let totalDuration=0;
  let success=0;

  for(const chunk of chunks){
    const coords=chunk.map(p=>p[1]+','+p[0]).join(';');
    const url='https://router.project-osrm.org/route/v1/driving/'+coords+'?overview=full&geometries=geojson&steps=false';

    try{
      const response=await fetch(url);
      if(!response.ok) throw new Error('OSRM');
      const data=await response.json();
      const route=data.routes && data.routes[0];
      if(!route) throw new Error('sem rota');

      L.geoJSON(route.geometry,{
        style:{color:'#1267E3',weight:5,opacity:.85}
      }).addTo(map);

      totalDistance+=route.distance||0;
      totalDuration+=route.duration||0;
      success++;
    }catch(e){
      L.polyline(chunk,{color:'#94A3B8',weight:3,dashArray:'7 7'}).addTo(map);
    }
  }

  if(success>0){
    document.getElementById('summary').textContent=
      stops.length+' paradas • '+(totalDistance/1000).toFixed(1)+' km • ~ '+Math.round(totalDuration/60)+' min de deslocamento';
  }else{
    document.getElementById('summary').textContent=stops.length+' paradas • sequência RouteCopilot';
  }
}

drawRoadRoute();

function escapeHtml(str){
  return String(str).replace(/[&<>"']/g,m=>({
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
