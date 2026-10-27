package com.routecopilot

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.routecopilot.location.DriverLocationService
import com.routecopilot.navigation.WhatsAppLauncher
import com.routecopilot.navigation.WazeLauncher
import com.routecopilot.route.ActiveRoute
import com.routecopilot.route.CustomerQueuePlanner
import com.routecopilot.route.RouteGeocoder
import com.routecopilot.route.RoutePackage
import com.routecopilot.route.RouteRepository
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState

private val RcBlue =
    Color(0xFF1565D8)

private val RcOrange =
    Color(0xFFFF6A1A)

private val RcGreen =
    Color(0xFF159447)

private val RcText =
    Color(0xFF172033)

private val RcMuted =
    Color(0xFF68758A)

private val RcBackground =
    Color(0xFFF7F9FC)

private val RcSurface =
    Color.White

private val RcSoftBlue =
    Color(0xFFEAF3FF)

private val RcSoftOrange =
    Color(0xFFFFF0E7)

private enum class AppScreen {
    HOME,
    SYNC,
    ROUTE,
    MAP
}

class MainActivity : ComponentActivity() {

    private val accessibilityEnabled:
        MutableState<Boolean> =
        mutableStateOf(
            false
        )

    private val openRouteRequested:
        MutableState<Boolean> =
        mutableStateOf(
            false
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        RouteRepository.initialize(
            applicationContext
        )

        SpxSessionState.initialize(
            applicationContext
        )

        enableEdgeToEdge()

        accessibilityEnabled.value =
            isAccessibilityServiceEnabled(
                this
            )

        handleIntent(
            intent
        )

        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary =
                            RcBlue,
                        secondary =
                            RcOrange,
                        background =
                            RcBackground,
                        surface =
                            RcSurface,
                        onPrimary =
                            Color.White,
                        onSecondary =
                            Color.White,
                        onBackground =
                            RcText,
                        onSurface =
                            RcText
                    )
            ) {
                RouteCopilotApp(
                    accessibilityEnabled =
                        accessibilityEnabled.value,
                    forceRoute =
                        openRouteRequested.value,
                    onRouteConsumed = {
                        openRouteRequested.value =
                            false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        accessibilityEnabled.value =
            isAccessibilityServiceEnabled(
                this
            )
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        handleIntent(
            intent
        )
    }

    private fun handleIntent(
        intent: Intent?
    ) {
        if (
            intent
                ?.getBooleanExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    false
                ) ==
            true
        ) {
            openRouteRequested.value =
                true
        }
    }
}

@Composable
private fun RouteCopilotApp(
    accessibilityEnabled: Boolean,
    forceRoute: Boolean,
    onRouteConsumed: () -> Unit
) {
    val spxState by
        SpxSessionState
            .state
            .collectAsState()

    val activeRoute by
        RouteRepository
            .activeRoute
            .collectAsState()

    var screen by
        remember {
            mutableStateOf(
                if (
                    activeRoute?.ready ==
                    true
                ) {
                    AppScreen.ROUTE
                } else {
                    AppScreen.HOME
                }
            )
        }

    LaunchedEffect(
        forceRoute,
        activeRoute?.updatedAt,
        spxState
    ) {
        if (
            forceRoute &&
            activeRoute != null
        ) {
            screen =
                AppScreen.ROUTE

            onRouteConsumed()
        }

        if (
            spxState ==
            SpxState.ROUTE_READY ||
            spxState ==
            SpxState.IMPORT_PARTIAL ||
            spxState ==
            SpxState.IMPORT_COMPLETE
        ) {
            if (
                activeRoute != null
            ) {
                screen =
                    AppScreen.ROUTE
            }
        }
    }

    when (screen) {
        AppScreen.HOME ->
            HomeScreen(
                accessibilityEnabled =
                    accessibilityEnabled,
                activeRoute =
                    activeRoute,
                onStart = {
                    SpxSessionState
                        .beginImport()

                    screen =
                        AppScreen.SYNC
                },
                onOpenRoute = {
                    screen =
                        AppScreen.ROUTE
                }
            )

        AppScreen.SYNC ->
            SyncScreen(
                accessibilityEnabled =
                    accessibilityEnabled,
                onBack = {
                    SpxSessionState
                        .resetUi()

                    screen =
                        AppScreen.HOME
                }
            )

        AppScreen.ROUTE ->
            RouteScreen(
                route =
                    activeRoute,
                onMap = {
                    screen =
                        AppScreen.MAP
                },
                onHome = {
                    screen =
                        AppScreen.HOME
                },
                onResync = {
                    SpxSessionState
                        .beginImport()

                    screen =
                        AppScreen.SYNC
                }
            )

        AppScreen.MAP ->
            RouteMapScreen(
                route =
                    activeRoute,
                onBack = {
                    screen =
                        AppScreen.ROUTE
                }
            )
    }
}

