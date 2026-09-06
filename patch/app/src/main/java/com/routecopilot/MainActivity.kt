package com.routecopilot

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.routecopilot.map.RouteMapScreen
import com.routecopilot.messaging.CustomerMessaging
import com.routecopilot.route.*
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState
import com.routecopilot.tracking.TrackingClient
import com.routecopilot.tracking.TrackingConfig
import com.routecopilot.ui.theme.RouteCopilotTheme

private val Bg = Color(0xFF08111F)
private val Card = Color(0xFF111C2E)
private val Card2 = Color(0xFF162338)
private val Blue = Color(0xFF2563EB)
private val Cyan = Color(0xFF38BDF8)
private val Orange = Color(0xFFF97316)
private val White = Color(0xFFF8FAFC)
private val Muted = Color(0xFF94A3B8)
private val Green = Color(0xFF22C55E)
private val Yellow = Color(0xFFF59E0B)

class MainActivity : ComponentActivity(), LocationListener {

    private var currentLat by mutableStateOf<Double?>(null)
    private var currentLon by mutableStateOf<Double?>(null)
    private var accessibilityEnabled by mutableStateOf(false)

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        RouteRepository.initialize(this)
        accessibilityEnabled = isAccessibilityServiceEnabled(this)

        requestLocationIfNeeded()

        setContent {
            RouteCopilotTheme {
                RouteCopilotApp(
                    accessibilityEnabled = accessibilityEnabled,
                    courierLat = currentLat,
                    courierLon = currentLon,
                    openAccessibility = { openAccessibilitySettings() },
                    openSpx = { openSpx() },
                    startSync = {
                        RouteRepository.clear()
                        SpxSessionState.begin()
                        openSpx()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = isAccessibilityServiceEnabled(this)
        startLocation()
    }

    private fun requestLocationIfNeeded() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            startLocation()
        }
    }

    private fun startLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                10f,
                this
            )
        }
        runCatching {
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                7000L,
                20f,
                this
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
    }

    private fun openSpx() {
        val intent = packageManager.getLaunchIntentForPackage("com.shopee.spx.driver.brazil")
        if (intent == null) {
            Toast.makeText(this, "SPX não encontrado neste aparelho.", Toast.LENGTH_LONG).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val s = it.resolveInfo.serviceInfo
                s.packageName == context.packageName &&
                    s.name.contains("SpxAccessibilityService")
            }
    }
}

