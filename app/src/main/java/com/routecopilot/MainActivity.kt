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

private val Background =
    Color(0xFF08111F)

private val Surface =
    Color(0xFF111C2E)

private val SurfaceSecondary =
    Color(0xFF162338)

private val Blue =
    Color(0xFF2563EB)

private val LightBlue =
    Color(0xFF38BDF8)

private val Orange =
    Color(0xFFF97316)

private val White =
    Color(0xFFF8FAFC)

private val Muted =
    Color(0xFF94A3B8)

private val Success =
    Color(0xFF22C55E)

private val Warning =
    Color(0xFFF59E0B)

class MainActivity :
    ComponentActivity() {

    private val accessibilityAtiva:
        MutableState<Boolean> =
        mutableStateOf(false)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        enableEdgeToEdge()

        accessibilityAtiva.value =
            isAccessibilityServiceEnabled(
                this
            )

        processarIntent(
            intent
        )

        setContent {

            RouteCopilotTheme {

                RouteCopilotApp(
                    accessibilityAtiva =
                        accessibilityAtiva.value
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

        processarIntent(
            intent
        )
    }

    override fun onResume() {

        super.onResume()

        accessibilityAtiva.value =
            isAccessibilityServiceEnabled(
                this
            )

        /*
         * Caso o Android tenha mantido o RouteCopilot
         * em segundo plano, o estado abaixo garante
         * abertura da Gestão ao retornar.
         */
        if (
            intent.getBooleanExtra(
                "OPEN_ROUTE_MANAGEMENT",
                false
            )
        ) {

            SpxSessionState
                .requestOpenManagement()
        }
    }

    private fun processarIntent(
        intent: Intent?
    ) {

        if (
            intent?.getBooleanExtra(
                "OPEN_ROUTE_MANAGEMENT",
                false
            ) == true
        ) {

            SpxSessionState
                .requestOpenManagement()
        }
    }
}

@Composable
fun RouteCopilotApp(
    accessibilityAtiva: Boolean
) {

    val spxState by
        SpxSessionState
            .state
            .collectAsState()

    val abrirGestao by
        SpxSessionState
            .openManagement
            .collectAsState()

    var tela by
        remember {
            mutableStateOf(
                "home"
            )
        }

    LaunchedEffect(
        abrirGestao,
        spxState
    ) {

        if (
            abrirGestao ||
            spxState ==
            SpxState.IMPORT_COMPLETE ||
            spxState ==
            SpxState.RETURNING_TO_COPILOT ||
            spxState ==
            SpxState.ROUTE_READY
        ) {

            tela =
                "gestao"
        }
    }

    when (
        tela
    ) {

        "home" -> {

            HomeScreen(

                onIniciarRota = {

                    SpxSessionState
                        .resetRoute()

                    tela =
                        "importar"
                }
            )
        }

        "importar" -> {

            ImportRouteScreen(

                accessibilityAtiva =
                    accessibilityAtiva,

                onVoltar = {

                    tela =
                        "home"
                }
            )
        }

        "gestao" -> {

            RouteManagementScreen(

                onVoltarHome = {

                    SpxSessionState
                        .clearOpenManagement()

                    tela =
                        "home"
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onIniciarRota: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Background
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(
                    horizontal = 22.dp,
                    vertical = 18.dp
                )
    ) {

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        Text(
            text = "ROUTE",
            color = LightBlue,
            fontSize = 14.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text = "COPILOT",
            color = White,
            fontSize = 38.sp,
            fontWeight =
                FontWeight.ExtraBold
        )

        Text(
            text =
                "Operação inteligente de entregas",
            color = Muted,
            fontSize = 15.sp
        )

        Spacer(
            Modifier.height(
                36.dp
            )
        )

        RouteCard(
            title =
                "Pronto para iniciar",
            description =
                "Nenhuma rota ativa no momento"
        )

        Spacer(
            Modifier.height(
                28.dp
            )
        )

        ActionButton(
            text =
                "INICIAR ROTA",
            color =
                Orange,
            onClick =
                onIniciarRota
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        ActionButton(
            text =
                "ROTAS",
            color =
                SurfaceSecondary
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        ActionButton(
            text =
                "HISTÓRICO",
            color =
                SurfaceSecondary
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        ActionButton(
            text =
                "CONFIGURAÇÕES",
            color =
                SurfaceSecondary
        )
    }
}

@Composable
private fun RouteCard(
    title: String,
    description: String
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Surface,
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .padding(
                    20.dp
                )
    ) {

        Text(
            text =
                "STATUS DO COPILOT",
            color =
                LightBlue,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                12.sp
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                title,
            color =
                White,
            fontSize =
                21.sp,
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
                description,
            color =
                Muted
        )
    }
}

@Composable
fun ImportRouteScreen(
    accessibilityAtiva: Boolean,
    onVoltar: () -> Unit
) {

    val context =
        LocalContext.current

    val state by
        SpxSessionState
            .state
            .collectAsState()

    val mensagem by
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

    val encontrados by
        SpxSessionState
            .packageCount
            .collectAsState()

    var spxAberto by
        remember {
            mutableStateOf(
                false
            )
        }

    LaunchedEffect(
        accessibilityAtiva
    ) {

        if (
            accessibilityAtiva &&
            !spxAberto &&
            state !=
            SpxState.ROUTE_READY
        ) {

            spxAberto =
                true

            SpxSessionState
                .updateState(
                    SpxState.OPENING_SPX
                )

            SpxSessionState
                .updateMessage(
                    "Abrindo SPX..."
                )

            abrirSPX(
                context
            )
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Background
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(
                    horizontal =
                        22.dp,
                    vertical =
                        18.dp
                )
    ) {

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                "NOVA ROTA",
            color =
                LightBlue,
            fontSize =
                13.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "Importar do SPX",
            color =
                White,
            fontSize =
                30.sp,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        Text(
            text =
                "O Copilot lê a rota no SPX e retorna automaticamente quando terminar.",
            color =
                Muted,
            fontSize =
                14.sp
        )

        Spacer(
            Modifier.height(
                28.dp
            )
        )

        if (
            !accessibilityAtiva
        ) {

            PermissionCard(
                context
            )

        } else {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Surface,
                            RoundedCornerShape(
                                22.dp
                            )
                        )
                        .padding(
                            20.dp
                        )
            ) {

                Text(
                    text =
                        "SPX",
                    color =
                        when (
                            state
                        ) {

                            SpxState.LOGIN_REQUIRED ->
                                Warning

                            SpxState.IMPORT_COMPLETE,
                            SpxState.ROUTE_READY ->
                                Success

                            else ->
                                LightBlue
                        },
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        12.sp
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Text(
                    text =
                        mensagem,
                    color =
                        White,
                    fontSize =
                        20.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                if (
                    state ==
                    SpxState.LOGIN_REQUIRED
                ) {

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        text =
                            "Faça o login normalmente no SPX. O Copilot continuará automaticamente.",
                        color =
                            Warning,
                        fontSize =
                            13.sp
                    )
                }

                if (
                    at != null
                ) {

                    Spacer(
                        Modifier.height(
                            16.dp
                        )
                    )

                    Text(
                        text =
                            "AT: $at",
                        color =
                            Muted,
                        fontSize =
                            14.sp
                    )
                }

                if (
                    encontrados > 0
                ) {

                    Spacer(
                        Modifier.height(
                            6.dp
                        )
                    )

                    Text(
                        text =
                            if (
                                total != null
                            ) {

                                "Pedidos: $encontrados / $total"

                            } else {

                                "Pedidos encontrados: $encontrados"
                            },
                        color =
                            Muted,
                        fontSize =
                            14.sp
                    )
                }
            }

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            ActionButton(
                text =
                    "ABRIR SPX",
                color =
                    Blue
            ) {

                abrirSPX(
                    context
                )
            }
        }

        Spacer(
            Modifier.weight(
                1f
            )
        )

        ActionButton(
            text =
                "VOLTAR",
            color =
                SurfaceSecondary,
            onClick =
                onVoltar
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )
    }
}

@Composable
fun RouteManagementScreen(
    onVoltarHome: () -> Unit
) {

    val context =
        LocalContext.current

    val clipboard =
        LocalClipboardManager.current

    val at by
        SpxSessionState
            .atCode
            .collectAsState()

    val data by
        SpxSessionState
            .dataCarregamento
            .collectAsState()

    val totalEsperado by
        SpxSessionState
            .totalEsperado
            .collectAsState()

    val importados by
        SpxSessionState
            .packageCount
            .collectAsState()

    val totalExibido =
        totalEsperado
            ?: importados

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Background
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(
                    horizontal =
                        22.dp,
                    vertical =
                        18.dp
                )
    ) {

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                "GESTÃO",
            color =
                LightBlue,
            fontSize =
                13.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "Rota ativa",
            color =
                White,
            fontSize =
                30.sp,
            fontWeight =
                FontWeight.ExtraBold
        )

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Surface,
                        RoundedCornerShape(
                            22.dp
                        )
                    )
                    .padding(
                        20.dp
                    )
        ) {

            Text(
                text =
                    "ROTA IMPORTADA",
                color =
                    Success,
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            Text(
                text =
                    at
                        ?: "AT não identificada",
                color =
                    White,
                fontSize =
                    21.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    20.dp
                )
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                InfoBlock(
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    label =
                        "CARREGAMENTO",
                    value =
                        data
                            ?: "Não identificado"
                )

                InfoBlock(
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    label =
                        "PEDIDOS",
                    value =
                        totalExibido
                            .toString()
                )
            }

            Spacer(
                Modifier.height(
                    20.dp
                )
            )

            ActionButton(
                text =
                    "COPIAR AT",
                color =
                    Blue
            ) {

                val texto =
                    buildString {

                        appendLine(
                            "AT: ${at ?: "Não identificada"}"
                        )

                        appendLine(
                            "Data de carregamento: ${data ?: "Não identificada"}"
                        )

                        append(
                            "Total de pedidos: $totalExibido"
                        )
                    }

                clipboard.setText(
                    AnnotatedString(
                        texto
                    )
                )

                Toast.makeText(
                    context,
                    "Dados da rota copiados",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        ActionButton(
            text =
                "OTIMIZAR ROTA",
            color =
                Orange
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        ActionButton(
            text =
                "MAPA",
            color =
                SurfaceSecondary
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        ActionButton(
            text =
                "INICIAR ENTREGAS",
            color =
                Blue
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        ActionButton(
            text =
                "ABRIR SPX",
            color =
                SurfaceSecondary
        ) {

            abrirSPX(
                context
            )
        }

        Spacer(
            Modifier.weight(
                1f
            )
        )

        ActionButton(
            text =
                "VOLTAR AO INÍCIO",
            color =
                SurfaceSecondary,
            onClick =
                onVoltarHome
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )
    }
}

@Composable
private fun InfoBlock(
    modifier: Modifier =
        Modifier,
    label: String,
    value: String
) {

    Column(
        modifier =
            modifier
                .background(
                    SurfaceSecondary,
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .padding(
                    14.dp
                )
    ) {

        Text(
            text =
                label,
            color =
                Muted,
            fontSize =
                10.sp,
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
                value,
            color =
                White,
            fontSize =
                16.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
fun PermissionCard(
    context: Context
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Surface,
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .padding(
                    20.dp
                )
    ) {

        Text(
            text =
                "CONFIGURAÇÃO NECESSÁRIA",
            color =
                Orange,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                12.sp
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                "Ativar integração com SPX",
            color =
                White,
            fontSize =
                20.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(
                7.dp
            )
        )

        Text(
            text =
                "O Android exige que o serviço seja ativado manualmente uma vez.",
            color =
                Muted,
            fontSize =
                14.sp
        )

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        ActionButton(
            text =
                "ATIVAR",
            color =
                Orange
        ) {

            abrirConfiguracaoAcessibilidade(
                context
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit = {}
) {

    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    58.dp
                ),
        onClick =
            onClick,
        shape =
            RoundedCornerShape(
                16.dp
            ),
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        color,
                    contentColor =
                        White
                )
    ) {

        Text(
            text =
                text,
            fontWeight =
                FontWeight.Bold,
            fontSize =
                15.sp
        )
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
                info
                    .resolveInfo
                    .serviceInfo

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

    try {

        context.startActivity(
            Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        )

    } catch (
        _: Exception
    ) {

        Toast.makeText(
            context,
            "Não foi possível abrir a acessibilidade.",
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
        intent == null
    ) {

        Toast.makeText(
            context,
            "SPX não encontrado neste aparelho.",
            Toast.LENGTH_LONG
        ).show()

        return
    }

    SpxSessionState
        .updatePackageName(
            packageName
        )

    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
    )

    context.startActivity(
        intent
    )
}