@Composable
private fun Page(
    content: @Composable () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    RcBackground
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        18.dp,
                    vertical =
                        18.dp
                )
    ) {
        content()
    }
}

@Composable
private fun BrandHeader(
    subtitle: String? =
        null
) {
    Column {
        Row(
            horizontalArrangement =
                Arrangement
                    .spacedBy(
                        3.dp
                    )
        ) {
            Text(
                text =
                    "Route",
                color =
                    RcBlue,
                fontSize =
                    27.sp
            )

            Text(
                text =
                    "Copilot",
                color =
                    RcOrange,
                fontSize =
                    27.sp
            )
        }

        if (
            !subtitle
                .isNullOrBlank()
        ) {
            Text(
                text =
                    subtitle,
                color =
                    RcMuted,
                fontSize =
                    12.sp
            )
        }
    }
}

@Composable
private fun HomeScreen(
    accessibilityEnabled: Boolean,
    activeRoute: ActiveRoute?,
    onStart: () -> Unit,
    onOpenRoute: () -> Unit
) {
    val context =
        LocalContext.current

    Page {
        BrandHeader(
            "Entregas mais rápidas, com menos espera."
        )

        Spacer(
            Modifier.height(
                26.dp
            )
        )

        Text(
            text =
                "Operação de hoje",
            color =
                RcText,
            fontSize =
                25.sp
        )

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        RcCard {
            Text(
                text =
                    if (
                        activeRoute ==
                        null
                    ) {
                        "Nenhuma rota ativa"
                    } else {
                        activeRoute.at
                    },
                color =
                    RcText,
                fontSize =
                    20.sp
            )

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            Text(
                text =
                    if (
                        activeRoute ==
                        null
                    ) {
                        "Sincronize sua AT no SPX para começar."
                    } else {
                        "${activeRoute.packages.size} pedidos salvos no aparelho"
                    },
                color =
                    RcMuted,
                fontSize =
                    13.sp
            )

            if (
                activeRoute
                    ?.partial ==
                true
            ) {
                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Text(
                    text =
                        "⚠ Importação parcial — revise antes de iniciar.",
                    color =
                        RcOrange,
                    fontSize =
                        12.sp
                )
            }

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            PrimaryButton(
                text =
                    "INICIAR NOVA ROTA",
                color =
                    RcBlue,
                onClick = {
                    if (
                        !accessibilityEnabled
                    ) {
                        abrirConfiguracaoAcessibilidade(
                            context
                        )
                    } else {
                        onStart()
                    }
                }
            )

            if (
                activeRoute !=
                null
            ) {
                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                SecondaryButton(
                    text =
                        "ABRIR ROTA SALVA",
                    onClick =
                        onOpenRoute
                )
            }
        }

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        InfoCard(
            title =
                "SPX",
            text =
                if (
                    accessibilityEnabled
                ) {
                    "Integração ativa. Login, aceite e reconhecimento facial continuam sendo feitos no próprio SPX."
                } else {
                    "Ative a integração uma vez nas configurações de acessibilidade."
                },
            accent =
                if (
                    accessibilityEnabled
                ) {
                    RcGreen
                } else {
                    RcOrange
                }
        )
    }
}

