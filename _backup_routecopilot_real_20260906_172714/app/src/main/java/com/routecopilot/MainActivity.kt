package com.routecopilot

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.routecopilot.map.RouteMapScreen
import com.routecopilot.messaging.CustomerMessaging
import com.routecopilot.route.DeliveryStop
import com.routecopilot.route.EtaEngine
import com.routecopilot.route.GeocodingManager
import com.routecopilot.route.RouteOptimizer
import com.routecopilot.route.RouteRepository
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState
import com.routecopilot.tracking.CourierTrackingService
import com.routecopilot.tracking.TrackingConfig
import com.routecopilot.tracking.TrackingClient
import com.routecopilot.ui.theme.RouteCopilotTheme

private val AppBackground = Color(0xFFF7F9FC)
private val AppSurface = Color(0xFFFFFFFF)
private val AppSurfaceSoft = Color(0xFFF1F5F9)
private val AppBlue = Color(0xFF1267E3)
private val AppBlueSoft = Color(0xFFEAF3FF)
private val AppOrange = Color(0xFFFF6A1A)
private val AppOrangeSoft = Color(0xFFFFF1E8)
private val AppGreen = Color(0xFF18A957)
private val AppGreenSoft = Color(0xFFECFDF3)
private val AppText = Color(0xFF0F172A)
private val AppMuted = Color(0xFF64748B)

private enum class Screen {
    HOME,
    SYNC,
    ROUTE,
    MAP,
    MESSAGES
}

class MainActivity : ComponentActivity(), LocationListener {

    private val accessibilityEnabled: MutableState<Boolean> =
        mutableStateOf(false)

    private val courierLat: MutableState<Double?> =
        mutableStateOf(null)

    private val courierLon: MutableState<Double?> =
        mutableStateOf(null)

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            startLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        SpxSessionState.initialize(this)
        RouteRepository.initialize(this)

        accessibilityEnabled.value =
            isAccessibilityServiceEnabled(this)

        requestLocationIfNeeded()

