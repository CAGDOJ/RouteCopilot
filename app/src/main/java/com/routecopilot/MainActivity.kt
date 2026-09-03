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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState
import com.routecopilot.ui.theme.RouteCopilotTheme

private val Background = Color(0xFF08111F)
private val Surface = Color(0xFF111C2E)
private val SurfaceSecondary = Color(0xFF162338)
private val Blue = Color(0xFF2563EB)
private val LightBlue = Color(0xFF38BDF8)
private val Orange = Color(0xFFF97316)
private val White = Color(0xFFF8FAFC)
private val Muted = Color(0xFF94A3B8)
private val Success = Color(0xFF22C55E)
private val Warning = Color(0xFFF59E0B)

class MainActivity : ComponentActivity() {

    private val accessibilityAtiva: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        accessibilityAtiva.value = isAccessibilityServiceEnabled(this)

        setContent {
            RouteCopilotTheme {
                RouteCopilotApp(accessibilityAtiva.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityAtiva.value = isAccessibilityServiceEnabled(this)
    }
}

@Composable
fun RouteCopilotApp(accessibilityAtiva: Boolean) {
    val spxState by SpxSessionState.state.collectAsState()
    var tela by remember { mutableStateOf("home") }

    LaunchedEffect(spxState) {
        when (spxState) {
            SpxState.IMPORT_COMPLETE,
            SpxState.RETURNING_TO_COPILOT,
            SpxState.ROUTE_READY -> tela = "gestao"

            SpxState.NO_ACTIVE_ROUTE -> tela = "importar"
            else -> Unit
        }
    }

    when (tela) {
        "home" -> HomeScreen {
            SpxSessionState.resetRoute()
            tela = "importar"
        }

        "importar" -> ImportRouteScreen(
            accessibilityAtiva = accessibilityAtiva,
            onVoltar = { tela = "home" }
        )

        "gestao" -> RouteManagementScreen(
            onVoltarHome = { tela = "home" }
        )
    }
}

@Composable
fun HomeScreen(onIniciarRota: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("ROUTE", color = LightBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("COPILOT", color = White, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
        Text("Operação inteligente de entregas", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(36.dp))
        RouteCard("Pronto para iniciar", "Nenhuma rota ativa no momento")
        Spacer(Modifier.height(28.dp))
        ActionButton("INICIAR ROTA", Orange, onIniciarRota)
        Spacer(Modifier.height(12.dp))
        ActionButton("ROTAS", SurfaceSecondary)
        Spacer(Modifier.height(12.dp))
        ActionButton("HISTÓRICO", SurfaceSecondary)
        Spacer(Modifier.height(12.dp))
        ActionButton("CONFIGURAÇÕES", SurfaceSecondary)
    }
}

@Composable
private fun RouteCard(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Text("STATUS DO COPILOT", color = LightBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(description, color = Muted)
    }
}

@Composable
fun ImportRouteScreen(
    accessibilityAtiva: Boolean,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val state by SpxSessionState.state.collectAsState()
    val mensagem by SpxSessionState.statusMessage.collectAsState()
    val at by SpxSessionState.atCode.collectAsState()
    val encontrados by SpxSessionState.packageCount.collectAsState()
    var spxAberto by remember { mutableStateOf(false) }

    LaunchedEffect(accessibilityAtiva, state) {
        if (accessibilityAtiva && !spxAberto && state == SpxState.UNKNOWN) {
            spxAberto = true
            SpxSessionState.updateState(SpxState.OPENING_SPX)
            SpxSessionState.updateMessage("Abrindo SPX...")
            abrirSPX(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("IMPORTAÇÃO SPX", color = LightBlue, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(28.dp))

        if (!accessibilityAtiva) {
            PermissionCard(context)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "SPX",
                    color = when (state) {
                        SpxState.NO_ACTIVE_ROUTE, SpxState.LOGIN_REQUIRED -> Warning
                        SpxState.IMPORT_COMPLETE, SpxState.ROUTE_READY -> Success
                        else -> LightBlue
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(14.dp))

                if (state == SpxState.NO_ACTIVE_ROUTE) {
                    Text("Nenhuma rota ativa", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "O SPX está mostrando Em Rota (0). Quando uma rota estiver disponível, toque em VERIFICAR NOVAMENTE.",
                        color = Muted,
                        fontSize = 15.sp
                    )
                } else {
                    Text(mensagem, color = White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }

                if (state == SpxState.LOGIN_REQUIRED) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Faça o login normalmente no SPX. O Copilot continuará sozinho depois.",
                        color = Warning,
                        fontSize = 14.sp
                    )
                }

                if (at != null) {
                    Spacer(Modifier.height(16.dp))
                    Text("AT: $at", color = Muted)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Pedidos encontrados: $encontrados", color = Muted, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))

            if (state == SpxState.NO_ACTIVE_ROUTE) {
                ActionButton("VERIFICAR NOVAMENTE", Orange) {
                    spxAberto = false
                    SpxSessionState.resetRoute()
                }
            } else {
                ActionButton("ABRIR SPX", Blue) {
                    abrirSPX(context)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        ActionButton("VOLTAR", SurfaceSecondary, onVoltar)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun RouteManagementScreen(onVoltarHome: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val at by SpxSessionState.atCode.collectAsState()
    val data by SpxSessionState.dataCarregamento.collectAsState()
    val totalEsperado by SpxSessionState.totalEsperado.collectAsState()
    val importados by SpxSessionState.packageCount.collectAsState()
    val totalExibido = totalEsperado ?: importados

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("GESTÃO", color = LightBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Rota ativa", color = White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Text("ROTA IMPORTADA", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(at ?: "AT não identificada", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBlock(Modifier.weight(1f), "CARREGAMENTO", data ?: "Não identificado")
                InfoBlock(Modifier.weight(1f), "PEDIDOS", totalExibido.toString())
            }

            Spacer(Modifier.height(20.dp))
            ActionButton("COPIAR AT", Blue) {
                val texto = buildString {
                    appendLine("AT: ${at ?: "Não identificada"}")
                    appendLine("Data de carregamento: ${data ?: "Não identificada"}")
                    append("Total de pedidos: $totalExibido")
                }
                clipboard.setText(AnnotatedString(texto))
                Toast.makeText(context, "Dados da rota copiados", Toast.LENGTH_SHORT).show()
            }
        }

        Spacer(Modifier.height(18.dp))
        ActionButton("OTIMIZAR ROTA", Orange)
        Spacer(Modifier.height(12.dp))
        ActionButton("MAPA", SurfaceSecondary)
        Spacer(Modifier.height(12.dp))
        ActionButton("INICIAR ENTREGAS", Blue)
        Spacer(Modifier.height(12.dp))
        ActionButton("ABRIR SPX", SurfaceSecondary) { abrirSPX(context) }
        Spacer(Modifier.weight(1f))
        ActionButton("VOLTAR AO INÍCIO", SurfaceSecondary, onVoltarHome)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoBlock(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .background(SurfaceSecondary, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(value, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PermissionCard(context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Text("CONFIGURAÇÃO NECESSÁRIA", color = Orange, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Ativar integração com SPX", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        ActionButton("ATIVAR", Orange) { abrirConfiguracaoAcessibilidade(context) }
    }
}

@Composable
fun ActionButton(text: String, color: Color, onClick: () -> Unit = {}) {
    Button(
        modifier = Modifier.fillMaxWidth().height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = White)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            val service = info.resolveInfo.serviceInfo
            service.packageName == context.packageName &&
                service.name.contains("SpxAccessibilityService")
        }
}

fun abrirConfiguracaoAcessibilidade(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    } catch (_: Exception) {
        Toast.makeText(context, "Não foi possível abrir as configurações.", Toast.LENGTH_LONG).show()
    }
}

fun abrirSPX(context: Context) {
    val packageName = "com.shopee.spx.driver.brazil"
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)

    if (intent == null) {
        Toast.makeText(context, "SPX não encontrado.", Toast.LENGTH_LONG).show()
        return
    }

    SpxSessionState.updatePackageName(packageName)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