@Composable
private fun SyncScreen(
    accessibilityEnabled: Boolean,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val state by
        SpxSessionState
            .state
            .collectAsState()

    val message by
        SpxSessionState
            .statusMessage
            .collectAsState()

    val count by
        SpxSessionState
            .packageCount
            .collectAsState()

    val route by
        RouteRepository
            .activeRoute
            .collectAsState()

    var opened by
        remember {
            mutableStateOf(
                false
            )
        }

    LaunchedEffect(
        accessibilityEnabled,
        opened
    ) {
        if (
            accessibilityEnabled &&
            !opened
        ) {
            opened =
                true

            SpxSessionState
                .update(
                    SpxState.OPENING_SPX,
                    "Abrindo SPX..."
                )

            abrirSPX(
                context
            )
        }
    }

    Page {
        BrandHeader(
            "Sincronização automática da rota"
        )

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        RcCard {
            Text(
                text =
                    "Sincronizando",
                color =
                    RcText,
                fontSize =
                    24.sp
            )

            Spacer(
                Modifier.height(
                    6.dp
                )
            )

            Text(
                text =
                    message,
                color =
                    RcMuted,
                fontSize =
                    14.sp
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            7.dp
                        ),
                color =
                    RcBlue,
                trackColor =
                    RcSoftBlue
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                text =
                    "AT: ${
                        route?.at
                            ?: "aguardando"
                    }",
                color =
                    RcText
            )

            Text(
                text =
                    if (
                        route
                            ?.expectedTotal !=
                        null
                    ) {
                        "Pedidos: $count / ${route?.expectedTotal}"
                    } else {
                        "Pedidos encontrados: $count"
                    },
                color =
                    RcText
            )
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        if (
            state ==
            SpxState.LOGIN_REQUIRED ||
            state ==
            SpxState.CONSENT_REQUIRED ||
            state ==
            SpxState.FACE_CHECK_REQUIRED
        ) {
            InfoCard(
                title =
                    "Ação necessária no SPX",
                text =
                    message,
                accent =
                    RcOrange
            )
        } else {
            InfoCard(
                title =
                    "Sincronização assistida",
                text =
                    "O Copilot identifica a AT, importa os BRs e volta sozinho. Se não conseguir confirmar o final da lista em até 3 minutos, volta com a rota marcada como parcial em vez de ficar preso no SPX.",
                accent =
                    RcBlue
            )
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        SecondaryButton(
            text =
                "ABRIR SPX",
            onClick = {
                abrirSPX(
                    context
                )
            }
        )

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        SecondaryButton(
            text =
                "CANCELAR / VOLTAR",
            onClick =
                onBack
        )
    }
}

