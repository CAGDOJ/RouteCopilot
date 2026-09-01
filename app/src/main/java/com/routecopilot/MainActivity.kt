package com.routecopilot

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.routecopilot.spx.SpxPackage
import com.routecopilot.spx.SpxSessionState
import com.routecopilot.spx.SpxState
import com.routecopilot.ui.theme.RouteCopilotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Bg =
    Color(0xFF07111F)

private val Card =
    Color(0xFF101D30)

private val Card2 =
    Color(0xFF16253A)

private val Blue =
    Color(0xFF2E6BE6)

private val Cyan =
    Color(0xFF38BDF8)

private val Orange =
    Color(0xFFFF7417)

private val White =
    Color(0xFFF8FAFC)

private val Muted =
    Color(0xFF94A3B8)

private val Green =
    Color(0xFF22C55E)

enum class AppScreen {
    HOME,
    IMPORT,
    ROUTE,
    MAP
}

object AppNavigation {

    private val _screen =
        MutableStateFlow(
            AppScreen.HOME
        )

    val screen =
        _screen.asStateFlow()

    fun home() {
        _screen.value =
            AppScreen.HOME
    }

    fun importRoute() {
        _screen.value =
            AppScreen.IMPORT
    }

    fun route() {
        _screen.value =
            AppScreen.ROUTE
    }

    fun map() {
        _screen.value =
            AppScreen.MAP
    }
}

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
            isAccessibilityEnabled(
                this
            )

        processIntent(
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

    override fun onResume() {
        super.onResume()

        accessibilityAtiva.value =
            isAccessibilityEnabled(
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

        processIntent(
            intent
        )
    }

    private fun processIntent(
        intent: Intent?
    ) {

        if (
            intent?.getBooleanExtra(
                "OPEN_ROUTE_MANAGEMENT",
                false
            ) == true
        ) {

            AppNavigation.route()

            intent.removeExtra(
                "OPEN_ROUTE_MANAGEMENT"
            )
        }
    }
}

@Composable
fun RouteCopilotApp(
    accessibilityAtiva: Boolean
) {

    val tela by
        AppNavigation
            .screen
            .collectAsState()

    val estadoSPX by
        SpxSessionState
            .state
            .collectAsState()

    LaunchedEffect(
        estadoSPX
    ) {

        if (
            estadoSPX ==
            SpxState.ROUTE_READY
        ) {

            AppNavigation.route()
        }
    }

    when (tela) {

        AppScreen.HOME -> {

            HomeScreen()
        }

        AppScreen.IMPORT -> {

            ImportScreen(
                accessibilityAtiva
            )
        }

        AppScreen.ROUTE -> {

            RouteScreen()
        }

        AppScreen.MAP -> {

            RouteMapScreen()
        }
    }
}

@Composable
private fun HomeScreen() {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Bg)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(22.dp)
    ) {

        Spacer(
            Modifier.height(14.dp)
        )

        Text(
            "ROUTE",
            color = Cyan,
            fontWeight =
                FontWeight.Bold,
            fontSize = 13.sp
        )

        Text(
            "COPILOT",
            color = White,
            fontWeight =
                FontWeight.Black,
            fontSize = 37.sp
        )

        Text(
            "Sua operação de entregas em um só lugar",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(
            Modifier.height(34.dp)
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Card,
                        RoundedCornerShape(
                            22.dp
                        )
                    )
                    .padding(20.dp)
        ) {

            Text(
                "PRONTO",
                color = Green,
                fontSize = 11.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(7.dp)
            )

            Text(
                "Nenhuma rota ativa",
                color = White,
                fontSize = 21.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "Importe sua rota do SPX para começar.",
                color = Muted,
                fontSize = 13.sp
            )
        }

        Spacer(
            Modifier.height(24.dp)
        )

        MainButton(
            "IMPORTAR ROTA DO SPX",
            Orange
        ) {

            SpxSessionState
                .resetRoute()

            AppNavigation
                .importRoute()
        }
    }
}