        setContent {
            RouteCopilotTheme {
                RouteCopilotApp(
                    accessibilityEnabled = accessibilityEnabled.value,
                    courierLat = courierLat.value,
                    courierLon = courierLon.value,
                    onStartTracking = {
                        startCourierTracking()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        accessibilityEnabled.value =
            isAccessibilityServiceEnabled(this)

        startLocation()
    }

    private fun requestLocationIfNeeded() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (
            fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
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
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (
            fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        runCatching {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                10f,
                this
            )
        }

        runCatching {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                7000L,
                20f,
                this
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        courierLat.value = location.latitude
        courierLon.value = location.longitude
    }

    private fun startCourierTracking() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (
            fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationIfNeeded()

            Toast.makeText(
                this,
                "Permita a localização para iniciar o rastreamento.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val intent = Intent(
            this,
            CourierTrackingService::class.java
        )

        ContextCompat.startForegroundService(
            this,
            intent
        )

        Toast.makeText(
            this,
            "Rastreamento da rota iniciado.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun RouteCopilotApp(
    accessibilityEnabled: Boolean,
    courierLat: Double?,
    courierLon: Double?,
    onStartTracking: () -> Unit
) {
    val context = LocalContext.current

    val spxState by
        SpxSessionState.state.collectAsState()

    val sessionId by
        SpxSessionState.sessionId.collectAsState()

    val stops by
        RouteRepository.stops.collectAsState()

    var screen by remember {
        mutableStateOf(
            when {
                SpxSessionState.routeReady.value -> Screen.ROUTE
                SpxSessionState.syncActive.value -> Screen.SYNC
                else -> Screen.HOME
            }
        )
    }

    LaunchedEffect(spxState) {
        screen =
            when (spxState) {
                SpxState.ROUTE_READY,
                SpxState.IMPORT_COMPLETE,
                SpxState.RETURNING_TO_COPILOT ->
                    Screen.ROUTE

                SpxState.ERROR ->
                    Screen.SYNC

                SpxState.IDLE ->
                    screen

                else ->
                    if (screen == Screen.HOME) {
                        Screen.SYNC
                    } else {
                        screen
                    }
            }
    }

    LaunchedEffect(
        spxState,
        sessionId
    ) {
        if (spxState == SpxState.ROUTE_READY) {
            GeocodingManager.geocodeMissing(
                context = context,
                onFinished = {
                    val optimized = RouteOptimizer.optimize(
                        stops = RouteRepository.stops.value,
                        startLat = courierLat,
                        startLon = courierLon
                    )

                    RouteRepository.replaceAll(optimized)
                }
            )
        }
    }

    when (screen) {
        Screen.HOME ->
            HomeScreen(
                accessibilityEnabled = accessibilityEnabled,
                hasRoute = stops.isNotEmpty() && SpxSessionState.routeReady.value,
                onStart = {
                    RouteRepository.clear()
                    SpxSessionState.beginImport()
                    screen = Screen.SYNC
                },
                onOpenMessages = {
                    screen = Screen.MESSAGES
                },
                onOpenMap = {
                    screen = Screen.MAP
                }
            )

        Screen.SYNC ->
            SyncScreen(
                accessibilityEnabled = accessibilityEnabled,
                onBack = {
                    SpxSessionState.reset()
                    screen = Screen.HOME
                }
            )

        Screen.ROUTE ->
            RouteScreen(
                stops = stops,
                courierLat = courierLat,
                courierLon = courierLon,
                onOptimize = {
                    val optimized = RouteOptimizer.optimize(
                        stops = stops,
                        startLat = courierLat,
                        startLon = courierLon
                    )
                    RouteRepository.replaceAll(optimized)
                },
                onOpenMap = {
                    screen = Screen.MAP
                },
                onOpenMessages = {
                    screen = Screen.MESSAGES
                },
                onStartTracking = onStartTracking,
                onBackHome = {
                    screen = Screen.HOME
                }
            )

        Screen.MAP ->
            MapScreen(
                stops = stops,
                courierLat = courierLat,
                courierLon = courierLon,
                onBack = {
                    screen = Screen.ROUTE
                }
            )

        Screen.MESSAGES ->
            MessagesScreen(
                stops = stops,
                courierLat = courierLat,
                courierLon = courierLon,
                onStartTracking = onStartTracking,
                onBack = {
                    screen = Screen.ROUTE
                }
            )
    }
}

@Composable
private fun AppScaffold(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 18.dp,
                vertical = 16.dp
            )
    ) {
        content()
    }
}

@Composable
private fun BrandHeader(
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                id = R.drawable.ic_routecopilot_logo
            ),
            contentDescription = "RouteCopilot",
            tint = Color.Unspecified,
            modifier = Modifier
                .width(40.dp)
                .height(40.dp)
        )

        Column {
            Text(
                text = routeCopilotBrand(),
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = AppMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun routeCopilotBrand(): AnnotatedString {
    return buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = AppBlue,
                fontWeight = FontWeight.ExtraBold
            )
        ) {
            append("Route")
        }

        withStyle(
            SpanStyle(
                color = AppOrange,
                fontWeight = FontWeight.ExtraBold
            )
        ) {
            append("Copilot")
        }
    }
}

@Composable
private fun HomeScreen(
    accessibilityEnabled: Boolean,
    hasRoute: Boolean,
    onStart: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current

    AppScaffold {
        BrandHeader(
            "Sua rota mais simples, suas entregas mais rápidas."
        )

        Spacer(Modifier.height(26.dp))

        Text(
            text = "Olá, entregador!",
            color = AppText,
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Vamos juntos para mais um dia de entregas!",
            color = AppMuted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AppSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.ic_box
                        ),
                        contentDescription = null,
                        tint = AppOrange,
                        modifier = Modifier
                            .width(34.dp)
                            .height(34.dp)
                    )

                    Column {
                        Text(
                            text = "SUA ROTA DE HOJE",
                            color = AppMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Pronta para sincronizar",
                            color = AppText,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                PrimaryButton(
                    text = "INICIAR ROTA",
                    icon = R.drawable.ic_play,
                    color = AppBlue,
                    onClick = {
                        if (!accessibilityEnabled) {
                            abrirConfiguracaoAcessibilidade(context)
                        } else {
                            onStart()
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                ActionCard(
                    icon = R.drawable.ic_message,
                    iconColor = AppGreen,
                    title = "Mensagem inicial",
                    subtitle = "Disponível após importar os pedidos",
                    onClick = {
                        if (hasRoute) {
                            onOpenMessages()
                        } else {
                            Toast.makeText(
                                context,
                                "Primeiro sincronize a rota.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                ActionCard(
                    icon = R.drawable.ic_location,
                    iconColor = AppBlue,
                    title = "Mapa da rota RouteCopilot",
                    subtitle = "Sem necessidade de Waze",
                    onClick = {
                        if (hasRoute) {
                            onOpenMap()
                        } else {
                            Toast.makeText(
                                context,
                                "Primeiro sincronize a rota.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Conexões e ferramentas",
            color = AppText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        ConnectionRow(
            icon = R.drawable.ic_spx,
            iconColor = AppOrange,
            title = "SPX conectado",
            subtitle =
                if (accessibilityEnabled) {
                    "Pronto para sincronizar"
                } else {
                    "Ative a acessibilidade"
                },
            ok = accessibilityEnabled
        )

        Spacer(Modifier.height(8.dp))

        ConnectionRow(
            icon = R.drawable.ic_navigation,
            iconColor = AppBlue,
            title = "Mapa RouteCopilot",
            subtitle = "Rota exibida dentro do aplicativo",
            ok = true
        )

        Spacer(Modifier.height(8.dp))

        ConnectionRow(
            icon = R.drawable.ic_whatsapp,
            iconColor = AppGreen,
            title = "Mensagens",
            subtitle = "WhatsApp / compartilhamento preparado",
            ok = true
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SyncScreen(
    accessibilityEnabled: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val state by
        SpxSessionState.state.collectAsState()

    val message by
        SpxSessionState.statusMessage.collectAsState()

    val at by
        SpxSessionState.atCode.collectAsState()

    val count by
        SpxSessionState.packageCount.collectAsState()

    val expected by
        SpxSessionState.totalEsperado.collectAsState()

    val session by
        SpxSessionState.sessionId.collectAsState()

    var openedSession by remember {
        mutableStateOf(-1L)
    }

    LaunchedEffect(
        accessibilityEnabled,
        session,
        state
    ) {
        if (
            accessibilityEnabled &&
            session > 0L &&
            openedSession != session &&
            state != SpxState.ERROR &&
            state != SpxState.ROUTE_READY
        ) {
            openedSession = session

            SpxSessionState.updateState(
                SpxState.OPENING_SPX,
                "Abrindo SPX..."
            )

            abrirSPX(context)
        }
    }

    val step = syncStep(state)
    val progress = (step.toFloat() / 5f).coerceIn(0f, 1f)

    AppScaffold {
        BrandHeader()

        Spacer(Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AppSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.ic_spx
                        ),
                        contentDescription = null,
                        tint = AppOrange,
                        modifier = Modifier.size(32.dp)
                    )

                    Column {
                        Text(
                            text =
                                when (state) {
                                    SpxState.LOGIN_REQUIRED ->
                                        "Login necessário"

                                    SpxState.CONSENT_REQUIRED ->
                                        "Aceite necessário"

                                    SpxState.FACE_CHECK_REQUIRED ->
                                        "Reconhecimento facial"

                                    SpxState.ERROR ->
                                        "Sincronização interrompida"

                                    else ->
                                        "Importando rota"
                                },
                            color = AppText,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Text(
                            text = message,
                            color = AppMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(
                            AppBlueSoft,
                            RoundedCornerShape(99.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .background(
                                AppBlue,
                                RoundedCornerShape(99.dp)
                            )
                    )
                }

                Spacer(Modifier.height(20.dp))

                SyncStepRow(
                    number = "1",
                    title = "Acesso ao SPX",
                    done = step > 1,
                    active = step == 1
                )

                SyncStepRow(
                    number = "2",
                    title = "Localizar Entrega / Em Rota",
                    done = step > 2,
                    active = step == 2
                )

                SyncStepRow(
                    number = "3",
                    title =
                        if (at != null) {
                            "AT identificada"
                        } else {
                            "Identificar AT"
                        },
                    done = step > 3,
                    active = step == 3
                )

                SyncStepRow(
                    number = "4",
                    title =
                        when {
                            expected != null ->
                                "Importar pedidos ($count/$expected)"

                            count > 0 ->
                                "Importar pedidos ($count)"

                            else ->
                                "Importar pedidos"
                        },
                    done = step > 4,
                    active = step == 4
                )

                SyncStepRow(
                    number = "5",
                    title = "Preparar rota RouteCopilot",
                    done = state == SpxState.ROUTE_READY,
                    active = step == 5
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        when (state) {
            SpxState.LOGIN_REQUIRED ->
                InfoCard(
                    title = "Ação necessária",
                    text = "Faça o login normalmente no SPX. Senha e códigos de verificação não são lidos pelo RouteCopilot.",
                    warning = true
                )

            SpxState.CONSENT_REQUIRED ->
                InfoCard(
                    title = "Ação necessária",
                    text = "Confirme o aceite diretamente no SPX.",
                    warning = true
                )

            SpxState.FACE_CHECK_REQUIRED ->
                InfoCard(
                    title = "Ação necessária",
                    text = "Conclua o reconhecimento facial diretamente no SPX.",
                    warning = true
                )

            SpxState.ERROR ->
                InfoCard(
                    title = "Não foi possível concluir",
                    text = message,
                    warning = true
                )

            else ->
                InfoCard(
                    title = "Leitura automática",
                    text = "A tela só avança quando o serviço realmente reconhece uma etapa no SPX.",
                    warning = false
                )
        }

        Spacer(Modifier.height(14.dp))

        if (accessibilityEnabled) {
            PrimaryButton(
                text = "ABRIR SPX",
                icon = R.drawable.ic_spx,
                color = AppBlue,
                onClick = {
                    abrirSPX(context)
                }
            )

            Spacer(Modifier.height(10.dp))
        }

        PrimaryButton(
            text = "VOLTAR / CANCELAR",
            icon = R.drawable.ic_back,
            color = AppSurfaceSoft,
            textColor = AppText,
            onClick = onBack
        )

        Spacer(Modifier.height(16.dp))
    }
}

private fun syncStep(state: SpxState): Int {
    return when (state) {
        SpxState.IDLE,
        SpxState.STARTING_IMPORT,
        SpxState.OPENING_SPX,
        SpxState.CHECKING_SESSION,
        SpxState.LOGIN_REQUIRED,
        SpxState.AUTHENTICATED,
        SpxState.CONSENT_REQUIRED,
        SpxState.FACE_CHECK_REQUIRED,
        SpxState.WAITING_CONTENT ->
            1

        SpxState.FINDING_ROUTE,
        SpxState.OPENING_DELIVERIES,
        SpxState.OPENING_IN_ROUTE,
        SpxState.FINDING_DOWNLOAD_BUTTON,
        SpxState.DOWNLOAD_BUTTON_FOUND,
        SpxState.WAITING_ROUTE_DOWNLOAD,
        SpxState.DOWNLOADING_ROUTE ->
            2

        SpxState.ROUTE_DETECTED ->
            3

        SpxState.READING_ROUTE,
        SpxState.IMPORTING_PACKAGES,
        SpxState.PACKAGE_DETECTED ->
            4

        SpxState.SYNCING_OCCURRENCES,
        SpxState.SYNCING_CLOSED,
        SpxState.VALIDATING_ROUTE,
        SpxState.CALCULATING_ROUTE,
        SpxState.PREPARING_ROUTE,
        SpxState.IMPORT_COMPLETE,
        SpxState.RETURNING_TO_COPILOT,
        SpxState.ROUTE_READY ->
            5

        SpxState.ERROR ->
            1
    }
}

@Composable
private fun RouteScreen(
    stops: List<DeliveryStop>,
    courierLat: Double?,
    courierLon: Double?,
    onOptimize: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenMessages: () -> Unit,
    onStartTracking: () -> Unit,
    onBackHome: () -> Unit
) {
    val context =
        LocalContext.current

    val at by
        SpxSessionState.atCode.collectAsState()

    val date by
        SpxSessionState.dataCarregamento.collectAsState()

    val expected by
        SpxSessionState.totalEsperado.collectAsState()

    val count by
        SpxSessionState.packageCount.collectAsState()

    val occurrenceCount by
        SpxSessionState.occurrenceCount.collectAsState()

    val closedCount by
        SpxSessionState.closedCount.collectAsState()

    val occurrenceDescriptions by
        SpxSessionState.occurrenceDescriptions.collectAsState()

    val total = expected ?: count

    val geocoded = stops.count {
        it.latitude != null && it.longitude != null
    }

    AppScaffold {
        BrandHeader(
            "Acompanhe a rota criada pelo RouteCopilot."
        )

        Spacer(Modifier.height(22.dp))

        Text(
            text = "Rota ativa",
            color = AppText,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Pedidos organizados, mapa interno e estimativa por parada.",
            color = AppMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(14.dp))

        StatsRow(
            inRoute = total,
            occurrences = occurrenceCount,
            closed = closedCount
        )

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = AppSurface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.ic_location
                        ),
                        contentDescription = null,
                        tint = AppBlue,
                        modifier = Modifier
                            .width(32.dp)
                            .height(32.dp)
                    )

                    Column {
                        Text(
                            text = at ?: "AT não identificada",
                            color = AppText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Text(
                            text = "Data: ${date ?: "Não identificada"}",
                            color = AppMuted,
                            fontSize = 12.sp
                        )

                        Text(
                            text =
                                "Pedidos: $count${
                                    if (expected != null) {
                                        " / $expected"
                                    } else {
                                        ""
                                    }
                                }",
                            color = AppMuted,
                            fontSize = 12.sp
                        )

                        Text(
                            text = "Endereços no mapa: $geocoded / ${stops.size}",
                            color = AppMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        PrimaryButton(
            text =
                if (at.isNullOrBlank()) {
                    "AT NÃO IDENTIFICADA"
                } else {
                    "COPIAR AT"
                },
            icon = R.drawable.ic_check,
            color = AppBlue,
            onClick = {
                if (!at.isNullOrBlank()) {
                    val clipboard =
                        context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as ClipboardManager

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "AT RouteCopilot",
                            at
                        )
                    )

                    Toast.makeText(
                        context,
                        "AT copiada: $at",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        Spacer(Modifier.height(14.dp))

        PrimaryButton(
            text = "OTIMIZAR ROTA",
            icon = R.drawable.ic_navigation,
            color = AppOrange,
            onClick = onOptimize
        )

        Spacer(Modifier.height(10.dp))

        ActionCard(
            icon = R.drawable.ic_location,
            iconColor = AppBlue,
            title = "Mapa da rota RouteCopilot",
            subtitle = "Paradas numeradas e trajeto dentro do aplicativo",
            onClick = onOpenMap
        )

        Spacer(Modifier.height(10.dp))

        ActionCard(
            icon = R.drawable.ic_message,
            iconColor = AppGreen,
            title = "Mensagens aos clientes",
            subtitle = "ETA inclui deslocamento + tempo das entregas",
            onClick = onOpenMessages
        )

        Spacer(Modifier.height(10.dp))

        ActionCard(
            icon = R.drawable.ic_play,
            iconColor = AppOrange,
            title = "Iniciar entregas / rastreamento",
            subtitle = "Mantém posição e ETA dos links enviados atualizados",
            onClick = onStartTracking
        )

        Spacer(Modifier.height(18.dp))

        if (occurrenceDescriptions.isNotEmpty()) {
            Text(
                text = "Ocorrências do SPX",
                color = AppText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            occurrenceDescriptions
                .take(5)
                .forEach { description ->
                    InfoCard(
                        title = "Ocorrência",
                        text = description,
                        warning = true
                    )

                    Spacer(Modifier.height(8.dp))
                }

            if (occurrenceDescriptions.size > 5) {
                Text(
                    text = "+ ${occurrenceDescriptions.size - 5} descrições",
                    color = AppOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))
        }

        Text(
            text = "Pedidos (${stops.size})",
            color = AppText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        stops
            .sortedBy {
                it.copilotOrder
                    ?: it.originalOrder
                    ?: Int.MAX_VALUE
            }
            .take(10)
            .forEachIndexed { index, stop ->
                PackageRow(
                    index = stop.copilotOrder ?: index + 1,
                    name = stop.recipient ?: "Não identificado",
                    address = stop.address ?: "Não identificado",
                    neighborhood = stop.neighborhood ?: "Não identificado",
                    next = index == 0
                )

                Spacer(Modifier.height(8.dp))
            }

        if (stops.size > 10) {
            Text(
                text = "+ ${stops.size - 10} pedidos importados",
                color = AppBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = "VOLTAR AO INÍCIO",
            icon = R.drawable.ic_back,
            color = AppSurfaceSoft,
            textColor = AppText,
            onClick = onBackHome
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MapScreen(
    stops: List<DeliveryStop>,
    courierLat: Double?,
    courierLon: Double?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 12.dp
            )
        ) {
            BrandHeader(
                "Mapa da rota criada pelo RouteCopilot."
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = AppSurface
            )
        ) {
            RouteMapScreen(
                stops = stops,
                courierLat = courierLat,
                courierLon = courierLon
            )
        }

        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (
                stops.none {
                    it.latitude != null && it.longitude != null
                }
            ) {
                InfoCard(
                    title = "Endereços não localizados",
                    text = "O SPX importou os pedidos, mas ainda não foi possível obter coordenadas suficientes para desenhar o mapa.",
                    warning = true
                )

                Spacer(Modifier.height(10.dp))
            }

            PrimaryButton(
                text = "VOLTAR À ROTA",
                icon = R.drawable.ic_back,
                color = AppSurfaceSoft,
                textColor = AppText,
                onClick = onBack
            )
        }
    }
}

@Composable
private fun MessagesScreen(
    stops: List<DeliveryStop>,
    courierLat: Double?,
    courierLon: Double?,
    onStartTracking: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val ordered = stops.sortedBy {
        it.copilotOrder
            ?: it.originalOrder
            ?: Int.MAX_VALUE
    }

    val averageService = RouteRepository.averageServiceSeconds(
        TrackingConfig.DEFAULT_SERVICE_SECONDS
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = 18.dp,
                vertical = 12.dp
            )
    ) {
        BrandHeader(
            "Mensagem + ETA individual por pedido."
        )

        Spacer(Modifier.height(12.dp))

        if (TrackingConfig.BASE_URL.isBlank()) {
            InfoCard(
                title = "Link público ainda não publicado",
                text = "As mensagens e o ETA funcionam. Para o cliente abrir o mapa ao vivo, publique o backend incluído no pacote e informe a URL em TrackingConfig.kt.",
                warning = true
            )

            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = ordered,
                key = { it.br }
            ) { stop ->
                val eta = EtaEngine.etaMinutes(
                    orderedStops = ordered,
                    targetBr = stop.br,
                    courierLat = courierLat,
                    courierLon = courierLon,
                    averageServiceSeconds = averageService
                )

                MessageStopCard(
                    stop = stop,
                    eta = eta,
                    onInitial = {
                        RouteRepository.markMessageSent(stop.br)

                        if (
                            courierLat != null &&
                            courierLon != null
                        ) {
                            TrackingClient.update(
                                stop = stop.copy(messageSent = true),
                                courierLat = courierLat,
                                courierLon = courierLon,
                                etaMinutes = eta
                            )
                        }

                        CustomerMessaging.openWhatsAppOrShare(
                            context = context,
                            stop = stop,
                            message = CustomerMessaging.initialMessage(
                                stop,
                                eta
                            )
                        )
                    },
                    onNext = {
                        RouteRepository.markNext(stop.br)
                        RouteRepository.markMessageSent(stop.br)

                        CustomerMessaging.openWhatsAppOrShare(
                            context = context,
                            stop = stop,
                            message = CustomerMessaging.nextStopMessage(
                                stop,
                                eta
                            )
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        PrimaryButton(
            text = "INICIAR RASTREAMENTO",
            icon = R.drawable.ic_play,
            color = AppOrange,
            onClick = onStartTracking
        )

        Spacer(Modifier.height(8.dp))

        PrimaryButton(
            text = "VOLTAR",
            icon = R.drawable.ic_back,
            color = AppSurfaceSoft,
            textColor = AppText,
            onClick = onBack
        )
    }
}

@Composable
private fun MessageStopCard(
    stop: DeliveryStop,
    eta: Int,
    onInitial: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AppSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = "Parada ${stop.copilotOrder ?: stop.originalOrder ?: "-"}",
                color = AppBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Text(
                text = stop.address ?: stop.br,
                color = AppText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            if (!stop.recipient.isNullOrBlank()) {
                Text(
                    text = stop.recipient,
                    color = AppMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Previsão: aproximadamente $eta min",
                color = AppOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Text(
                text = "Inclui tempo das entregas anteriores.",
                color = AppMuted,
                fontSize = 10.sp
            )

            Spacer(Modifier.height(10.dp))

            PrimaryButton(
                text =
                    if (stop.messageSent) {
                        "REENVIAR MENSAGEM"
                    } else {
                        "MENSAGEM INICIAL"
                    },
                icon = R.drawable.ic_message,
                color = AppGreen,
                onClick = onInitial
            )

            Spacer(Modifier.height(8.dp))

            PrimaryButton(
                text = "AVISAR: SUA ENTREGA É A PRÓXIMA",
                icon = R.drawable.ic_navigation,
                color = AppBlue,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun StatsRow(
    inRoute: Int,
    occurrences: Int,
    closed: Int
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val gap = 10.dp
        val cardWidth = (maxWidth - gap - gap) / 3

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            StatCard(
                width = cardWidth,
                icon = R.drawable.ic_delivery,
                value = inRoute.toString(),
                title = "Em rota",
                background = AppBlueSoft,
                accent = AppBlue
            )

            StatCard(
                width = cardWidth,
                icon = R.drawable.ic_warning,
                value = occurrences.toString(),
                title = "Ocorrências",
                background = AppOrangeSoft,
                accent = AppOrange
            )

            StatCard(
                width = cardWidth,
                icon = R.drawable.ic_check,
                value = closed.toString(),
                title = "Encerrados",
                background = AppGreenSoft,
                accent = AppGreen
            )
        }
    }
}

@Composable
private fun StatCard(
    width: Dp,
    icon: Int,
    value: String,
    title: String,
    background: Color,
    accent: Color
) {
    Card(
        modifier = Modifier.width(width),
        colors = CardDefaults.cardColors(
            containerColor = background
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = value,
                color = accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = title,
                color = AppText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    icon: Int,
    color: Color,
    textColor: Color = Color.White,
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
            contentColor = textColor
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = textColor,
            modifier = Modifier
                .width(20.dp)
                .height(20.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionCard(
    icon: Int,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppSurface,
            contentColor = AppText
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .width(27.dp)
                    .height(27.dp)
            )

            Column {
                Text(
                    text = title,
                    color = AppText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Text(
                    text = subtitle,
                    color = AppMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    icon: Int,
    iconColor: Color,
    title: String,
    subtitle: String,
    ok: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AppSurface
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .width(27.dp)
                        .height(27.dp)
                )

                Column {
                    Text(
                        text = title,
                        color = AppText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = subtitle,
                        color = AppMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "●",
                color = if (ok) AppGreen else AppOrange,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun SyncStepRow(
    number: String,
    title: String,
    done: Boolean,
    active: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor =
                    when {
                        done -> AppGreenSoft
                        active -> AppBlueSoft
                        else -> AppSurfaceSoft
                    }
            ),
            shape = RoundedCornerShape(99.dp)
        ) {
            Text(
                text = if (done) "✓" else number,
                color =
                    when {
                        done -> AppGreen
                        active -> AppBlue
                        else -> AppMuted
                    },
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 6.dp
                )
            )
        }

        Column {
            Text(
                text = title,
                color = if (active) AppBlue else AppText,
                fontWeight =
                    if (active) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                fontSize = 14.sp
            )

            Text(
                text =
                    when {
                        done -> "Concluído"
                        active -> "Em andamento"
                        else -> "Aguardando"
                    },
                color =
                    when {
                        done -> AppGreen
                        active -> AppBlue
                        else -> AppMuted
                    },
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    text: String,
    warning: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (warning) {
                    AppOrangeSoft
                } else {
                    AppBlueSoft
                }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp)
        ) {
            Text(
                text = title,
                color = if (warning) AppOrange else AppBlue,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = text,
                color = AppText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PackageRow(
    index: Int,
    name: String,
    address: String,
    neighborhood: String,
    next: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AppSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        if (next) {
                            AppBlue
                        } else {
                            AppSurfaceSoft
                        }
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = index.toString(),
                    color =
                        if (next) {
                            Color.White
                        } else {
                            AppMuted
                        },
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 7.dp
                    )
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "NOME",
                    color = AppMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = name,
                    color = AppText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "END",
                    color = AppMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = address,
                    color = AppText,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "BAIRRO",
                    color = AppMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = neighborhood,
                    color = AppBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                if (next) {
                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = "Próxima entrega",
                        color = AppOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun isAccessibilityServiceEnabled(
    context: Context
): Boolean {
    val manager =
        context.getSystemService(
            Context.ACCESSIBILITY_SERVICE
        ) as AccessibilityManager

    return manager
        .getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        .any { info ->
            val service = info.resolveInfo.serviceInfo

            service.packageName == context.packageName &&
                service.name.contains("SpxAccessibilityService")
        }
}

fun abrirConfiguracaoAcessibilidade(
    context: Context
) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )
    }.onFailure {
        Toast.makeText(
            context,
            "Não foi possível abrir as configurações de acessibilidade.",
            Toast.LENGTH_LONG
        ).show()
    }
}

fun abrirSPX(
    context: Context
) {
    val packageName = "com.shopee.spx.driver.brazil"

    val intent = context.packageManager
        .getLaunchIntentForPackage(packageName)

    if (intent == null) {
        Toast.makeText(
            context,
            "SPX não encontrado neste aparelho.",
            Toast.LENGTH_LONG
        ).show()

        SpxSessionState.fail(
            "SPX não encontrado neste aparelho."
        )

        return
    }

    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
    )

    context.startActivity(intent)
}