@Composable
private fun RouteScreen(
    route: ActiveRoute?,
    onMap: () -> Unit,
    onHome: () -> Unit,
    onResync: () -> Unit
) {
    val context =
        LocalContext.current

    val driverLocation by
        RouteRepository
            .driverLocation
            .collectAsState()

    val queue =
        CustomerQueuePlanner
            .compute(
                route
            )

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) { grants ->
            val granted =
                grants[
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ] ==
                true ||
                    grants[
                        Manifest.permission
                            .ACCESS_COARSE_LOCATION
                    ] ==
                    true

            if (granted) {
                startDriverLocation(
                    context
                )
            } else {
                Toast.makeText(
                    context,
                    "A localização é necessária para o mapa e ETA da rota.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    RcBackground
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        start =
                            18.dp,
                        end =
                            18.dp,
                        top =
                            18.dp,
                        bottom =
                            110.dp
                    )
        ) {
            BrandHeader(
                "Sua rota e seus clientes em um só lugar."
            )

            Spacer(
                Modifier.height(
                    20.dp
                )
            )

            if (
                route ==
                null
            ) {
                InfoCard(
                    title =
                        "Nenhuma rota ativa",
                    text =
                        "Sincronize uma AT no SPX.",
                    accent =
                        RcOrange
                )

                return@Column
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement
                        .SpaceBetween
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(
                                1f
                            )
                ) {
                    Text(
                        text =
                            route.at,
                        color =
                            RcText,
                        fontSize =
                            22.sp
                    )

                    Text(
                        text =
                            "Carregamento: ${
                                route.loadingDate
                                    ?: "não identificado"
                            }",
                        color =
                            RcMuted,
                        fontSize =
                            12.sp
                    )
                }

                StatusPill(
                    text =
                        if (
                            route.partial
                        ) {
                            "PARCIAL"
                        } else {
                            "PRONTA"
                        },
                    color =
                        if (
                            route.partial
                        ) {
                            RcOrange
                        } else {
                            RcGreen
                        }
                )
            }

            Spacer(
                Modifier.height(
                    14.dp
                )
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement
                        .spacedBy(
                            10.dp
                        )
            ) {
                MetricCard(
                    modifier =
                        Modifier
                            .weight(
                                1f
                            ),
                    value =
                        route.packages
                            .size
                            .toString(),
                    label =
                        "Importados"
                )

                MetricCard(
                    modifier =
                        Modifier
                            .weight(
                                1f
                            ),
                    value =
                        (
                            route.expectedTotal
                                ?: route.packages
                                    .size
                            )
                            .toString(),
                    label =
                        "Esperados"
                )
            }

            if (
                route.partial
            ) {
                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                InfoCard(
                    title =
                        "Importação parcial",
                    text =
                        "O Copilot voltou ao app para não ficar preso no SPX. Toque em Continuar sincronização para buscar os pedidos restantes.",
                    accent =
                        RcOrange
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                PrimaryButton(
                    text =
                        "CONTINUAR SINCRONIZAÇÃO",
                    color =
                        RcOrange,
                    onClick =
                        onResync
                )
            }

            Spacer(
                Modifier.height(
                    16.dp
                )
            )

            Text(
                text =
                    "Fila inteligente",
                color =
                    RcText,
                fontSize =
                    19.sp
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            queue.next
                ?.let { pkg ->
                    CustomerCard(
                        title =
                            "PRÓXIMA ENTREGA",
                        pkg =
                            pkg,
                        accent =
                            RcBlue,
                        actionLabel =
                            "AVISAR / WHATSAPP",
                        onAction = {
                            WhatsAppLauncher
                                .open(
                                    context,
                                    pkg.phone,
                                    CustomerQueuePlanner
                                        .nextMessage(
                                            pkg
                                        )
                                )
                        }
                    )
                }

            queue.preAlert
                ?.let { pkg ->
                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    CustomerCard(
                        title =
                            "PRÉ-AVISO — 1 ENTREGA ANTES",
                        pkg =
                            pkg,
                        accent =
                            RcOrange,
                        actionLabel =
                            "PRÉ-AVISAR CLIENTE",
                        onAction = {
                            WhatsAppLauncher
                                .open(
                                    context,
                                    pkg.phone,
                                    CustomerQueuePlanner
                                        .preAlertMessage(
                                            pkg
                                        )
                                )
                        }
                    )
                }

            Spacer(
                Modifier.height(
                    16.dp
                )
            )

            PrimaryButton(
                text =
                    "MAPA INTERNO DA ROTA",
                color =
                    RcBlue,
                onClick =
                    onMap
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            SecondaryButton(
                text =
                    if (
                        driverLocation ==
                        null
                    ) {
                        "ATIVAR LOCALIZAÇÃO DA ROTA"
                    } else {
                        "LOCALIZAÇÃO ATIVA ✓"
                    },
                onClick = {
                    if (
                        hasLocationPermission(
                            context
                        )
                    ) {
                        startDriverLocation(
                            context
                        )
                    } else {
                        locationPermissionLauncher
                            .launch(
                                arrayOf(
                                    Manifest.permission
                                        .ACCESS_FINE_LOCATION,
                                    Manifest.permission
                                        .ACCESS_COARSE_LOCATION
                                )
                            )
                    }
                }
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            SecondaryButton(
                text =
                    "COPIAR AT / RESUMO",
                onClick = {
                    copyRouteSummary(
                        context
                    )
                }
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            SecondaryButton(
                text =
                    "ABRIR SPX",
                onClick = {
                    abrirSPX(
                        context
                    )
                }
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                text =
                    "Pedidos (${route.packages.size})",
                color =
                    RcText,
                fontSize =
                    19.sp
            )

            Spacer(
                Modifier.height(
                    8.dp
                )
            )

            route.packages
                .sortedBy {
                    it.sequence
                }
                .take(
                    15
                )
                .forEach { pkg ->
                    PackageRow(
                        pkg
                    )

                    Spacer(
                        Modifier.height(
                            7.dp
                        )
                    )
                }

            if (
                route.packages.size >
                15
            ) {
                Text(
                    text =
                        "+ ${route.packages.size - 15} pedidos salvos",
                    color =
                        RcBlue,
                    fontSize =
                        12.sp
                )
            }

            Spacer(
                Modifier.height(
                    16.dp
                )
            )

            SecondaryButton(
                text =
                    "VOLTAR AO INÍCIO",
                onClick =
                    onHome
            )
        }

        FloatingActionButton(
            onClick = {
                SpxSessionState
                    .requestScanner()

                abrirSPX(
                    context
                )
            },
            modifier =
                Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .padding(
                        22.dp
                    ),
            shape =
                CircleShape,
            containerColor =
                RcOrange,
            contentColor =
                Color.White
        ) {
            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    text =
                        "RC",
                    fontSize =
                        11.sp
                )

                Text(
                    text =
                        "QR",
                    fontSize =
                        16.sp
                )
            }
        }
    }
}

