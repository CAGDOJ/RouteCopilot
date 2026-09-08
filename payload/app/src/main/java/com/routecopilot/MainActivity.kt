package com.routecopilot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routecopilot.data.ActivityEntry
import com.routecopilot.data.ClientPreference
import com.routecopilot.data.DeliveryPreferenceType
import com.routecopilot.data.GeoPoint
import com.routecopilot.data.PackageStatus
import com.routecopilot.data.RomaneioFolderStore
import com.routecopilot.data.RomaneioPackage
import com.routecopilot.data.RomaneioRepository
import com.routecopilot.data.RomaneioRoute
import com.routecopilot.data.RomaneioSession
import com.routecopilot.data.RouteLogic
import com.routecopilot.data.RouteRunState
import com.routecopilot.data.RouteStop
import com.routecopilot.map.GeocodingRepository
import com.routecopilot.map.RouteMapView
import com.routecopilot.navigation.WazeLauncher
import com.routecopilot.scanner.PackageScanner
import com.routecopilot.spx.SpxBridge
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxStatus
import com.routecopilot.ui.theme.RouteCopilotTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Background = Color(0xFF08111F)
private val Surface = Color(0xFF111C2E)
private val Surface2 = Color(0xFF162338)
private val Blue = Color(0xFF2563EB)
private val Cyan = Color(0xFF38BDF8)
private val Orange = Color(0xFFF97316)
private val White = Color(0xFFF8FAFC)
private val Muted = Color(0xFF94A3B8)
private val Success = Color(0xFF22C55E)
private val Warning = Color(0xFFF59E0B)
private val Danger = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {

    private val accessibilityEnabled: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        RomaneioSession.initialize(this)
        SpxBridge.refreshPresence(this)
        accessibilityEnabled.value = isAccessibilityServiceEnabled(this)

        setContent {
            RouteCopilotTheme {
                RouteCopilotApp(accessibilityEnabled.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled.value = isAccessibilityServiceEnabled(this)
        SpxBridge.refreshPresence(this)
    }
}

private enum class AppScreen {
    HOME,
    ROUTE_PICKER,
    ROUTE,
    MAP
}

@Composable
private fun RouteCopilotApp(accessibilityEnabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val route by RomaneioSession.route.collectAsState()
    val routes by RomaneioSession.availableRoutes.collectAsState()
    val statuses by RomaneioSession.statuses.collectAsState()
    val preferences by RomaneioSession.preferences.collectAsState()
    val runState by RomaneioSession.runState.collectAsState()
    val pauseReason by RomaneioSession.pauseReason.collectAsState()
    val busy by RomaneioSession.busy.collectAsState()
    val statusText by RomaneioSession.status.collectAsState()
    val error by RomaneioSession.error.collectAsState()
    val lastSync by RomaneioSession.lastSync.collectAsState()
    val activities by RomaneioSession.activities.collectAsState()
    val spxStatus by SpxSessionState.status.collectAsState()

    var screen by remember { mutableStateOf(if (route != null) AppScreen.ROUTE else AppScreen.HOME) }
    var folderUri by remember { mutableStateOf(RomaneioFolderStore.get(context)) }
    var coordinates by remember { mutableStateOf<Map<String, GeoPoint>>(emptyMap()) }
    var geocodeProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var scannedPackage by remember { mutableStateOf<RomaneioPackage?>(null) }

    fun sync() {
        val uri = RomaneioFolderStore.get(context)
        if (uri == null) return
        if (busy) return

        scope.launch {
            RomaneioSession.startLoading("Sincronizando...")
            runCatching {
                RomaneioRepository.syncLatestRoutes(context, uri)
            }.onSuccess { result ->
                RomaneioSession.applySync(result)
                screen = if (result.routes.size > 1) AppScreen.ROUTE_PICKER else AppScreen.ROUTE
            }.onFailure { throwable ->
                RomaneioSession.fail(throwable.message ?: "Não foi possível sincronizar.")
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            RomaneioFolderStore.save(context, uri)
            folderUri = uri
            sync()
        }
    }

    val activeRoute = route
    val activeStops = remember(activeRoute, statuses, coordinates) {
        activeRoute?.let { RouteLogic.buildStops(it, statuses, coordinates) }.orEmpty()
    }

    LaunchedEffect(activeRoute?.routeKey) {
        coordinates = emptyMap()
        geocodeProgress = null
        val loaded = activeRoute ?: return@LaunchedEffect
        val stopsWithoutCoordinates = RouteLogic.buildStops(loaded, statuses)
        if (stopsWithoutCoordinates.isEmpty()) return@LaunchedEffect

        geocodeProgress = 0 to stopsWithoutCoordinates.size
        GeocodingRepository.resolveStops(
            context = context,
            stops = stopsWithoutCoordinates
        ) { done, total, map ->
            coordinates = map
            geocodeProgress = done to total
        }
        geocodeProgress = null
    }

    when (screen) {
        AppScreen.HOME -> HomeScreen(
            spxStatus = spxStatus,
            accessibilityEnabled = accessibilityEnabled,
            hasRoute = route != null,
            route = route,
            statuses = statuses,
            runState = runState,
            lastSync = lastSync,
            busy = busy,
            statusText = statusText,
            error = error,
            onSync = {
                val saved = RomaneioFolderStore.get(context)
                if (saved == null) folderLauncher.launch(null) else sync()
            },
            onContinue = { screen = AppScreen.ROUTE },
            onSpx = {
                if (!accessibilityEnabled) {
                    openAccessibilitySettings(context)
                } else {
                    SpxBridge.connect(context)
                }
            }
        )

        AppScreen.ROUTE_PICKER -> RoutePickerScreen(
            routes = routes,
            onSelect = {
                RomaneioSession.selectRoute(it)
                screen = AppScreen.ROUTE
            },
            onHome = { screen = AppScreen.HOME }
        )

        AppScreen.ROUTE -> RouteScreen(
            route = route,
            statuses = statuses,
            preferences = preferences,
            runState = runState,
            pauseReason = pauseReason,
            stops = activeStops,
            coordinates = coordinates,
            geocodeProgress = geocodeProgress,
            activities = activities,
            spxStatus = spxStatus,
            onHome = { screen = AppScreen.HOME },
            onExpandMap = { screen = AppScreen.MAP },
            onStart = RomaneioSession::startDeliveries,
            onPause = RomaneioSession::pause,
            onResume = RomaneioSession::resume,
            onScan = {
                startRouteScanner(
                    context = context,
                    route = route,
                    onFound = { scannedPackage = it }
                )
            },
            onNavigate = { stop ->
                val pkg = stop.activePackages.firstOrNull()
                if (pkg != null) WazeLauncher.navigate(context, pkg.navigationAddress)
            },
            onRetry = RomaneioSession::retryDelivery,
            onSpx = {
                if (!accessibilityEnabled) openAccessibilitySettings(context)
                else SpxBridge.openForOfficialAction(context)
            }
        )

        AppScreen.MAP -> FullMapScreen(
            route = route,
            stops = activeStops,
            coordinates = coordinates,
            geocodeProgress = geocodeProgress,
            onClose = { screen = AppScreen.ROUTE }
        )
    }

    scannedPackage?.let { pkg ->
        ScanResultDialog(
            pkg = pkg,
            onDismiss = { scannedPackage = null },
            onDelivered = {
                RomaneioSession.markDelivered(pkg.spxTn)
                scannedPackage = null
            },
            onOccurrence = {
                RomaneioSession.markOccurrence(pkg.spxTn)
                scannedPackage = null
            },
            onOfficialSpx = {
                scannedPackage = null
                if (!accessibilityEnabled) openAccessibilitySettings(context)
                else SpxBridge.openForOfficialAction(context)
            }
        )
    }
}

@Composable
private fun HomeScreen(
    spxStatus: SpxStatus,
    accessibilityEnabled: Boolean,
    hasRoute: Boolean,
    route: RomaneioRoute?,
    statuses: Map<String, PackageStatus>,
    runState: RouteRunState,
    lastSync: Long,
    busy: Boolean,
    statusText: String,
    error: String?,
    onSync: () -> Unit,
    onContinue: () -> Unit,
    onSpx: () -> Unit
) {
    val pending = route?.packages?.count {
        val status = statuses[it.spxTn] ?: PackageStatus.PENDING
        status == PackageStatus.PENDING
    } ?: 0
    val occurrences = route?.packages?.count {
        val status = statuses[it.spxTn] ?: PackageStatus.PENDING
        status == PackageStatus.OCCURRENCE || status == PackageStatus.POSSIBLE_OCCURRENCE
    } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(58.dp)
            Spacer(Modifier.size(14.dp))
            Column {
                Text("ROUTE COPILOT", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("Gestão inteligente de rota", color = Muted, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(42.dp))

        SpxStatusCard(
            status = spxStatus,
            accessibilityEnabled = accessibilityEnabled,
            onClick = onSpx
        )

        if (hasRoute && route != null) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text("ÚLTIMA ROTA", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(route.atId, color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "$pending pendentes • $occurrences ocorrências",
                    color = Muted,
                    fontSize = 13.sp
                )
                if (runState == RouteRunState.PAUSED) {
                    Spacer(Modifier.height(6.dp))
                    Text("⏸ Pausada", color = Warning, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else if (runState == RouteRunState.IN_PROGRESS) {
                    Spacer(Modifier.height(6.dp))
                    Text("● Em andamento", color = Success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
            ActionButton("CONTINUAR ROTA", Blue, onContinue)
        }

        Spacer(Modifier.height(if (hasRoute) 12.dp else 28.dp))
        ActionButton(
            text = if (busy) "SINCRONIZANDO..." else "SINCRONIZAR",
            color = Orange,
            enabled = !busy,
            onClick = onSync
        )

        if (busy) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Cyan, strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(statusText, color = Muted, fontSize = 12.sp)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Danger, fontSize = 12.sp)
        }

        if (lastSync > 0L) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Última sincronização: ${formatTime(lastSync)}",
                color = Muted,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.weight(1f))
        Text("⚙ Configurações", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpxStatusCard(
    status: SpxStatus,
    accessibilityEnabled: Boolean,
    onClick: () -> Unit
) {
    val (label, color) = when {
        !accessibilityEnabled -> "Ativar integração" to Warning
        status == SpxStatus.CONNECTED -> "Conectado" to Success
        status == SpxStatus.AUTH_REQUIRED -> "Autenticação necessária" to Warning
        status == SpxStatus.UNAVAILABLE -> "Indisponível" to Danger
        else -> "Verificar conexão" to Cyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("SPX", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(label, color = color, fontSize = 12.sp)
        }
        Text("›", color = Muted, fontSize = 24.sp)
    }
}

@Composable
private fun RoutePickerScreen(
    routes: List<RomaneioRoute>,
    onSelect: (RomaneioRoute) -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
    ) {
        Text("‹ Início", color = Cyan, modifier = Modifier.clickable(onClick = onHome))
        Spacer(Modifier.height(18.dp))
        Text("ROTAS DO DIA", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("AT diferente é mantida como rota separada.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(routes, key = { it.routeKey }) { route ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(16.dp))
                        .clickable { onSelect(route) }
                        .padding(16.dp)
                ) {
                    Text(route.atId, color = White, fontWeight = FontWeight.Bold)
                    Text("${route.packages.size} pedidos • ${route.loadDate ?: "data não identificada"}", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RouteScreen(
    route: RomaneioRoute?,
    statuses: Map<String, PackageStatus>,
    preferences: Map<String, ClientPreference>,
    runState: RouteRunState,
    pauseReason: String,
    stops: List<RouteStop>,
    coordinates: Map<String, GeoPoint>,
    geocodeProgress: Pair<Int, Int>?,
    activities: List<ActivityEntry>,
    spxStatus: SpxStatus,
    onHome: () -> Unit,
    onExpandMap: () -> Unit,
    onStart: () -> Unit,
    onPause: (String) -> Unit,
    onResume: () -> Unit,
    onScan: () -> Unit,
    onNavigate: (RouteStop) -> Unit,
    onRetry: (String) -> Unit,
    onSpx: () -> Unit
) {
    if (route == null) {
        Box(modifier = Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Text("Nenhuma rota carregada.", color = White)
        }
        return
    }

    val pending = route.packages.count {
        (statuses[it.spxTn] ?: PackageStatus.PENDING) == PackageStatus.PENDING
    }
    val occurrences = route.packages.count {
        val status = statuses[it.spxTn] ?: PackageStatus.PENDING
        status == PackageStatus.OCCURRENCE || status == PackageStatus.POSSIBLE_OCCURRENCE
    }
    val activePackages = route.packages.count {
        statuses[it.spxTn] != PackageStatus.DELIVERED
    }
    val nextStop = RouteLogic.nextStop(route, statuses, coordinates)

    var showPauseDialog by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Cyan, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onHome))
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("ROTA", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(route.atId, color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (runState != RouteRunState.IDLE) {
                Text(
                    "📷",
                    fontSize = 28.sp,
                    modifier = Modifier.clickable(onClick = onScan).padding(6.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                RouteSummary(
                    pending = pending,
                    occurrences = occurrences,
                    activePackages = activePackages,
                    stops = stops.size,
                    runState = runState,
                    pauseReason = pauseReason
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(18.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("MAPA DA ROTA", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("⛶ Expandir", color = Cyan, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onExpandMap))
                    }
                    RouteMapView(
                        stops = stops,
                        coordinates = coordinates,
                        modifier = Modifier.fillMaxWidth().height(210.dp).background(Surface2, RoundedCornerShape(14.dp))
                    )
                    geocodeProgress?.let { (done, total) ->
                        Text("Preparando endereços: $done/$total", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }

            if (runState == RouteRunState.IDLE) {
                item {
                    ActionButton("INICIAR ENTREGAS", Orange, onStart)
                }
            } else {
                item {
                    OperationCard(
                        runState = runState,
                        nextStop = nextStop,
                        onScan = onScan,
                        onNavigate = { nextStop?.let(onNavigate) },
                        onPause = { showPauseDialog = true },
                        onResume = onResume
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(14.dp))
                        .clickable { showActivity = !showActivity }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Atividade", color = White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(if (showActivity) "⌃" else "⌄", color = Muted)
                }
                if (showActivity) {
                    activities.take(8).forEach { entry ->
                        Text(
                            text = "${if (entry.isWarning) "⚠" else "✓"} ${entry.text}",
                            color = if (entry.isWarning) Warning else Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            item {
                Text("PEDIDOS", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            items(
                items = route.packages.filter { statuses[it.spxTn] != PackageStatus.DELIVERED },
                key = { it.spxTn }
            ) { pkg ->
                PackageCard(
                    pkg = pkg,
                    status = statuses[pkg.spxTn] ?: PackageStatus.PENDING,
                    preference = preferences[pkg.spxTn],
                    onNavigate = { WazeLauncher.navigate(LocalContext.current, pkg.navigationAddress) },
                    onRetry = { onRetry(pkg.spxTn) }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SPX ${when (spxStatus) { SpxStatus.CONNECTED -> "● conectado"; SpxStatus.AUTH_REQUIRED -> "● autenticação necessária"; SpxStatus.UNAVAILABLE -> "● indisponível"; else -> "○ verificando" }}",
                    color = if (spxStatus == SpxStatus.CONNECTED) Success else Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable(onClick = onSpx).padding(vertical = 8.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showPauseDialog) {
        PauseDialog(
            onDismiss = { showPauseDialog = false },
            onConfirm = {
                onPause(it)
                showPauseDialog = false
            }
        )
    }
}

@Composable
private fun RouteSummary(
    pending: Int,
    occurrences: Int,
    activePackages: Int,
    stops: Int,
    runState: RouteRunState,
    pauseReason: String
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricBox(Modifier.weight(1f), pending.toString(), "Para entregar", Cyan)
            MetricBox(Modifier.weight(1f), occurrences.toString(), "Ocorrências", Orange)
        }
        Spacer(Modifier.height(10.dp))
        Text("$activePackages pedidos ativos • $stops paradas físicas", color = Muted, fontSize = 12.sp)
        when (runState) {
            RouteRunState.IN_PROGRESS -> Text("● EM ROTA", color = Success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            RouteRunState.PAUSED -> Text("⏸ PAUSADA${if (pauseReason.isNotBlank()) " • $pauseReason" else ""}", color = Warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            RouteRunState.IDLE -> Unit
        }
    }
}

@Composable
private fun MetricBox(modifier: Modifier, value: String, label: String, accent: Color) {
    Column(modifier = modifier.background(Surface2, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, color = accent, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun OperationCard(
    runState: RouteRunState,
    nextStop: RouteStop?,
    onScan: () -> Unit,
    onNavigate: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Text("PRÓXIMA PARADA", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (nextStop == null) {
            Text("Nenhuma entrega pendente na sequência principal.", color = White, fontWeight = FontWeight.Bold)
        } else {
            val first = nextStop.activePackages.firstOrNull()
            Text("${nextStop.stopNumber}. ${first?.recipientName?.takeIf { it.isNotBlank() } ?: "Cliente"}", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(nextStop.address, color = Muted, fontSize = 13.sp)
            Text(nextStop.bairro.ifBlank { "Bairro não informado" }, color = Muted, fontSize = 12.sp)
            Text("${nextStop.activePackages.size} pedido(s) nesta parada", color = Cyan, fontSize = 11.sp)
        }

        Spacer(Modifier.height(14.dp))
        if (runState == RouteRunState.PAUSED) {
            ActionButton("RETOMAR", Success, onResume)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(Modifier.weight(1f), "📷 BIPAR", Orange, onScan)
                SmallButton(Modifier.weight(1f), "NAVEGAR", Blue, onNavigate)
                SmallButton(Modifier.weight(1f), "PAUSAR", Surface2, onPause)
            }
        }
    }
}

@Composable
private fun PackageCard(
    pkg: RomaneioPackage,
    status: PackageStatus,
    preference: ClientPreference?,
    onNavigate: () -> Unit,
    onRetry: () -> Unit
) {
    val accent = when (status) {
        PackageStatus.PENDING -> Cyan
        PackageStatus.POSSIBLE_OCCURRENCE, PackageStatus.OCCURRENCE -> Orange
        PackageStatus.DELIVERED -> Success
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text("Nome: ${pkg.recipientName.ifBlank { "Não informado" }}", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("End: ${pkg.destinationAddress.ifBlank { "Não informado" }}", color = White, fontSize = 13.sp)
        Text("Bairro: ${pkg.bairro.ifBlank { "Não informado" }}", color = White, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(pkg.spxTn, color = Muted, fontSize = 11.sp)
        Text(statusLabel(status), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)

        preference?.takeIf { it.type != DeliveryPreferenceType.NONE }?.let { pref ->
            Spacer(Modifier.height(7.dp))
            Text(preferenceLabel(pref), color = Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (pref.hasKeyword && pref.keyword.isNotBlank()) {
                Text("🔑 Palavra-chave: ${pref.keyword}", color = White, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(Modifier.weight(1f), "NAVEGAR", Blue, onNavigate)
            if (status == PackageStatus.OCCURRENCE || status == PackageStatus.POSSIBLE_OCCURRENCE) {
                SmallButton(Modifier.weight(1f), "TENTAR ENTREGA", Surface2, onRetry)
            }
        }
    }
}

@Composable
private fun FullMapScreen(
    route: RomaneioRoute?,
    stops: List<RouteStop>,
    coordinates: Map<String, GeoPoint>,
    geocodeProgress: Pair<Int, Int>?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Cyan, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onClose))
            Spacer(Modifier.size(8.dp))
            Column {
                Text("MAPA DA ROTA", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(route?.atId.orEmpty(), color = Muted, fontSize = 11.sp)
            }
        }

        RouteMapView(
            stops = stops,
            coordinates = coordinates,
            modifier = Modifier.fillMaxSize()
        )

        geocodeProgress?.let { (done, total) ->
            Text("Preparando $done/$total", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PauseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val options = listOf("Almoço", "Abastecimento", "Manutenção", "Pausa operacional")
    var selected by remember { mutableStateOf(options.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pausar rota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    Text(
                        text = if (selected == option) "● $option" else "○ $option",
                        modifier = Modifier.fillMaxWidth().clickable { selected = option }.padding(6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("CONFIRMAR") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("FECHAR") }
        }
    )
}

private fun startRouteScanner(
    context: Context,
    route: RomaneioRoute?,
    onFound: (RomaneioPackage) -> Unit
) {
    val activity = context as? Activity ?: return
    if (route == null) return

    PackageScanner.start(
        activity = activity,
        onResult = { raw ->
            val br = Regex("BR[A-Z0-9]{8,}", RegexOption.IGNORE_CASE)
                .find(raw.replace(" ", ""))
                ?.value
                ?.uppercase()

            if (br == null) {
                Toast.makeText(context, "O código lido não contém um BR válido.", Toast.LENGTH_LONG).show()
                return@start
            }

            val pkg = route.packages.firstOrNull { it.spxTn.equals(br, ignoreCase = true) }
            if (pkg == null) {
                Toast.makeText(context, "Pedido $br não pertence a esta rota.", Toast.LENGTH_LONG).show()
                return@start
            }

            onFound(pkg)
        },
        onError = { message ->
            if (message != "Leitura cancelada.") {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
private fun ScanResultDialog(
    pkg: RomaneioPackage,
    onDismiss: () -> Unit,
    onDelivered: () -> Unit,
    onOccurrence: () -> Unit,
    onOfficialSpx: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✓ Pedido bipado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(pkg.spxTn, fontWeight = FontWeight.Bold)
                Text("Nome: ${pkg.recipientName.ifBlank { "Não informado" }}")
                Text("End: ${pkg.destinationAddress.ifBlank { "Não informado" }}")
                Text("Bairro: ${pkg.bairro.ifBlank { "Não informado" }}")
                Spacer(Modifier.height(6.dp))
                Text("A leitura identifica o pacote. Escolha o resultado da entrega.")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelivered) { Text("ENTREGUE") }
                TextButton(onClick = onOccurrence) { Text("OCORRÊNCIA") }
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onOfficialSpx) { Text("CONFIRMAR NO SPX") }
                TextButton(onClick = onDismiss) { Text("FECHAR") }
            }
        }
    )
}

@Composable
private fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).background(Surface, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("R↗", color = Orange, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = White)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SmallButton(
    modifier: Modifier,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(46.dp),
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun statusLabel(status: PackageStatus): String = when (status) {
    PackageStatus.PENDING -> "PARA ENTREGAR"
    PackageStatus.POSSIBLE_OCCURRENCE -> "POSSÍVEL OCORRÊNCIA"
    PackageStatus.OCCURRENCE -> "OCORRÊNCIA"
    PackageStatus.DELIVERED -> "ENTREGUE"
}

private fun preferenceLabel(pref: ClientPreference): String = when (pref.type) {
    DeliveryPreferenceType.NONE -> ""
    DeliveryPreferenceType.HANDS -> "📝 EM MÃOS"
    DeliveryPreferenceType.NEIGHBOR -> "📝 VIZINHO${pref.neighborName.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""}"
    DeliveryPreferenceType.PORTER -> "📝 PORTARIA"
    DeliveryPreferenceType.PORCH_MAILBOX -> "📝 VARANDA/CAIXA DE CORREIO"
    DeliveryPreferenceType.NOBODY_AVAILABLE -> "⚠ NINGUÉM PARA RECEBER"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(timestamp))

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            val service = info.resolveInfo.serviceInfo
            service.packageName == context.packageName && service.name.contains("SpxAccessibilityService")
        }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir Acessibilidade.", Toast.LENGTH_LONG).show()
    }
}