@Composable
private fun ImportScreen(
    accessibilityAtiva:
        Boolean
) {

    val context =
        LocalContext.current

    val mensagem by
        SpxSessionState
            .statusMessage
            .collectAsState()

    val at by
        SpxSessionState
            .atCode
            .collectAsState()

    val quantidade by
        SpxSessionState
            .packageCount
            .collectAsState()

    val total by
        SpxSessionState
            .totalEsperado
            .collectAsState()

    var abriuSPX by
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
            !abriuSPX
        ) {

            abriuSPX =
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
                .background(Bg)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(22.dp)
    ) {

        Text(
            "NOVA ROTA",
            color = Cyan,
            fontSize = 12.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            "Importando do SPX",
            color = White,
            fontSize = 29.sp,
            fontWeight =
                FontWeight.Black
        )

        Spacer(
            Modifier.height(25.dp)
        )

        if (
            !accessibilityAtiva
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Card,
                            RoundedCornerShape(
                                20.dp
                            )
                        )
                        .padding(20.dp)
            ) {

                Text(
                    "ACESSIBILIDADE",
                    color = Orange,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    "Ative o RouteCopilot uma única vez nas configurações do Android.",
                    color = White
                )

                Spacer(
                    Modifier.height(17.dp)
                )

                MainButton(
                    "ATIVAR",
                    Orange
                ) {

                    context.startActivity(
                        Intent(
                            Settings
                                .ACTION_ACCESSIBILITY_SETTINGS
                        )
                    )
                }
            }

        } else {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Card,
                            RoundedCornerShape(
                                20.dp
                            )
                        )
                        .padding(20.dp)
            ) {

                Text(
                    "SPX CONECTADO",
                    color = Green,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 11.sp
                )

                Spacer(
                    Modifier.height(10.dp)
                )

                Text(
                    mensagem,
                    color = White,
                    fontSize = 19.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                if (
                    at != null
                ) {

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Text(
                        "AT  $at",
                        color = Cyan
                    )
                }

                if (
                    quantidade > 0
                ) {

                    Spacer(
                        Modifier.height(5.dp)
                    )

                    Text(
                        if (
                            total != null
                        ) {
                            "$quantidade / $total pedidos"
                        } else {
                            "$quantidade pedidos encontrados"
                        },
                        color = Muted
                    )
                }
            }

            Spacer(
                Modifier.height(15.dp)
            )

            MainButton(
                "ABRIR SPX",
                Blue
            ) {

                abrirSPX(
                    context
                )
            }
        }

        Spacer(
            Modifier.weight(1f)
        )

        MainButton(
            "VOLTAR",
            Card2
        ) {

            AppNavigation.home()
        }
    }
}

@Composable
private fun RouteScreen() {

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

    val total by
        SpxSessionState
            .totalEsperado
            .collectAsState()

    val packages by
        SpxSessionState
            .packages
            .collectAsState()

    val lista =
        packages
            .values
            .toList()

    val totalExibido =
        total ?: lista.size

    val enderecos =
        lista.count {
            !it.address
                .isNullOrBlank()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Bg)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
    ) {

        Column(
            modifier =
                Modifier
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 14.dp
                    )
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        "ROTA ATIVA",
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        at ?: "AT não identificada",
                        color = White,
                        fontSize = 24.sp,
                        fontWeight =
                            FontWeight.Black
                    )
                }

                Text(
                    data ?: "",
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            Spacer(
                Modifier.height(17.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        9.dp
                    )
            ) {

                MiniStat(
                    Modifier.weight(1f),
                    "PEDIDOS",
                    totalExibido.toString()
                )

                MiniStat(
                    Modifier.weight(1f),
                    "LIDOS",
                    lista.size.toString()
                )

                MiniStat(
                    Modifier.weight(1f),
                    "ENDEREÇOS",
                    "$enderecos"
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Card,
                            RoundedCornerShape(
                                18.dp
                            )
                        )
                        .padding(16.dp)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "MAPA DA ROTA",
                            color = White,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 15.sp
                        )

                        Text(
                            if (
                                enderecos > 0
                            ) {
                                "$enderecos endereços identificados"
                            } else {
                                "Aguardando endereços do SPX"
                            },
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }

                    SmallButton(
                        "ABRIR"
                    ) {

                        AppNavigation.map()
                    }
                }
            }

            Spacer(
                Modifier.height(11.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        9.dp
                    )
            ) {

                CompactButton(
                    Modifier.weight(1f),
                    "COPIAR AT",
                    Blue
                ) {

                    val texto =
                        """
AT: ${at ?: "Não identificada"}
Data de carregamento: ${data ?: "Não identificada"}
Total de pedidos: $totalExibido
                        """.trimIndent()

                    clipboard.setText(
                        AnnotatedString(
                            texto
                        )
                    )

                    Toast
                        .makeText(
                            context,
                            "Rota copiada",
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }

                CompactButton(
                    Modifier.weight(1f),
                    "ABRIR SPX",
                    Card2
                ) {

                    abrirSPX(
                        context
                    )
                }
            }

            Spacer(
                Modifier.height(18.dp)
            )

            Text(
                "PEDIDOS DA ROTA",
                color = Muted,
                fontSize = 13.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(8.dp)
            )
        }

        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        horizontal =
                            20.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            itemsIndexed(
                lista
            ) {
                    index,
                    pedido ->

                PedidoCard(
                    index + 1,
                    pedido
                )
            }

            item {

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Bg)
                    .padding(
                        horizontal =
                            20.dp,
                        vertical =
                            10.dp
                    )
        ) {

            MainButton(
                "INICIAR ENTREGAS",
                Orange
            ) {

                abrirSPX(
                    context
                )
            }
        }
    }
}