@Composable
private fun RouteMapScreen(
    route: ActiveRoute?,
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val activeRoute by
        RouteRepository
            .activeRoute
            .collectAsState()

    val driverLocation by
        RouteRepository
            .driverLocation
            .collectAsState()

    var geocodeText by
        remember {
            mutableStateOf(
                "Preparando endereços..."
            )
        }

    LaunchedEffect(
        route?.at
    ) {
        if (
            route != null
        ) {
            val missing =
                route.packages
                    .count {
                        !it.address
                            .isNullOrBlank() &&
                            (
                                it.latitude ==
                                null ||
                                    it.longitude ==
                                    null
                                )
                    }

            if (
                missing >
                0
            ) {
                geocodeText =
                    "Localizando endereços..."

                RouteGeocoder
                    .geocodeMissing(
                        context
                    ) {
                            done,
                            total ->

                        geocodeText =
                            "Localizando endereços: $done / $total"
                    }

                geocodeText =
                    "Mapa atualizado."
            } else {
                geocodeText =
                    "Mapa atualizado."
            }
        }
    }

    val mapRoute =
        activeRoute
            ?: route

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    RcBackground
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(
                    12.dp
                )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement =
                Arrangement
                    .SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(
                            1f
                        )
            ) {
                Text(
                    text =
                        "Mapa da rota",
                    color =
                        RcText,
                    fontSize =
                        22.sp
                )

                Text(
                    text =
                        geocodeText,
                    color =
                        RcMuted,
                    fontSize =
                        11.sp
                )
            }

            Button(
                onClick =
                    onBack,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                RcSoftBlue,
                            contentColor =
                                RcBlue
                        )
            ) {
                Text(
                    "VOLTAR"
                )
            }
        }

        Spacer(
            Modifier.height(
                10.dp
            )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(
                        1f
                    ),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            Color.White
                    ),
            shape =
                RoundedCornerShape(
                    18.dp
                )
        ) {
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize(),
                factory = { ctx ->
                    WebView(
                        ctx
                    ).apply {
                        settings
                            .javaScriptEnabled =
                            true

                        settings
                            .domStorageEnabled =
                            true

                        webViewClient =
                            WebViewClient()
                    }
                },
                update = { webView ->
                    webView
                        .loadDataWithBaseURL(
                            "https://routecopilot.local/",
                            buildMapHtml(
                                mapRoute,
                                driverLocation
                                    ?.latitude,
                                driverLocation
                                    ?.longitude
                            ),
                            "text/html",
                            "UTF-8",
                            null
                        )
                }
            )
        }

        val next =
            CustomerQueuePlanner
                .compute(
                    mapRoute
                )
                .next

        if (
            next != null
        ) {
            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement
                        .spacedBy(
                            8.dp
                        )
            ) {
                Button(
                    modifier =
                        Modifier
                            .weight(
                                1f
                            ),
                    onClick = {
                        WazeLauncher
                            .open(
                                context,
                                next.latitude,
                                next.longitude
                            )
                    },
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    RcSoftBlue,
                                contentColor =
                                    RcBlue
                            )
                ) {
                    Text(
                        "ABRIR NO WAZE"
                    )
                }

                Button(
                    modifier =
                        Modifier
                            .weight(
                                1f
                            ),
                    onClick = {
                        WhatsAppLauncher
                            .open(
                                context,
                                next.phone,
                                CustomerQueuePlanner
                                    .nextMessage(
                                        next
                                    )
                            )
                    },
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    Color(
                                        0xFFEAF9EF
                                    ),
                                contentColor =
                                    RcGreen
                            )
                ) {
                    Text(
                        "WHATSAPP"
                    )
                }
            }
        }
    }
}

