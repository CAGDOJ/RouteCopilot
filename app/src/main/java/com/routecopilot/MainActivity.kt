package com.routecopilot

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routecopilot.data.repository.RouteRepository
import com.routecopilot.spx.OccurrencePhase
import com.routecopilot.spx.SpxAutomationMode
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState
import com.routecopilot.ui.theme.RouteCopilotTheme

private val AppBackground =
    Color(0xFF08111F)

private val AppSurface =
    Color(0xFF111C2E)

private val AppSurface2 =
    Color(0xFF162338)

private val AppBlue =
    Color(0xFF2563EB)

private val AppCyan =
    Color(0xFF38BDF8)

private val AppOrange =
    Color(0xFFF97316)

private val AppWhite =
    Color(0xFFF8FAFC)

private val AppMuted =
    Color(0xFF94A3B8)

private val AppGreen =
    Color(0xFF22C55E)

private val AppYellow =
    Color(0xFFF59E0B)

class MainActivity :
    ComponentActivity() {

    private val openManagement =
        mutableStateOf(false)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        enableEdgeToEdge()

        processIntent(
            intent
        )

        setContent {
            RouteCopilotTheme {
                RouteCopilotApp(
                    forceManagement =
                        openManagement.value,
                    consumeManagement = {
                        openManagement.value =
                            false
                    }
                )
            }
        }
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

        processIntent(
            intent
        )
    }

    private fun processIntent(
        intent: Intent?
    ) {
        if (
            intent
                ?.getBooleanExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    false
                ) == true
        ) {
            openManagement.value =
                true
        }
    }
}

@Composable
private fun RouteCopilotApp(
    forceManagement: Boolean,
    consumeManagement: () -> Unit
) {
    val context =
        LocalContext.current

    val state by
        SpxSessionState
            .state
            .collectAsState()

    val occurrence by
        SpxSessionState
            .occurrencePhase
            .collectAsState()

    var screen by remember {
        mutableStateOf(
            if (
                forceManagement
            ) {
                "management"
            } else {
                "home"
            }
        )
    }

    var savedAt by remember {
        mutableStateOf<String?>(
            null
        )
    }

    LaunchedEffect(
        forceManagement,
        state,
        occurrence
    ) {
        if (
            forceManagement ||
            state ==
            SpxState.IMPORT_COMPLETE ||
            state ==
            SpxState.ROUTE_READY ||
            occurrence ==
            OccurrencePhase.DONE
        ) {
            screen =
                "management"

            if (
                forceManagement
            ) {
                consumeManagement()
            }
        }

        if (
            (
                state ==
                SpxState.IMPORT_COMPLETE ||
                state ==
                SpxState.ROUTE_READY
                )
        ) {
            val at =
                SpxSessionState
                    .atCode
                    .value

            if (
                !at.isNullOrBlank() &&
                savedAt != at
            ) {
                val repository =
                    RouteRepository.get(
                        context
                    )

                repository.saveImportedRoute(
                    at = at,
                    loadDate =
                        SpxSessionState
                            .dataCarregamento
                            .value,
                    expectedTotal =
                        SpxSessionState
                            .totalEsperado
                            .value,
                    brs =
                        SpxSessionState
                            .packages
                            .value
                            .keys
                )

                savedAt =
                    at
            }
        }
    }

    when (
        screen
    ) {
        "home" -> {
            HomeScreen(
                startImport = {
                    SpxSessionState
                        .startImport()

                    screen =
                        "import"
                },
                openHistory = {
                    screen =
                        "history"
                }
            )
        }

        "import" -> {
            ImportScreen(
                back = {
                    screen =
                        "home"
                }
            )
        }

        "management" -> {
            ManagementScreen(
                goHome = {
                    screen =
                        "home"
                }
            )
        }

        "history" -> {
            HistoryScreen(
                back = {
                    screen =
                        "home"
                }
            )
        }
    }
}

@Composable
private fun AppPage(
    content:
    @Composable
    androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    AppBackground
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 18.dp
                ),
        content =
            content
    )
}

