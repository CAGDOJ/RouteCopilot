package com.routecopilot

import android.accessibilityservice.AccessibilityServiceInfo
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState
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
private val AppBorder = Color(0xFFE2E8F0)

private enum class Screen {
    HOME,
    SYNC,
    ROUTE
}

class MainActivity : ComponentActivity() {

    private val accessibilityEnabled: MutableState<Boolean> =
        mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        accessibilityEnabled.value =
            isAccessibilityServiceEnabled(this)

        setContent {
            RouteCopilotTheme {
                RouteCopilotApp(
                    accessibilityEnabled =
                        accessibilityEnabled.value
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        accessibilityEnabled.value =
            isAccessibilityServiceEnabled(this)
    }
}

@Composable
private fun RouteCopilotApp(
    accessibilityEnabled: Boolean
) {
    val spxState by
        SpxSessionState.state.collectAsState()

    var screen by remember {
        mutableStateOf(Screen.HOME)
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

    when (screen) {
        Screen.HOME ->
            HomeScreen(
                accessibilityEnabled = accessibilityEnabled,
                onStart = {
                    SpxSessionState.beginImport()
                    screen = Screen.SYNC
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
                onBackHome = {
                    SpxSessionState.reset()
                    screen = Screen.HOME
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    onStart: () -> Unit
) {
    val context =
        LocalContext.current

    AppScaffold {
        BrandHeader(
            "Sua rota mais simples, suas entregas mais rápidas."
        )

        Spacer(
            Modifier.height(26.dp)
        )

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

        Spacer(
            Modifier.height(20.dp)
        )

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

                Spacer(
                    Modifier.height(18.dp)
                )

                PrimaryButton(
                    text = "INICIAR ROTA",
                    icon = R.drawable.ic_play,
                    color = AppBlue,
                    onClick = {
                        if (!accessibilityEnabled) {
                            abrirConfiguracaoAcessibilidade(
                                context
                            )
                        } else {
                            onStart()
                        }
                    }
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                ActionCard(
                    icon = R.drawable.ic_message,
                    iconColor = AppGreen,
                    title = "Disparar mensagem inicial",
                    subtitle = "Mensagem preparada após sincronizar a rota",
                    onClick = {
                        Toast.makeText(
                            context,
                            "Primeiro sincronize a rota.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                Spacer(
                    Modifier.height(10.dp)
                )

                ActionCard(
                    icon = R.drawable.ic_location,
                    iconColor = AppBlue,
                    title = "Localizador da rota",
                    subtitle = "Ativado quando a operação começar",
                    onClick = {
                        Toast.makeText(
                            context,
                            "Primeiro sincronize a rota.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        Spacer(
            Modifier.height(20.dp)
        )

        Text(
            text = "Conexões e ferramentas",
            color = AppText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            Modifier.height(10.dp)
        )

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

        Spacer(
            Modifier.height(8.dp)
        )

        ConnectionRow(
            icon = R.drawable.ic_navigation,
            iconColor = AppBlue,
            title = "Waze integrado",
            subtitle = "Navegação pronta",
            ok = true
        )

        Spacer(
            Modifier.height(8.dp)
        )

        ConnectionRow(
            icon = R.drawable.ic_whatsapp,
            iconColor = AppGreen,
            title = "WhatsApp pronto",
            subtitle = "Mensagens configuradas",
            ok = true
        )

        Spacer(
            Modifier.height(16.dp)
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
        SpxSessionState.state.collectAsState()

    val message by
        SpxSessionState.statusMessage.collectAsState()

    var spxOpened by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(
        accessibilityEnabled,
        spxOpened
    ) {
        if (
            accessibilityEnabled &&
            !spxOpened &&
            state != SpxState.ERROR
        ) {
            spxOpened = true

            SpxSessionState.updateState(
                SpxState.OPENING_SPX,
                "Abrindo SPX..."
            )

            abrirSPX(context)
        }
    }

    AppScaffold {
        BrandHeader()

        Spacer(
            Modifier.height(22.dp)
        )

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
                Icon(
                    painter = painterResource(
                        id = R.drawable.ic_sync
                    ),
                    contentDescription = null,
                    tint = AppBlue,
                    modifier = Modifier
                        .width(54.dp)
                        .height(54.dp)
                )

                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    text = "Sincronizando rota",
                    color = AppText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = message,
                    color = AppMuted,
                    fontSize = 14.sp
                )

                Spacer(
                    Modifier.height(18.dp)
                )

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = AppBlue,
                    trackColor = AppBlueSoft
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                StepRow(
                    "1",
                    "Verificar login",
                    state.ordinal > SpxState.CHECKING_SESSION.ordinal,
                    state == SpxState.CHECKING_SESSION ||
                        state == SpxState.LOGIN_REQUIRED
                )

                StepRow(
                    "2",
                    "Confirmar aceite",
                    state.ordinal > SpxState.CONSENT_REQUIRED.ordinal,
                    state == SpxState.CONSENT_REQUIRED
                )

                StepRow(
                    "3",
                    "Reconhecimento facial",
                    state.ordinal > SpxState.FACE_CHECK_REQUIRED.ordinal,
                    state == SpxState.FACE_CHECK_REQUIRED
                )

                StepRow(
                    "4",
                    "Localizar seta de download",
                    state.ordinal > SpxState.DOWNLOAD_BUTTON_FOUND.ordinal,
                    state == SpxState.FINDING_DOWNLOAD_BUTTON ||
                        state == SpxState.DOWNLOAD_BUTTON_FOUND
                )

                StepRow(
                    "5",
                    "Baixar rota",
                    state.ordinal > SpxState.WAITING_ROUTE_DOWNLOAD.ordinal,
                    state == SpxState.DOWNLOADING_ROUTE ||
                        state == SpxState.WAITING_ROUTE_DOWNLOAD
                )

                StepRow(
                    "6",
                    "Abrir Entrega / Em Rota",
                    state.ordinal > SpxState.OPENING_IN_ROUTE.ordinal,
                    state == SpxState.OPENING_DELIVERIES ||
                        state == SpxState.OPENING_IN_ROUTE
                )

                StepRow(
                    "7",
                    "Importar pedidos",
                    state.ordinal > SpxState.VALIDATING_ROUTE.ordinal,
                    state == SpxState.READING_ROUTE ||
                        state == SpxState.VALIDATING_ROUTE
                )

                StepRow(
                    "8",
                    "Preparar rota",
                    state == SpxState.ROUTE_READY,
                    state == SpxState.CALCULATING_ROUTE
                )
            }
        }

        Spacer(
            Modifier.height(14.dp)
        )

        when (state) {
            SpxState.LOGIN_REQUIRED,
            SpxState.CONSENT_REQUIRED,
            SpxState.FACE_CHECK_REQUIRED -> {
                val text =
                    when (state) {
                        SpxState.LOGIN_REQUIRED ->
                            "Faça o login no SPX. O Copilot continua automaticamente depois."

                        SpxState.CONSENT_REQUIRED ->
                            "Confirme o aceite na tela oficial do SPX."

                        else ->
                            "Conclua o reconhecimento facial no SPX."
                    }

                InfoCard(
                    title = "Ação necessária",
                    text = text,
                    warning = true
                )
            }

            SpxState.ERROR ->
                InfoCard(
                    title = "Sincronização interrompida",
                    text = message,
                    warning = true
                )

            else ->
                InfoCard(
                    title = "Sincronização automática",
                    text = "O Copilot só abre o SPX quando precisa navegar. Você pode usar Voltar/Home normalmente.",
                    warning = false
                )
        }

        Spacer(
            Modifier.height(14.dp)
        )

        PrimaryButton(
            text = "VOLTAR / CANCELAR",
            icon = R.drawable.ic_back,
            color = AppSurfaceSoft,
            textColor = AppText,
            onClick = onBack
        )

        Spacer(
            Modifier.height(16.dp)
        )
    }
}

@Composable
private fun RouteScreen(
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

    val packages by
        SpxSessionState.packageCodes.collectAsState()

    val total =
        expected ?: count

    AppScaffold {
        BrandHeader(
            "Acompanhe suas entregas em tempo real."
        )

        Spacer(
            Modifier.height(22.dp)
        )

        Text(
            text = "Rota ativa",
            color = AppText,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "Seus pedidos organizados e ao seu alcance.",
            color = AppMuted,
            fontSize = 13.sp
        )

        Spacer(
            Modifier.height(14.dp)
        )

        StatsRow(
            inRoute = total,
            occurrences = 0,
            closed = 0
        )

        Spacer(
            Modifier.height(14.dp)
        )

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
                    }
                }
            }
        }

        Spacer(
            Modifier.height(14.dp)
        )

        ActionCard(
            icon = R.drawable.ic_message,
            iconColor = AppGreen,
            title = "Disparar mensagem de início",
            subtitle = "Abrir mensagens preparadas para a rota",
            onClick = {
                Toast.makeText(
                    context,
                    "Mensagens serão integradas na próxima etapa.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        Spacer(
            Modifier.height(10.dp)
        )

        ActionCard(
            icon = R.drawable.ic_location,
            iconColor = AppBlue,
            title = "Localizador da rota",
            subtitle = "Acompanhar operação e próxima entrega",
            onClick = {
                Toast.makeText(
                    context,
                    "Localizador será integrado na próxima etapa.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        Spacer(
            Modifier.height(18.dp)
        )

        Text(
            text = "Pedidos (${packages.size})",
            color = AppText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            Modifier.height(10.dp)
        )

        packages
            .take(8)
            .forEachIndexed { index, code ->
                PackageRow(
                    index = index + 1,
                    code = code,
                    next = index == 0
                )

                Spacer(
                    Modifier.height(8.dp)
                )
            }

        if (packages.size > 8) {
            Text(
                text = "+ ${packages.size - 8} pedidos importados",
                color = AppBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(
            Modifier.height(20.dp)
        )

        PrimaryButton(
            text = "VOLTAR AO INÍCIO",
            icon = R.drawable.ic_back,
            color = AppSurfaceSoft,
            textColor = AppText,
            onClick = onBackHome
        )

        Spacer(
            Modifier.height(16.dp)
        )
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
        val cardWidth =
            (maxWidth - gap - gap) / 3

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

            Spacer(
                Modifier.height(6.dp)
            )

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

        Spacer(
            Modifier.width(8.dp)
        )

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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                color =
                    if (ok) {
                        AppGreen
                    } else {
                        AppOrange
                    },
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun StepRow(
    number: String,
    title: String,
    done: Boolean,
    active: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (done) "✓" else number,
            color =
                when {
                    done -> AppGreen
                    active -> AppBlue
                    else -> AppMuted
                },
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Column {
            Text(
                text = title,
                color =
                    if (active) {
                        AppBlue
                    } else {
                        AppText
                    },
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
                        active -> "Em andamento..."
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
                color =
                    if (warning) {
                        AppOrange
                    } else {
                        AppBlue
                    },
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(4.dp)
            )

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
    code: String,
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

            Column {
                Text(
                    text = code,
                    color = AppText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                if (next) {
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
            val service =
                info.resolveInfo.serviceInfo

            service.packageName ==
                context.packageName &&
                service.name.contains(
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
    val packageName =
        "com.shopee.spx.driver.brazil"

    val intent =
        context.packageManager
            .getLaunchIntentForPackage(
                packageName
            )

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

    context.startActivity(
        intent
    )
}