@Composable
private fun RouteCopilotApp(
    accessibilityEnabled: Boolean,
    courierLat: Double?,
    courierLon: Double?,
    openAccessibility: () -> Unit,
    openSpx: () -> Unit,
    startSync: () -> Unit
) {
    val context = LocalContext.current
    val state by SpxSessionState.state.collectAsState()
    val message by SpxSessionState.message.collectAsState()
    val stops by RouteRepository.stops.collectAsState()

    var screen by remember { mutableStateOf("home") }

    LaunchedEffect(state) {
        if (state == SpxState.ROUTE_READY || state == SpxState.IMPORT_COMPLETE) {
            GeocodingManager.geocodeMissing(context)
            screen = "route"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Bg
    ) {
        when (screen) {
            "home" -> HomeScreen(
                accessibilityEnabled = accessibilityEnabled,
                onStart = {
                    if (accessibilityEnabled) {
                        startSync()
                        screen = "sync"
                    } else {
                        screen = "sync"
                    }
                },
                onRoute = { screen = "route" }
            )

            "sync" -> SyncScreen(
                state = state,
                message = message,
                accessibilityEnabled = accessibilityEnabled,
                count = SpxSessionState.packageCount.collectAsState().value,
                total = SpxSessionState.expectedTotal.collectAsState().value,
                onActivate = openAccessibility,
                onOpenSpx = openSpx,
                onCancel = { screen = "home" }
            )

            "route" -> RouteScreen(
                stops = stops,
                courierLat = courierLat,
                courierLon = courierLon,
                onHome = { screen = "home" },
                onMap = { screen = "map" },
                onOptimize = {
                    val optimized = RouteOptimizer.optimize(stops, courierLat, courierLon)
                    RouteRepository.replaceAll(optimized)
                },
                onMessages = { screen = "messages" }
            )

            "map" -> Column(
                Modifier
                    .fillMaxSize()
                    .background(Bg)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                RouteHeader("MAPA DA ROTA", "Rota criada pelo RouteCopilot")
                Box(Modifier.weight(1f)) {
                    RouteMapScreen(
                        stops = stops,
                        courierLat = courierLat,
                        courierLon = courierLon
                    )
                }
                AppButton("VOLTAR À ROTA", Card2) { screen = "route" }
                Spacer(Modifier.height(12.dp))
            }

            "messages" -> MessagesScreen(
                stops = stops,
                courierLat = courierLat,
                courierLon = courierLon,
                onBack = { screen = "route" }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    accessibilityEnabled: Boolean,
    onStart: () -> Unit,
    onRoute: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(22.dp)
    ) {
        Brand()
        Spacer(Modifier.height(30.dp))

        CardBox {
            SmallLabel("OPERAÇÃO")
            Text("Pronto para iniciar", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (accessibilityEnabled)
                    "Integração SPX ativa."
                else
                    "Ative a integração SPX antes de importar.",
                color = if (accessibilityEnabled) Green else Yellow
            )
        }

        Spacer(Modifier.height(22.dp))
        AppButton("INICIAR / IMPORTAR ROTA", Orange, onStart)
        Spacer(Modifier.height(10.dp))
        AppButton("ROTA ATUAL", Blue, onRoute)
    }
}

@Composable
private fun SyncScreen(
    state: SpxState,
    message: String,
    accessibilityEnabled: Boolean,
    count: Int,
    total: Int?,
    onActivate: () -> Unit,
    onOpenSpx: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(22.dp)
    ) {
        Brand()
        Spacer(Modifier.height(28.dp))

        CardBox {
            SmallLabel("SINCRONIZAÇÃO SPX")
            Text(
                text = when (state) {
                    SpxState.LOGIN_REQUIRED -> "Login necessário"
                    SpxState.CONSENT_REQUIRED -> "Aceite necessário"
                    SpxState.FACE_CHECK_REQUIRED -> "Reconhecimento facial"
                    SpxState.ERROR -> "Não foi possível concluir"
                    else -> "Importando rota"
                },
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = Muted)
            if (count > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (total != null) "$count / $total pedidos" else "$count pedidos encontrados",
                    color = Cyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        if (!accessibilityEnabled) {
            AppButton("ATIVAR INTEGRAÇÃO", Orange, onActivate)
        } else {
            AppButton("ABRIR SPX", Blue, onOpenSpx)
        }
        Spacer(Modifier.height(10.dp))
        AppButton("VOLTAR", Card2, onCancel)
    }
}

@Composable
private fun RouteScreen(
    stops: List<DeliveryStop>,
    courierLat: Double?,
    courierLon: Double?,
    onHome: () -> Unit,
    onMap: () -> Unit,
    onOptimize: () -> Unit,
    onMessages: () -> Unit
) {
    val geoCount = stops.count { it.latitude != null && it.longitude != null }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(22.dp)
    ) {
        RouteHeader("ROTA COPILOT", "${stops.size} pedidos • $geoCount localizados no mapa")

        CardBox {
            SmallLabel("ROTA OTIMIZADA")
            Text(
                if (stops.any { it.copilotOrder != null })
                    "Sequência calculada"
                else
                    "Aguardando otimização",
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "O ETA inclui deslocamento + tempo médio gasto nas entregas anteriores.",
                color = Muted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        AppButton("OTIMIZAR ROTA", Orange, onOptimize)
        Spacer(Modifier.height(10.dp))
        AppButton("MAPA DA ROTA", Blue, onMap)
        Spacer(Modifier.height(10.dp))
        AppButton("MENSAGENS AOS CLIENTES", Card2, onMessages)
        Spacer(Modifier.height(10.dp))
        AppButton("INÍCIO", Card2, onHome)
    }
}

@Composable
private fun MessagesScreen(
    stops: List<DeliveryStop>,
    courierLat: Double?,
    courierLon: Double?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val ordered = stops.sortedBy { it.copilotOrder ?: Int.MAX_VALUE }
    val avgService = RouteRepository.averageServiceSeconds(TrackingConfig.DEFAULT_SERVICE_SECONDS)

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(18.dp)
    ) {
        RouteHeader("MENSAGENS", "ETA com tempo de deslocamento + tempo de entrega")
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ordered, key = { it.br }) { stop ->
                val eta = EtaEngine.etaMinutes(
                    orderedStops = ordered,
                    targetBr = stop.br,
                    courierLat = courierLat,
                    courierLon = courierLon,
                    averageServiceSeconds = avgService
                )

                CardBox {
                    Text(
                        "Parada ${stop.copilotOrder ?: "-"}",
                        color = Cyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stop.address ?: "Endereço ainda não identificado",
                        color = White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Previsão: ~ $eta min", color = Muted)
                    Spacer(Modifier.height(10.dp))
                    AppButton("ENVIAR MENSAGEM", Blue) {
                        val msg = CustomerMessaging.initialMessage(stop, eta)
                        CustomerMessaging.openShare(context, stop, msg)
                    }

                    if (courierLat != null && courierLon != null) {
                        TrackingClient.update(stop, courierLat, courierLon, eta)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AppButton("VOLTAR", Card2, onBack)
    }
}

@Composable
private fun RouteHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Brand() {
    Row {
        Text("Route", color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Copilot", color = Orange, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun AppButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = White
        )
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