@Composable
private fun RcCard(
    content:
        @Composable
        ColumnScope.() -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        RcSurface
                ),
        elevation =
            CardDefaults
                .cardElevation(
                    defaultElevation =
                        2.dp
                ),
        shape =
            RoundedCornerShape(
                20.dp
            )
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        18.dp
                    ),
            content =
                content
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    text: String,
    accent: Color
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        if (
                            accent ==
                            RcOrange
                        ) {
                            RcSoftOrange
                        } else {
                            RcSoftBlue
                        }
                ),
        shape =
            RoundedCornerShape(
                16.dp
            )
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        15.dp
                    )
        ) {
            Text(
                text =
                    title,
                color =
                    accent,
                fontSize =
                    14.sp
            )

            Spacer(
                Modifier.height(
                    4.dp
                )
            )

            Text(
                text =
                    text,
                color =
                    RcText,
                fontSize =
                    12.sp
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    value: String,
    label: String
) {
    Card(
        modifier =
            modifier,
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        RcSurface
                ),
        shape =
            RoundedCornerShape(
                15.dp
            )
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        14.dp
                    )
        ) {
            Text(
                text =
                    value,
                color =
                    RcBlue,
                fontSize =
                    22.sp
            )

            Text(
                text =
                    label,
                color =
                    RcMuted,
                fontSize =
                    11.sp
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {
    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        if (
                            color ==
                            RcOrange
                        ) {
                            RcSoftOrange
                        } else {
                            Color(
                                0xFFEAF9EF
                            )
                        }
                ),
        shape =
            RoundedCornerShape(
                999.dp
            )
    ) {
        Text(
            text =
                text,
            color =
                color,
            fontSize =
                10.sp,
            modifier =
                Modifier
                    .padding(
                        horizontal =
                            10.dp,
                        vertical =
                            6.dp
                    )
        )
    }
}

