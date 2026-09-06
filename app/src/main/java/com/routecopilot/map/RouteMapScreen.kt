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
            points.put(JSONObject().apply {
                put("lat", stop.latitude)
                put("lon", stop.longitude)
                put("order", stop.copilotOrder ?: 0)
                put("address", stop.address ?: "Parada")
            })
        }

    val courier = if (courierLat != null && courierLon != null) {
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

private fun buildHtml(pointsJson: String, courierJson: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>
html,body,#map{height:100%;margin:0;background:#08111F}
.leaflet-control-attribution{font-size:9px}
.pin{
 width:30px;height:30px;border-radius:15px;background:#F97316;color:#fff;
 display:flex;align-items:center;justify-content:center;font-weight:800;
 border:2px solid #fff;box-shadow:0 2px 8px #0008
}
.courier{
 width:18px;height:18px;border-radius:9px;background:#38BDF8;
 border:3px solid white;box-shadow:0 2px 8px #0008
}
</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const stops = $pointsJson;
const courier = $courierJson;
const map = L.map('map',{zoomControl:true});
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
 maxZoom:19, attribution:'© OpenStreetMap'
}).addTo(map);

const latlngs=[];
stops.forEach(s=>{
 const icon=L.divIcon({className:'',html:'<div class="pin">'+s.order+'</div>',iconSize:[30,30],iconAnchor:[15,15]});
 L.marker([s.lat,s.lon],{icon}).addTo(map).bindPopup('<b>Parada '+s.order+'</b><br>'+escapeHtml(s.address));
 latlngs.push([s.lat,s.lon]);
});
if(courier){
 const icon=L.divIcon({className:'',html:'<div class="courier"></div>',iconSize:[24,24],iconAnchor:[12,12]});
 L.marker([courier.lat,courier.lon],{icon}).addTo(map).bindPopup('Você');
 latlngs.unshift([courier.lat,courier.lon]);
}
if(latlngs.length>1){
 L.polyline(latlngs,{color:'#38BDF8',weight:5,opacity:.85}).addTo(map);
 map.fitBounds(L.latLngBounds(latlngs),{padding:[30,30]});
}else if(latlngs.length===1){
 map.setView(latlngs[0],16);
}else{
 map.setView([-1.4558,-48.4902],12);
}
function escapeHtml(str){
 return String(str).replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[m]));
}
</script>
</body>
</html>
""".trimIndent()
