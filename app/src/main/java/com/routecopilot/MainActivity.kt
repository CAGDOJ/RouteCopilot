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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
private val AppGreen = Color(0xFF16A34A)
private val AppGreenSoft = Color(0xFFECFDF3)
private val AppText = Color(0xFF0F172A)
private val AppMuted = Color(0xFF64748B)
private val AppBorder = Color(0xFFE2E8F0)

private enum class Screen { HOME, SYNC, ROUTE }

class MainActivity : ComponentActivity() {
    private val accessibilityEnabled: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    }
}

@Composable
private fun RouteCopilotApp(accessibilityEnabled: Boolean) {
    val spxState by SpxSessionState.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }

    LaunchedEffect(spxState) {
        screen = when (spxState) {
            SpxState.ROUTE_READY, SpxState.IMPORT_COMPLETE, SpxState.RETURNING_TO_COPILOT -> Screen.ROUTE
            SpxState.IDLE -> screen
            else -> if (screen == Screen.HOME) Screen.SYNC else screen
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            accessibilityEnabled = accessibilityEnabled,
            onStart = {
                SpxSessionState.beginImport()
                screen = Screen.SYNC
            }
        )
        Screen.SYNC -> SyncScreen(
            accessibilityEnabled = accessibilityEnabled,
            onBack = { screen = Screen.HOME }
        )
        Screen.ROUTE -> RouteScreen(
            onBackHome = {
                SpxSessionState.reset()
                screen = Screen.HOME
            }
        )
    }
}

@Composable
private fun AppScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) { content() }
}

@Composable
private fun BrandHeader(subtitle: String? = null) {
    Text("RouteCopilot", color = AppBlue, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
    if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = AppMuted, fontSize = 14.sp)
    }
}