@Composable
private fun PedidoCard(
    numero: Int,
    pedido: SpxPackage
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Card,
                    RoundedCornerShape(
                        16.dp
                    )
                )
                .padding(15.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .background(
                        Orange,
                        CircleShape
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                numero
                    .toString()
                    .padStart(
                        2,
                        '0'
                    ),
                color = White,
                fontWeight =
                    FontWeight.Black,
                fontSize = 14.sp
            )
        }

        Spacer(
            Modifier.size(13.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                pedido.code,
                color = White,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 15.sp
            )

            if (
                !pedido.address
                    .isNullOrBlank()
            ) {

                Spacer(
                    Modifier.height(3.dp)
                )

                Text(
                    pedido.address,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .size(9.dp)
                    .background(
                        Cyan,
                        CircleShape
                    )
        )
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier,
    titulo: String,
    valor: String
) {

    Column(
        modifier =
            modifier
                .background(
                    Card,
                    RoundedCornerShape(
                        15.dp
                    )
                )
                .padding(13.dp)
    ) {

        Text(
            titulo,
            color = Muted,
            fontSize = 9.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            valor,
            color = White,
            fontSize = 20.sp,
            fontWeight =
                FontWeight.Black
        )
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class MapStop(
    val packageCode: String,
    val address: String,
    val point: GeoPoint
)

data class MapState(
    val loading: Boolean = true,
    val stops: List<MapStop> =
        emptyList(),
    val route: List<GeoPoint> =
        emptyList(),
    val error: String? = null
)

@Composable
private fun RouteMapScreen() {

    val context =
        LocalContext.current

    val packages by
        SpxSessionState
            .packages
            .collectAsState()

    var state by
        remember {
            mutableStateOf(
                MapState()
            )
        }

    LaunchedEffect(
        packages
    ) {

        val comEndereco =
            packages
                .values
                .filter {
                    !it.address
                        .isNullOrBlank()
                }

        if (
            comEndereco.isEmpty()
        ) {

            state =
                MapState(
                    loading = false,
                    error =
                        "O SPX ainda não expôs os endereços dos pedidos."
                )

            return@LaunchedEffect
        }

        state =
            MapState(
                loading = true
            )

        val stops =
            mutableListOf<MapStop>()

        comEndereco
            .forEach { pedido ->

                val endereco =
                    pedido.address
                        ?: return@forEach

                geocodeAddress(
                    context,
                    endereco
                )?.let { point ->

                    stops.add(
                        MapStop(
                            packageCode =
                                pedido.code,
                            address =
                                endereco,
                            point =
                                point
                        )
                    )
                }
            }

        if (
            stops.isEmpty()
        ) {

            state =
                MapState(
                    loading = false,
                    error =
                        "Não consegui localizar os endereços no mapa."
                )

            return@LaunchedEffect
        }

        val rota =
            requestCompleteRoute(
                stops.map {
                    it.point
                }
            )

        state =
            MapState(
                loading = false,
                stops = stops,
                route = rota
            )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Bg)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            18.dp,
                        vertical =
                            12.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            SmallButton(
                "VOLTAR"
            ) {

                AppNavigation.route()
            }

            Spacer(
                Modifier.size(13.dp)
            )

            Column {

                Text(
                    "MAPA DA ROTA",
                    color = White,
                    fontWeight =
                        FontWeight.Black,
                    fontSize = 20.sp
                )

                Text(
                    "${state.stops.size} pontos localizados",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        when {

            state.loading -> {

                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        CircularProgressIndicator(
                            color = Cyan
                        )

                        Spacer(
                            Modifier.height(
                                12.dp
                            )
                        )

                        Text(
                            "Montando mapa...",
                            color = White
                        )
                    }
                }
            }

            state.error != null -> {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(25.dp),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            state.error!!,
                            color = White,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            "O mapa só pode posicionar um pedido quando o endereço estiver disponível no SPX.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            else -> {

                val html =
                    remember(
                        state
                    ) {

                        buildMapHtml(
                            state.stops,
                            state.route
                        )
                    }

                AndroidView(
                    modifier =
                        Modifier.fillMaxSize(),
                    factory = {

                        WebView(it)
                            .apply {

                                settings
                                    .javaScriptEnabled =
                                    true

                                settings
                                    .domStorageEnabled =
                                    true

                                webViewClient =
                                    WebViewClient()

                                loadDataWithBaseURL(
                                    "https://routecopilot.local/",
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                    },
                    update = {

                        it.loadDataWithBaseURL(
                            "https://routecopilot.local/",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private suspend fun geocodeAddress(
    context: Context,
    endereco: String
): GeoPoint? {

    return withContext(
        Dispatchers.IO
    ) {

        try {

            val geocoder =
                Geocoder(
                    context,
                    Locale(
                        "pt",
                        "BR"
                    )
                )

            val resultados =
                geocoder
                    .getFromLocationName(
                        endereco,
                        1
                    )

            val local =
                resultados
                    ?.firstOrNull()
                    ?: return@withContext null

            GeoPoint(
                latitude =
                    local.latitude,
                longitude =
                    local.longitude
            )

        } catch (_: Exception) {

            null
        }
    }
}

private suspend fun requestCompleteRoute(
    points: List<GeoPoint>
): List<GeoPoint> {

    if (
        points.size < 2
    ) {
        return points
    }

    return withContext(
        Dispatchers.IO
    ) {

        val rota =
            mutableListOf<GeoPoint>()

        try {

            /*
             * Faz em blocos de até 25 pontos para
             * suportar também rotas grandes.
             */
            var inicio =
                0

            while (
                inicio <
                points.size - 1
            ) {

                val fim =
                    minOf(
                        inicio + 25,
                        points.size
                    )

                val bloco =
                    points.subList(
                        inicio,
                        fim
                    )

                val trecho =
                    requestRouteChunk(
                        bloco
                    )

                if (
                    trecho.isNotEmpty()
                ) {

                    if (
                        rota.isNotEmpty()
                    ) {

                        rota.addAll(
                            trecho.drop(
                                1
                            )
                        )

                    } else {

                        rota.addAll(
                            trecho
                        )
                    }
                }

                if (
                    fim ==
                    points.size
                ) {
                    break
                }

                inicio =
                    fim - 1
            }

        } catch (_: Exception) {
        }

        if (
            rota.isEmpty()
        ) {
            points
        } else {
            rota
        }
    }
}

private fun requestRouteChunk(
    points: List<GeoPoint>
): List<GeoPoint> {

    if (
        points.size < 2
    ) {
        return points
    }

    val coords =
        points.joinToString(
            ";"
        ) {

            "${it.longitude},${it.latitude}"
        }

    val url =
        URL(
            "https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson"
        )

    val connection =
        url.openConnection()
            as HttpURLConnection

    connection.connectTimeout =
        12_000

    connection.readTimeout =
        15_000

    connection.requestMethod =
        "GET"

    connection.setRequestProperty(
        "User-Agent",
        "RouteCopilot/1.0"
    )

    try {

        val texto =
            connection
                .inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        val json =
            JSONObject(
                texto
            )

        val routes =
            json.getJSONArray(
                "routes"
            )

        if (
            routes.length() == 0
        ) {

            return emptyList()
        }

        val coordinates =
            routes
                .getJSONObject(0)
                .getJSONObject(
                    "geometry"
                )
                .getJSONArray(
                    "coordinates"
                )

        val resultado =
            mutableListOf<GeoPoint>()

        for (
            i in 0 until
                coordinates.length()
        ) {

            val item =
                coordinates
                    .getJSONArray(i)

            resultado.add(
                GeoPoint(
                    latitude =
                        item.getDouble(1),
                    longitude =
                        item.getDouble(0)
                )
            )
        }

        return resultado

    } finally {

        connection.disconnect()
    }
}

private fun buildMapHtml(
    stops: List<MapStop>,
    route: List<GeoPoint>
): String {

    val markers =
        stops
            .mapIndexed {
                    index,
                    stop ->

                """
L.marker([
    ${stop.point.latitude},
    ${stop.point.longitude}
]).addTo(map)
.bindPopup(
    '${index + 1}. ${escapeJs(stop.packageCode)}<br>${escapeJs(stop.address)}'
);
                """.trimIndent()
            }
            .joinToString(
                "\n"
            )

    val routePoints =
        route.joinToString(
            ","
        ) {

            "[${it.latitude},${it.longitude}]"
        }

    val bounds =
        stops.joinToString(
            ","
        ) {

            "[${it.point.latitude},${it.point.longitude}]"
        }

    return """
<!DOCTYPE html>
<html>
<head>

<meta
    name="viewport"
    content="width=device-width, initial-scale=1.0">

<link
    rel="stylesheet"
    href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>

<script
    src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
</script>

<style>

html,
body,
#map {
    width: 100%;
    height: 100%;
    margin: 0;
    padding: 0;
    background: #07111F;
}

.leaflet-control-attribution {
    font-size: 9px;
}

</style>

</head>

<body>

<div id="map"></div>

<script>

const map = L.map(
    'map',
    {
        zoomControl: true
    }
);

L.tileLayer(
    'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap'
    }
).addTo(map);

$markers

const route = [
    $routePoints
];

if (route.length > 1) {

    L.polyline(
        route,
        {
            weight: 5,
            opacity: 0.85
        }
    ).addTo(map);
}

const bounds = [
    $bounds
];

if (bounds.length > 1) {

    map.fitBounds(
        bounds,
        {
            padding: [30, 30]
        }
    );

} else if (bounds.length === 1) {

    map.setView(
        bounds[0],
        16
    );
}

</script>

</body>
</html>
    """.trimIndent()
}

private fun escapeJs(
    value: String
): String {

    return value
        .replace(
            "\\",
            "\\\\"
        )
        .replace(
            "'",
            "\\'"
        )
        .replace(
            "\n",
            " "
        )
}

@Composable
private fun MainButton(
    texto: String,
    cor: Color,
    onClick: () -> Unit
) {

    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
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
                        cor,
                    contentColor =
                        White
                )
    ) {

        Text(
            texto,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun CompactButton(
    modifier: Modifier,
    texto: String,
    cor: Color,
    onClick: () -> Unit
) {

    Button(
        modifier =
            modifier
                .height(47.dp),
        onClick =
            onClick,
        shape =
            RoundedCornerShape(
                14.dp
            ),
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        cor,
                    contentColor =
                        White
                )
    ) {

        Text(
            texto,
            fontSize = 11.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun SmallButton(
    texto: String,
    onClick: () -> Unit
) {

    Button(
        onClick =
            onClick,
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        Blue,
                    contentColor =
                        White
                ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {

        Text(
            texto,
            fontSize = 10.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
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
        .any {

            val service =
                it.resolveInfo
                    .serviceInfo

            service.packageName ==
                context.packageName &&
                service.name.contains(
                    "SpxAccessibilityService"
                )
        }
}

private fun abrirSPX(
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
            "SPX não encontrado.",
            Toast.LENGTH_LONG
        ).show()

        return
    }

    context.startActivity(
        intent
    )
}