@Composable
private fun CustomerCard(
    title: String,
    pkg: RoutePackage,
    accent: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        RcSurface
                ),
        shape =
            RoundedCornerShape(
                17.dp
            )
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        15.dp
                    )
        ) {
            Text(
                text =
                    title,
                color =
                    accent,
                fontSize =
                    11.sp
            )

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            Text(
                text =
                    "#${pkg.sequence} • ${maskBr(pkg.br)}",
                color =
                    RcText,
                fontSize =
                    16.sp
            )

            Text(
                text =
                    pkg.address
                        ?: "Endereço aguardando leitura do SPX",
                color =
                    RcMuted,
                fontSize =
                    12.sp
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            Button(
                onClick =
                    onAction,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                if (
                                    accent ==
                                    RcOrange
                                ) {
                                    RcSoftOrange
                                } else {
                                    RcSoftBlue
                                },
                            contentColor =
                                accent
                        )
            ) {
                Text(
                    actionLabel
                )
            }
        }
    }
}

@Composable
private fun PackageRow(
    pkg: RoutePackage
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        RcSurface
                ),
        shape =
            RoundedCornerShape(
                14.dp
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        13.dp
                    ),
            horizontalArrangement =
                Arrangement
                    .spacedBy(
                        12.dp
                    )
        ) {
            Card(
                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                RcSoftBlue
                        ),
                shape =
                    RoundedCornerShape(
                        10.dp
                    )
            ) {
                Text(
                    text =
                        pkg.sequence
                            .toString(),
                    color =
                        RcBlue,
                    modifier =
                        Modifier
                            .padding(
                                horizontal =
                                    10.dp,
                                vertical =
                                    7.dp
                            )
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(
                            1f
                        )
            ) {
                Text(
                    text =
                        maskBr(
                            pkg.br
                        ),
                    color =
                        RcText,
                    fontSize =
                        13.sp
                )

                Text(
                    text =
                        pkg.address
                            ?: "Endereço ainda não capturado",
                    color =
                        RcMuted,
                    fontSize =
                        11.sp
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    55.dp
                ),
        onClick =
            onClick,
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        color,
                    contentColor =
                        Color.White
                ),
        shape =
            RoundedCornerShape(
                15.dp
            )
    ) {
        Text(
            text
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    53.dp
                ),
        onClick =
            onClick,
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        Color.White,
                    contentColor =
                        RcBlue
                ),
        shape =
            RoundedCornerShape(
                15.dp
            )
    ) {
        Text(
            text
        )
    }
}