@Composable
private fun HomeScreen(accessibilityEnabled: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current

    AppScaffold {
        BrandHeader("Sua rota mais simples, suas entregas mais rápidas.")
        Spacer(Modifier.height(28.dp))
        Text("Olá, entregador!", color = AppText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(5.dp))
        Text("Tudo pronto para começar o seu dia.", color = AppMuted, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("SUA ROTA DE HOJE", color = AppMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Pronta para sincronizar", color = AppText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(18.dp))

                PrimaryButton(
                    text = "▶  INICIAR ROTA",
                    color = AppBlue,
                    onClick = {
                        if (!accessibilityEnabled) abrirConfiguracaoAcessibilidade(context) else onStart()
                    }
                )

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallActionCard(
                        modifier = Modifier.weight(1f),
                        title = "💬 Mensagem",
                        subtitle = "Início da rota",
                        onClick = {
                            Toast.makeText(context, "A mensagem será preparada após sincronizar a rota.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SmallActionCard(
                        modifier = Modifier.weight(1f),
                        title = "📍 Localizador",
                        subtitle = "Da rota",
                        onClick = {
                            Toast.makeText(context, "O localizador será ativado após iniciar a rota.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Conexões e ferramentas", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        ConnectionRow("SPX", if (accessibilityEnabled) "Integração pronta" else "Ative a acessibilidade", accessibilityEnabled)
        Spacer(Modifier.height(8.dp))
        ConnectionRow("Waze", "Navegação preparada", true)
        Spacer(Modifier.height(8.dp))
        ConnectionRow("WhatsApp", "Mensagens preparadas", true)
    }
}

@Composable
private fun SyncScreen(accessibilityEnabled: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by SpxSessionState.state.collectAsState()
    val message by SpxSessionState.statusMessage.collectAsState()
    var spxOpened by remember { mutableStateOf(false) }

    LaunchedEffect(accessibilityEnabled, spxOpened) {
        if (accessibilityEnabled && !spxOpened) {
            spxOpened = true
            SpxSessionState.updateState(SpxState.OPENING_SPX, "Abrindo SPX...")
            abrirSPX(context)
        }
    }

    AppScaffold {
        BrandHeader()
        Spacer(Modifier.height(26.dp))
        Text("Sincronizando com SPX", color = AppText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = AppMuted, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = AppBlue,
            trackColor = AppBlueSoft
        )
        Spacer(Modifier.height(26.dp))

        StepRow("1", "Verificar login", state.ordinal > SpxState.CHECKING_SESSION.ordinal, state == SpxState.CHECKING_SESSION || state == SpxState.LOGIN_REQUIRED)
        StepRow("2", "Confirmar aceite", state.ordinal > SpxState.CONSENT_REQUIRED.ordinal, state == SpxState.CONSENT_REQUIRED)
        StepRow("3", "Reconhecimento facial", state.ordinal > SpxState.FACE_CHECK_REQUIRED.ordinal, state == SpxState.FACE_CHECK_REQUIRED)
        StepRow("4", "Localizar seta de download", state.ordinal > SpxState.DOWNLOAD_BUTTON_FOUND.ordinal, state == SpxState.FINDING_DOWNLOAD_BUTTON || state == SpxState.DOWNLOAD_BUTTON_FOUND)
        StepRow("5", "Baixar rota", state.ordinal > SpxState.WAITING_ROUTE_DOWNLOAD.ordinal, state == SpxState.DOWNLOADING_ROUTE || state == SpxState.WAITING_ROUTE_DOWNLOAD)
        StepRow("6", "Abrir Entrega / Em Rota", state.ordinal > SpxState.OPENING_IN_ROUTE.ordinal, state == SpxState.OPENING_DELIVERIES || state == SpxState.OPENING_IN_ROUTE)
        StepRow("7", "Importar pedidos", state.ordinal > SpxState.VALIDATING_ROUTE.ordinal, state == SpxState.READING_ROUTE || state == SpxState.VALIDATING_ROUTE)
        StepRow("8", "Preparar rota", state == SpxState.ROUTE_READY, state == SpxState.CALCULATING_ROUTE)

        Spacer(Modifier.height(18.dp))

        when (state) {
            SpxState.LOGIN_REQUIRED, SpxState.CONSENT_REQUIRED, SpxState.FACE_CHECK_REQUIRED -> {
                val txt = when (state) {
                    SpxState.LOGIN_REQUIRED -> "Faça o login no SPX. Depois o Copilot continua sozinho."
                    SpxState.CONSENT_REQUIRED -> "Confirme o aceite na tela oficial do SPX. Depois a sincronização continua."
                    else -> "Conclua o reconhecimento facial. Depois o Copilot retoma automaticamente."
                }
                InfoCard("Ação necessária", txt, true)
            }
            SpxState.ERROR -> InfoCard("Não foi possível concluir", message, true)
            else -> InfoCard("Você pode aguardar", "O RouteCopilot está realizando a sincronização automaticamente.", false)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("VOLTAR", AppSurfaceSoft, AppText, onBack)
    }
}

@Composable
private fun RouteScreen(onBackHome: () -> Unit) {
    val context = LocalContext.current
    val at by SpxSessionState.atCode.collectAsState()
    val date by SpxSessionState.dataCarregamento.collectAsState()
    val expected by SpxSessionState.totalEsperado.collectAsState()
    val count by SpxSessionState.packageCount.collectAsState()
    val packages by SpxSessionState.packageCodes.collectAsState()
    val total = expected ?: count

    AppScaffold {
        BrandHeader("Acompanhe suas entregas em tempo real.")
        Spacer(Modifier.height(24.dp))
        Text("Rota ativa", color = AppText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), total.toString(), "Em rota", AppBlueSoft, AppBlue)
            StatCard(Modifier.weight(1f), "0", "Ocorrências", AppOrangeSoft, AppOrange)
            StatCard(Modifier.weight(1f), "0", "Encerrados", AppGreenSoft, AppGreen)
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(at ?: "AT não identificada", color = AppText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Data: ${date ?: "Não identificada"}", color = AppMuted, fontSize = 14.sp)
                Text("Pedidos: $count${if (expected != null) " / $expected" else ""}", color = AppMuted, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallActionCard(
                modifier = Modifier.weight(1f),
                title = "💬 Mensagem",
                subtitle = "Início da rota",
                onClick = { Toast.makeText(context, "Mensagens da rota serão preparadas aqui.", Toast.LENGTH_SHORT).show() }
            )
            SmallActionCard(
                modifier = Modifier.weight(1f),
                title = "📍 Localizador",
                subtitle = "Da rota",
                onClick = { Toast.makeText(context, "Localizador da rota será ativado aqui.", Toast.LENGTH_SHORT).show() }
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("Pedidos importados", color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        packages.take(5).forEachIndexed { index, code ->
            PackageRow(index + 1, code, index == 0)
            Spacer(Modifier.height(8.dp))
        }
        if (packages.size > 5) {
            Text("+ ${packages.size - 5} pedidos importados", color = AppBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("VOLTAR AO INÍCIO", AppSurfaceSoft, AppText, onBackHome)
    }
}

@Composable
private fun PrimaryButton(text: String, color: Color, textColor: Color = Color.White, onClick: () -> Unit) {
    Button(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallActionCard(modifier: Modifier = Modifier, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Button(
        modifier = modifier.height(74.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppSurface, contentColor = AppText)
    ) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = AppMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConnectionRow(title: String, subtitle: String, ok: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(15.dp)
    ) {
        Row(Modifier.padding(15.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AppText, fontWeight = FontWeight.Bold)
                Text(subtitle, color = AppMuted, fontSize = 12.sp)
            }
            Text(if (ok) "●" else "○", color = if (ok) AppGreen else AppOrange, fontSize = 18.sp)
        }
    }
}

@Composable
private fun StepRow(number: String, title: String, done: Boolean, active: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        val accent = when { done -> AppGreen; active -> AppBlue; else -> AppBorder }
        Text(if (done) "✓" else number, color = if (done || active) accent else AppMuted, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Column {
            Text(title, color = if (active) AppBlue else AppText, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            Text(
                when { done -> "Concluído"; active -> "Em andamento..."; else -> "Aguardando" },
                color = when { done -> AppGreen; active -> AppBlue; else -> AppMuted },
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String, warning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (warning) AppOrangeSoft else AppBlueSoft),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = if (warning) AppOrange else AppBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = AppText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, title: String, background: Color, accent: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = background), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = AppText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PackageRow(index: Int, code: String, next: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp)) {
            Text(index.toString(), color = AppBlue, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.padding(horizontal = 7.dp))
            Column(Modifier.weight(1f)) {
                Text(code, color = AppText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (next) Text("Próxima entrega", color = AppOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
        val service = info.resolveInfo.serviceInfo
        service.packageName == context.packageName && service.name.contains("SpxAccessibilityService")
    }
}

fun abrirConfiguracaoAcessibilidade(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir as configurações de acessibilidade.", Toast.LENGTH_LONG).show()
    }
}

fun abrirSPX(context: Context) {
    val packageName = "com.shopee.spx.driver.brazil"
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Toast.makeText(context, "SPX não encontrado neste aparelho.", Toast.LENGTH_LONG).show()
        return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