@Composable
private fun HomeScreen(
    startImport: () -> Unit,
    openHistory: () -> Unit
) {
    AppPage {
        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                "ROUTE",
            color =
                AppCyan,
            fontSize =
                14.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "COPILOT",
            color =
                AppWhite,
            fontSize =
                38.sp,
            fontWeight =
                FontWeight.ExtraBold
        )

        Text(
            text =
                "Operação inteligente de entregas",
            color =
                AppMuted
        )

        Spacer(
            Modifier.height(
                30.dp
            )
        )

        InfoCard(
            title =
                "PRONTO PARA INICIAR",
            text =
                "Importe a rota do SPX para começar."
        )

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        FullButton(
            text =
                "INICIAR ROTA",
            color =
                AppOrange,
            onClick =
                startImport
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        FullButton(
            text =
                "HISTÓRICO",
            color =
                AppSurface2,
            onClick =
                openHistory
        )
    }
}

@Composable
private fun ImportScreen(
    back: () -> Unit
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

    val at by
        SpxSessionState
            .atCode
            .collectAsState()

    val total by
        SpxSessionState
            .totalEsperado
            .collectAsState()

    val count by
        SpxSessionState
            .packageCount
            .collectAsState()

    val mode by
        SpxSessionState
            .automationMode
            .collectAsState()

    var launched by remember {
        mutableStateOf(false)
    }

    val accessibilityEnabled =
        isAccessibilityEnabled(
            context
        )

    LaunchedEffect(
        accessibilityEnabled,
        mode
    ) {
        if (
            accessibilityEnabled &&
            mode ==
            SpxAutomationMode.IMPORT_ROUTE &&
            !launched
        ) {
            launched =
                true

            openSpx(
                context
            )
        }
    }

    AppPage {
        Text(
            text =
                "IMPORTAÇÃO SPX",
            color =
                AppCyan,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        InfoCard(
            title =
                when (
                    state
                ) {
                    SpxState.LOGIN_REQUIRED ->
                        "LOGIN NECESSÁRIO"

                    SpxState.IMPORT_COMPLETE,
                    SpxState.ROUTE_READY ->
                        "ROTA PRONTA"

                    else ->
                        "SPX"
                },
            text =
                message
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        if (
            at != null
        ) {
            Text(
                text =
                    "AT: $at",
                color =
                    AppWhite,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Text(
            text =
                if (
                    total != null
                ) {
                    "Pedidos: $count / $total"
                } else {
                    "Pedidos encontrados: $count"
                },
            color =
                AppMuted
        )

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        if (
            !accessibilityEnabled
        ) {
            FullButton(
                text =
                    "ATIVAR ACESSIBILIDADE",
                color =
                    AppOrange
            ) {
                context.startActivity(
                    Intent(
                        Settings
                            .ACTION_ACCESSIBILITY_SETTINGS
                    )
                )
            }
        } else {
            FullButton(
                text =
                    "ABRIR SPX",
                color =
                    AppBlue
            ) {
                openSpx(
                    context
                )
            }
        }

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        FullButton(
            text =
                "VOLTAR",
            color =
                AppSurface2,
            onClick =
                back
        )
    }
}

@Composable
private fun ManagementScreen(
    goHome: () -> Unit
) {
    val context =
        LocalContext.current

    val at by
        SpxSessionState
            .atCode
            .collectAsState()

    val date by
        SpxSessionState
            .dataCarregamento
            .collectAsState()

    val total by
        SpxSessionState
            .totalEsperado
            .collectAsState()

    val count by
        SpxSessionState
            .packageCount
            .collectAsState()

    val occurrence by
        SpxSessionState
            .occurrencePhase
            .collectAsState()

    AppPage {
        Text(
            text =
                "GESTÃO DA ROTA",
            color =
                AppCyan,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(
                16.dp
            )
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    18.dp
                ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        AppSurface
                )
        ) {
            Column(
                Modifier.padding(
                    18.dp
                )
            ) {
                Text(
                    text =
                        at
                            ?: "AT não identificada",
                    color =
                        AppWhite,
                    fontSize =
                        20.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        6.dp
                    )
                )

                Text(
                    text =
                        "Carregamento: ${date ?: "Não identificado"}",
                    color =
                        AppMuted
                )

                Text(
                    text =
                        "Pedidos: ${total ?: count}",
                    color =
                        AppMuted
                )
            }
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        FullButton(
            text =
                "COPIAR AT",
            color =
                AppBlue
        ) {
            val text =
                buildString {
                    appendLine(
                        "AT: ${at ?: "Não identificada"}"
                    )

                    appendLine(
                        "Data de carregamento: ${date ?: "Não identificada"}"
                    )

                    append(
                        "Total de pedidos: ${total ?: count}"
                    )
                }

            copyText(
                context,
                text
            )
        }

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        FullButton(
            text =
                "FORA DE ROTA — PEDIDO ABERTO",
            color =
                AppYellow
        ) {
            SpxSessionState
                .startOutOfRoute()

            openSpx(
                context
            )
        }

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        Text(
            text =
                "Ocorrência: ${occurrence.name}",
            color =
                when (
                    occurrence
                ) {
                    OccurrencePhase.DONE ->
                        AppGreen

                    OccurrencePhase.ERROR,
                    OccurrencePhase.INVALID_PHOTO ->
                        Color(0xFFEF4444)

                    else ->
                        AppMuted
                }
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        FullButton(
            text =
                "ABRIR SPX",
            color =
                AppSurface2
        ) {
            openSpx(
                context
            )
        }

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        FullButton(
            text =
                "VOLTAR AO INÍCIO",
            color =
                AppSurface2,
            onClick =
                goHome
        )
    }
}

@Composable
private fun HistoryScreen(
    back: () -> Unit
) {
    val context =
        LocalContext.current

    var routes by remember {
        mutableStateOf(
            emptyList<
                com.routecopilot
                    .data
                    .model
                    .RouteRecord
            >()
        )
    }

    LaunchedEffect(Unit) {
        routes =
            RouteRepository
                .get(context)
                .getRoutes()
    }

    AppPage {
        Text(
            text =
                "HISTÓRICO",
            color =
                AppCyan,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(
                16.dp
            )
        )

        if (
            routes.isEmpty()
        ) {
            InfoCard(
                title =
                    "SEM ROTAS",
                text =
                    "As rotas importadas aparecerão aqui."
            )
        } else {
            routes.forEach {
                InfoCard(
                    title =
                        it.at,
                    text =
                        "${it.loadDate ?: "Data não identificada"} • ${it.expectedTotal ?: it.importedTotal} pedidos"
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )
            }
        }

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        FullButton(
            text =
                "VOLTAR",
            color =
                AppSurface2,
            onClick =
                back
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    text: String
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    AppSurface
            )
    ) {
        Column(
            Modifier.padding(
                18.dp
            )
        ) {
            Text(
                text =
                    title,
                color =
                    AppWhite,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            Text(
                text =
                    text,
                color =
                    AppMuted
            )
        }
    }
}

@Composable
private fun FullButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    56.dp
                ),
        onClick =
            onClick,
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        color,
                    contentColor =
                        AppWhite
                ),
        shape =
            RoundedCornerShape(
                15.dp
            )
    ) {
        Text(
            text =
                text,
            fontWeight =
                FontWeight.Bold
        )
    }
}

private fun openSpx(
    context: Context
) {
    val intent =
        context
            .packageManager
            .getLaunchIntentForPackage(
                "com.shopee.spx.driver.brazil"
            )

    if (
        intent == null
    ) {
        Toast.makeText(
            context,
            "SPX não encontrado.",
            Toast.LENGTH_LONG
        ).show()

        return
    }

    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    )

    context.startActivity(
        intent
    )
}

private fun isAccessibilityEnabled(
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
                info.resolveInfo
                    .serviceInfo

            service.packageName ==
                context.packageName &&
                service.name.contains(
                    "SpxAccessibilityService"
                )
        }
}

private fun copyText(
    context: Context,
    text: String
) {
    val clipboard =
        context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            "RouteCopilot",
            text
        )
    )

    Toast.makeText(
        context,
        "Dados da rota copiados.",
        Toast.LENGTH_SHORT
    ).show()
}