private fun buildMapHtml(
    route: ActiveRoute?,
    driverLat: Double?,
    driverLon: Double?
): String {
    val points =
        route?.packages
            ?.filter {
                it.latitude != null &&
                    it.longitude != null
            }
            ?.sortedBy {
                it.sequence
            }
            ?: emptyList()

    val jsPoints =
        points.joinToString(
            ","
        ) {
            "[${it.latitude},${it.longitude},${it.sequence}]"
        }

    val centerLat =
        driverLat
            ?: points
                .firstOrNull()
                ?.latitude
            ?: -14.235

    val centerLon =
        driverLon
            ?: points
                .firstOrNull()
                ?.longitude
            ?: -51.9253

    val driverJs =
        if (
            driverLat != null &&
            driverLon != null
        ) {
            """
            L.circleMarker(
                [$driverLat,$driverLon],
                {
                    radius:9,
                    color:'#1565D8',
                    fillColor:'#1565D8',
                    fillOpacity:1
                }
            ).addTo(map).bindPopup('Você');
            """
        } else {
            ""
        }

    return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet"
                  href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <style>
                html,body,#map{
                    height:100%;
                    margin:0;
                    background:#f7f9fc
                }
                .leaflet-control-attribution{
                    font-size:9px
                }
            </style>
        </head>
        <body>
        <div id="map"></div>

        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

        <script>
            const map =
                L.map('map')
                    .setView(
                        [$centerLat,$centerLon],
                        13
                    );

            L.tileLayer(
                'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                {
                    maxZoom:19,
                    attribution:'© OpenStreetMap'
                }
            ).addTo(map);

            const points =
                [$jsPoints];

            const latlngs =
                [];

            points.forEach(p => {
                L.marker(
                    [p[0],p[1]]
                )
                    .addTo(map)
                    .bindPopup(
                        'Parada ' + p[2]
                    );

                latlngs.push(
                    [p[0],p[1]]
                );
            });

            $driverJs

            if (
                latlngs.length >
                0
            ) {
                const bounds =
                    L.latLngBounds(
                        latlngs
                    );

                map.fitBounds(
                    bounds.pad(
                        0.18
                    )
                );

                const coords =
                    latlngs
                        .map(
                            p =>
                                p[1] +
                                ',' +
                                p[0]
                        )
                        .join(
                            ';'
                        );

                if (
                    latlngs.length >=
                    2
                ) {
                    fetch(
                        'https://router.project-osrm.org/route/v1/driving/' +
                        coords +
                        '?overview=full&geometries=geojson'
                    )
                    .then(
                        r =>
                            r.json()
                    )
                    .then(
                        data => {
                            if (
                                data.routes &&
                                data.routes.length
                            ) {
                                L.geoJSON(
                                    data.routes[0].geometry,
                                    {
                                        style:{
                                            color:'#1565D8',
                                            weight:5,
                                            opacity:0.82
                                        }
                                    }
                                ).addTo(
                                    map
                                );
                            }
                        }
                    )
                    .catch(
                        () => {
                            L.polyline(
                                latlngs,
                                {
                                    color:'#1565D8',
                                    weight:4,
                                    opacity:0.70
                                }
                            ).addTo(
                                map
                            );
                        }
                    );
                }
            }
        </script>
        </body>
        </html>
    """.trimIndent()
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
            AccessibilityServiceInfo
                .FEEDBACK_ALL_MASK
        )
        .any { info ->
            val service =
                info
                    .resolveInfo
                    .serviceInfo

            service.packageName ==
                context.packageName &&
                service.name
                    .contains(
                        "SpxAccessibilityService"
                    )
        }
}

fun abrirConfiguracaoAcessibilidade(
    context: Context
) {
    runCatching {
        context.startActivity(
            Intent(
                Settings
                    .ACTION_ACCESSIBILITY_SETTINGS
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
    val packageName =
        "com.shopee.spx.driver.brazil"

    val intent =
        context
            .packageManager
            .getLaunchIntentForPackage(
                packageName
            )

    if (
        intent ==
        null
    ) {
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

    context.startActivity(
        intent
    )
}

private fun hasLocationPermission(
    context: Context
): Boolean =
    ContextCompat
        .checkSelfPermission(
            context,
            Manifest.permission
                .ACCESS_FINE_LOCATION
        ) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat
            .checkSelfPermission(
                context,
                Manifest.permission
                    .ACCESS_COARSE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED

private fun startDriverLocation(
    context: Context
) {
    if (
        !hasLocationPermission(
            context
        )
    ) {
        return
    }

    val intent =
        Intent(
            context,
            DriverLocationService::class.java
        )

    ContextCompat
        .startForegroundService(
            context,
            intent
        )

    Toast.makeText(
        context,
        "Localização da rota ativada.",
        Toast.LENGTH_SHORT
    ).show()
}

private fun copyRouteSummary(
    context: Context
) {
    val clipboard =
        context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "RouteCopilot",
            RouteRepository
                .buildRouteSummary()
        )
    )

    Toast.makeText(
        context,
        "Resumo da rota copiado.",
        Toast.LENGTH_SHORT
    ).show()
}

private fun maskBr(
    br: String
): String {
    if (
        br.length <=
        8
    ) {
        return br
    }

    return "${br.take(4)}…${br.takeLast(4)}"
}
