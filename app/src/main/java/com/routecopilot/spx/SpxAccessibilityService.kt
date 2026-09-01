package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.routecopilot.MainActivity
import java.util.Calendar
import java.util.GregorianCalendar

class SpxAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG =
            "RouteCopilotACC"

        private const val SPX_PACKAGE =
            "com.shopee.spx.driver.brazil"

        private const val SCAN_DELAY_MS =
            700L

        private const val NAVIGATION_DELAY_MS =
            1500L

        /*
         * O gesto ser aceito NÃO significa que a tela
         * realmente se moveu.
         *
         * Por isso verificamos também se os BRs visíveis
         * continuam exatamente iguais.
         */
        private const val MIN_TIME_WITHOUT_NEW_MS =
            12_000L

        private const val SAME_PAGE_LIMIT =
            8

        private const val STAGNANT_LIMIT =
            10

        private const val MIN_GESTURE_INTERVAL_MS =
            850L
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var importCompleted =
        false

    private var loginAvisado =
        false

    private var ultimoEstadoLogado:
        SpxState? =
        null

    private var ultimoAtLogado:
        String? =
        null

    private var ultimaQuantidade =
        0

    private var stagnantPasses =
        0

    private var mesmaPaginaConsecutiva =
        0

    private var ultimoFingerprint =
        ""

    private var ultimoNovoPacoteTime =
        SystemClock.elapsedRealtime()

    private var ultimoNavigationTime =
        0L

    private var ultimoGestureTime =
        0L

    private val scanRunnable =
        Runnable {

            executarScan()
        }

    override fun onServiceConnected() {

        super.onServiceConnected()

        Log.d(
            TAG,
            "SERVICO=ATIVO"
        )
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (
            event == null
        ) {
            return
        }

        val packageName =
            event.packageName
                ?.toString()
                ?: return

        /*
         * O serviço só trabalha sobre o SPX.
         */
        if (
            packageName !=
            SPX_PACKAGE
        ) {
            return
        }

        /*
         * Caso o usuário tenha iniciado uma nova rota
         * depois de uma importação anterior.
         */
        if (
            importCompleted &&
            SpxSessionState.state.value ==
            SpxState.UNKNOWN
        ) {

            resetInternalImport()
        }

        if (
            importCompleted
        ) {
            return
        }

        SpxSessionState.updatePackageName(
            packageName
        )

        scheduleScan(
            180L
        )
    }

    private fun scheduleScan(
        delay: Long =
            SCAN_DELAY_MS
    ) {

        handler.removeCallbacks(
            scanRunnable
        )

        handler.postDelayed(
            scanRunnable,
            delay
        )
    }

    private fun executarScan() {

        if (
            importCompleted
        ) {
            return
        }

        val root =
            rootInActiveWindow
                ?: run {

                    alterarEstado(
                        SpxState.WAITING_CONTENT,
                        "Aguardando o SPX carregar..."
                    )

                    scheduleScan()

                    return
                }

        val textos =
            mutableListOf<String>()

        coletarTextos(
            root,
            textos
        )

        if (
            textos.isEmpty()
        ) {

            alterarEstado(
                SpxState.WAITING_CONTENT,
                "Aguardando o SPX carregar..."
            )

            scheduleScan()

            return
        }

        val tela =
            textos
                .joinToString(
                    " "
                )
                .lowercase()

        // ====================================================
        // LOGIN
        // ====================================================

        if (
            pareceTelaLogin(
                tela
            )
        ) {

            alterarEstado(
                SpxState.LOGIN_REQUIRED,
                "Autentique-se normalmente no SPX."
            )

            if (
                !loginAvisado
            ) {

                loginAvisado =
                    true

                Toast.makeText(
                    applicationContext,
                    "Faça o login normalmente no SPX.",
                    Toast.LENGTH_LONG
                ).show()
            }

            scheduleScan(
                900L
            )

            return
        }

        /*
         * Se saímos da tela de login, a autenticação
         * provavelmente terminou.
         */
        if (
            loginAvisado
        ) {

            loginAvisado =
                false

            alterarEstado(
                SpxState.AUTHENTICATED,
                "Autenticação concluída."
            )

            Log.d(
                TAG,
                "LOGIN=CONCLUIDO"
            )
        }

        // ====================================================
        // TOTAL DA ROTA
        // ====================================================

        val totalDetectado =
            encontrarTotalPedidos(
                textos
            )

        if (
            totalDetectado != null
        ) {

            val anterior =
                SpxSessionState
                    .totalEsperado
                    .value

            SpxSessionState
                .updateTotalEsperado(
                    totalDetectado
                )

            if (
                anterior !=
                SpxSessionState
                    .totalEsperado
                    .value
            ) {

                Log.d(
                    TAG,
                    "TOTAL_ESPERADO=${SpxSessionState.totalEsperado.value}"
                )
            }
        }

        // ====================================================
        // AT
        // ====================================================

        val at =
            encontrarCodigoAT(
                textos
            )

        if (
            at != null
        ) {

            SpxSessionState
                .updateAtCode(
                    at
                )

            SpxSessionState
                .updateDataCarregamento(
                    extrairDataCandidataDaAT(
                        at
                    )
                )

            if (
                ultimoAtLogado !=
                at
            ) {

                ultimoAtLogado =
                    at

                Log.d(
                    TAG,
                    "ROTA_AT=DETECTADA"
                )
            }
        }

        // ====================================================
        // BRs VISÍVEIS
        // ====================================================

        val brsVisiveis =
            encontrarCodigosBR(
                textos
            )

        atualizarFingerprint(
            brsVisiveis
        )

        val novos =
            SpxSessionState
                .addPackageCodes(
                    brsVisiveis
                )

        val quantidadeAtual =
            SpxSessionState
                .packageCount
                .value

        val totalEsperado =
            SpxSessionState
                .totalEsperado
                .value

        if (
            novos > 0
        ) {

            stagnantPasses =
                0

            mesmaPaginaConsecutiva =
                0

            ultimoNovoPacoteTime =
                SystemClock.elapsedRealtime()

            if (
                quantidadeAtual !=
                ultimaQuantidade
            ) {

                ultimaQuantidade =
                    quantidadeAtual

                Log.d(
                    TAG,
                    "PACOTES_TOTAL=$quantidadeAtual"
                )
            }

        } else if (
            quantidadeAtual > 0
        ) {

            stagnantPasses++
        }

        // ====================================================
        // TOTAL CONHECIDO
        // ====================================================

        if (
            totalEsperado != null &&
            totalEsperado > 0 &&
            quantidadeAtual >=
            totalEsperado
        ) {

            Log.d(
                TAG,
                "FIM=TOTAL_ESPERADO_ATINGIDO"
            )

            concluirImportacao()

            return
        }

        // ====================================================
        // IMPORTANDO LISTA
        // ====================================================

        if (
            quantidadeAtual > 0
        ) {

            alterarEstado(
                SpxState.SCANNING_PACKAGES,
                if (
                    totalEsperado != null
                ) {

                    "Importando pedidos: $quantidadeAtual de $totalEsperado"

                } else {

                    "Importando pedidos: $quantidadeAtual encontrados"
                }
            )

            Log.d(
                TAG,
                "SCAN | TOTAL=$quantidadeAtual | ESPERADO=${totalEsperado ?: "?"} | PARADO=$stagnantPasses | PAGINA=$mesmaPaginaConsecutiva"
            )

            /*
             * IMPORTANTE:
             *
             * Antes de tentar outra rolagem verificamos
             * se a página já permaneceu igual por tempo
             * suficiente.
             *
             * Isso corrige o bug em que dispatchGesture()
             * retornava true no final e o Copilot nunca
             * concluía a importação.
             */
            if (
                verificarFimDaLista(
                    quantidadeAtual,
                    totalEsperado
                )
            ) {
                return
            }

            /*
             * Primeiro tentamos a API de scroll da própria
             * árvore de acessibilidade.
             */
            val nodeScroll =
                tentarScrollPorNodes(
                    root
                )

            if (
                nodeScroll
            ) {

                Log.d(
                    TAG,
                    "SCROLL=NODE"
                )

                scheduleScan(
                    950L
                )

                return
            }

            /*
             * Fallback: swipe automático.
             */
            val gesture =
                tentarSwipeVertical()

            if (
                gesture
            ) {

                Log.d(
                    TAG,
                    "SCROLL=GESTURE_ENVIADO"
                )

                scheduleScan(
                    1100L
                )

                return
            }

            Log.d(
                TAG,
                "SCROLL=NAO_EXECUTADO"
            )

            scheduleScan(
                1100L
            )

            return
        }

        // ====================================================
        // AT ENCONTRADA, MAS AINDA SEM PEDIDOS
        // ====================================================

        if (
            at != null
        ) {

            alterarEstado(
                SpxState.ROUTE_DETECTED,
                "Rota localizada. Abrindo pedidos..."
            )

            tentarAbrirAT(
                root,
                at
            )

            scheduleScan(
                1000L
            )

            return
        }

        // ====================================================
        // LOCALIZAR A ÁREA DE ENTREGAS
        // ====================================================

        if (
            pareceTelaAutenticada(
                tela
            )
        ) {

            alterarEstado(
                SpxState.FINDING_ROUTE,
                "Localizando rota no SPX..."
            )

            tentarAbrirEntregas(
                root
            )

            scheduleScan(
                1000L
            )

            return
        }

        alterarEstado(
            SpxState.CHECKING_SESSION,
            "Verificando sessão do SPX..."
        )

        scheduleScan(
            900L
        )
    }

    private fun atualizarFingerprint(
        brs: Set<String>
    ) {

        if (
            brs.isEmpty()
        ) {
            return
        }

        val atual =
            brs
                .sorted()
                .joinToString("|")

        if (
            atual ==
            ultimoFingerprint
        ) {

            mesmaPaginaConsecutiva++

        } else {

            ultimoFingerprint =
                atual

            /*
             * Mudou a página.
             */
            mesmaPaginaConsecutiva =
                0
        }
    }

    private fun verificarFimDaLista(
        quantidade: Int,
        totalEsperado: Int?
    ): Boolean {

        /*
         * Se o SPX mostrou um total, não usamos
         * estimativa.
         *
         * Temos que atingir esse total.
         */
        if (
            totalEsperado != null &&
            totalEsperado > 0
        ) {

            return false
        }

        if (
            quantidade <= 0
        ) {
            return false
        }

        val agora =
            SystemClock.elapsedRealtime()

        val semNovos =
            agora -
                ultimoNovoPacoteTime

        val tempoOk =
            semNovos >=
                MIN_TIME_WITHOUT_NEW_MS

        val estagnou =
            stagnantPasses >=
                STAGNANT_LIMIT

        val mesmaPagina =
            mesmaPaginaConsecutiva >=
                SAME_PAGE_LIMIT

        if (
            tempoOk &&
            estagnou &&
            mesmaPagina
        ) {

            Log.d(
                TAG,
                "FIM=LISTA_ESTAVEL | TOTAL=$quantidade"
            )

            concluirImportacao()

            return true
        }

        return false
    }

    private fun tentarScrollPorNodes(
        root: AccessibilityNodeInfo
    ): Boolean {

        val candidatos =
            mutableListOf<AccessibilityNodeInfo>()

        coletarScrollables(
            root,
            candidatos
        )

        if (
            candidatos.isEmpty()
        ) {
            return false
        }

        val ordenados =
            candidatos
                .sortedByDescending { node ->

                    val rect =
                        Rect()

                    node.getBoundsInScreen(
                        rect
                    )

                    rect.height()
                }

        for (
            node in ordenados
        ) {

            try {

                if (
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    )
                ) {

                    return true
                }

            } catch (_: Exception) {
            }
        }

        return false
    }

    private fun tentarSwipeVertical(): Boolean {

        val agora =
            SystemClock.elapsedRealtime()

        if (
            agora -
            ultimoGestureTime <
            MIN_GESTURE_INTERVAL_MS
        ) {
            return false
        }

        ultimoGestureTime =
            agora

        val metrics =
            resources.displayMetrics

        val largura =
            metrics
                .widthPixels
                .toFloat()

        val altura =
            metrics
                .heightPixels
                .toFloat()

        val x =
            largura * 0.50f

        val inicioY =
            altura * 0.78f

        val fimY =
            altura * 0.30f

        val path =
            Path().apply {

                moveTo(
                    x,
                    inicioY
                )

                lineTo(
                    x,
                    fimY
                )
            }

        val stroke =
            GestureDescription
                .StrokeDescription(
                    path,
                    0L,
                    430L
                )

        val gesture =
            GestureDescription
                .Builder()
                .addStroke(
                    stroke
                )
                .build()

        return try {

            dispatchGesture(
                gesture,
                object :
                    GestureResultCallback() {

                    override fun onCompleted(
                        gestureDescription:
                            GestureDescription?
                    ) {

                        super.onCompleted(
                            gestureDescription
                        )

                        Log.d(
                            TAG,
                            "GESTURE=COMPLETO"
                        )
                    }

                    override fun onCancelled(
                        gestureDescription:
                            GestureDescription?
                    ) {

                        super.onCancelled(
                            gestureDescription
                        )

                        Log.d(
                            TAG,
                            "GESTURE=CANCELADO"
                        )
                    }
                },
                handler
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "ERRO_GESTURE",
                e
            )

            false
        }
    }

    private fun pareceTelaLogin(
        tela: String
    ): Boolean {

        val fortes =
            listOf(
                "esqueci minha senha",
                "fazer login",
                "iniciar sessão",
                "código de verificação",
                "codigo de verificacao"
            )

        if (
            fortes.any {
                tela.contains(it)
            }
        ) {
            return true
        }

        val comuns =
            listOf(
                "login",
                "senha",
                "email",
                "e-mail",
                "telefone",
                "entrar"
            )

        return comuns.count {
            tela.contains(it)
        } >= 2
    }

    private fun pareceTelaAutenticada(
        tela: String
    ): Boolean {

        val sinais =
            listOf(
                "entrega",
                "entregas",
                "rota",
                "rotas",
                "pacote",
                "pacotes",
                "em rota",
                "escanear",
                "ocorrência",
                "entregue"
            )

        return sinais.any {
            tela.contains(it)
        }
    }

    private fun encontrarCodigoAT(
        textos: List<String>
    ): String? {

        val regex =
            Regex(
                """\bAT[A-Z0-9]{8,}\b""",
                RegexOption.IGNORE_CASE
            )

        textos.forEach { texto ->

            val normalizado =
                texto
                    .replace(
                        " ",
                        ""
                    )
                    .replace(
                        "\n",
                        ""
                    )
                    .uppercase()

            val resultado =
                regex.find(
                    normalizado
                )

            if (
                resultado != null
            ) {

                return resultado
                    .value
                    .uppercase()
            }
        }

        return null
    }

    private fun encontrarCodigosBR(
        textos: List<String>
    ): Set<String> {

        val encontrados =
            linkedSetOf<String>()

        val regex =
            Regex(
                """\bBR[A-Z0-9]{8,}\b""",
                RegexOption.IGNORE_CASE
            )

        textos.forEach { texto ->

            val normalizado =
                texto
                    .replace(
                        " ",
                        ""
                    )
                    .replace(
                        "\n",
                        ""
                    )
                    .uppercase()

            regex
                .findAll(
                    normalizado
                )
                .forEach { match ->

                    encontrados.add(
                        match
                            .value
                            .uppercase()
                    )
                }
        }

        return encontrados
    }

    private fun encontrarTotalPedidos(
        textos: List<String>
    ): Int? {

        var maior:
            Int? =
            null

        /*
         * 9/66
         * 64 / 66
         */
        val fracao =
            Regex(
                """(?<!\d)(\d{1,4})\s*/\s*(\d{1,4})(?!\d)"""
            )

        textos.forEach { texto ->

            fracao
                .findAll(
                    texto
                )
                .forEach { match ->

                    val atual =
                        match
                            .groupValues[1]
                            .toIntOrNull()

                    val total =
                        match
                            .groupValues[2]
                            .toIntOrNull()

                    if (
                        atual != null &&
                        total != null &&
                        atual >= 0 &&
                        total > 0 &&
                        atual <= total &&
                        total <= 1000
                    ) {

                        if (
                            maior == null ||
                            total > maior!!
                        ) {

                            maior =
                                total
                        }
                    }
                }
        }

        /*
         * 66 pedidos
         * 66 pacotes
         * 66 entregas
         */
        val textual =
            Regex(
                """(?<!\d)(\d{1,4})\s*(?:pedidos?|pacotes?|entregas?)(?!\w)""",
                RegexOption.IGNORE_CASE
            )

        textos.forEach { texto ->

            textual
                .findAll(
                    texto
                )
                .forEach { match ->

                    val total =
                        match
                            .groupValues[1]
                            .toIntOrNull()

                    if (
                        total != null &&
                        total > 0 &&
                        total <= 1000
                    ) {

                        if (
                            maior == null ||
                            total > maior!!
                        ) {

                            maior =
                                total
                        }
                    }
                }
        }

        return maior
    }

    private fun extrairDataCandidataDaAT(
        at: String
    ): String? {

        val regex =
            Regex(
                """^AT(\d{4})(\d{2})(\d{2})"""
            )

        val resultado =
            regex.find(
                at.uppercase()
            )
                ?: return null

        val ano =
            resultado
                .groupValues[1]
                .toIntOrNull()
                ?: return null

        val mes =
            resultado
                .groupValues[2]
                .toIntOrNull()
                ?: return null

        val dia =
            resultado
                .groupValues[3]
                .toIntOrNull()
                ?: return null

        if (
            ano !in 2020..2100
        ) {
            return null
        }

        try {

            GregorianCalendar()
                .apply {

                    isLenient =
                        false

                    set(
                        Calendar.YEAR,
                        ano
                    )

                    set(
                        Calendar.MONTH,
                        mes - 1
                    )

                    set(
                        Calendar.DAY_OF_MONTH,
                        dia
                    )

                    time
                }

        } catch (
            _: Exception
        ) {

            return null
        }

        return String.format(
            "%02d/%02d/%04d",
            dia,
            mes,
            ano
        )
    }

    private fun coletarScrollables(
        node: AccessibilityNodeInfo?,
        resultado:
            MutableList<AccessibilityNodeInfo>
    ) {

        if (
            node == null
        ) {
            return
        }

        if (
            node.isScrollable
        ) {

            resultado.add(
                node
            )
        }

        for (
            i in 0 until
            node.childCount
        ) {

            coletarScrollables(
                node.getChild(i),
                resultado
            )
        }
    }

    private fun tentarAbrirEntregas(
        root: AccessibilityNodeInfo
    ) {

        if (
            !podeNavegarAgora()
        ) {
            return
        }

        val palavras =
            listOf(
                "entregas",
                "entrega"
            )

        for (
            palavra in palavras
        ) {

            val node =
                encontrarNodePorTexto(
                    root,
                    palavra,
                    false
                )

            if (
                node != null &&
                clicarNodeOuPai(
                    node
                )
            ) {

                registrarNavegacao()

                Log.d(
                    TAG,
                    "NAV=ENTREGAS"
                )

                return
            }
        }
    }

    private fun tentarAbrirAT(
        root: AccessibilityNodeInfo,
        at: String
    ) {

        if (
            !podeNavegarAgora()
        ) {
            return
        }

        val node =
            encontrarNodePorTexto(
                root,
                at,
                true
            )

        if (
            node != null &&
            clicarNodeOuPai(
                node
            )
        ) {

            registrarNavegacao()

            Log.d(
                TAG,
                "NAV=ROTA"
            )
        }
    }

    private fun podeNavegarAgora():
        Boolean {

        val agora =
            SystemClock.elapsedRealtime()

        return agora -
            ultimoNavigationTime >=
            NAVIGATION_DELAY_MS
    }

    private fun registrarNavegacao() {

        ultimoNavigationTime =
            SystemClock.elapsedRealtime()
    }

    private fun encontrarNodePorTexto(
        node: AccessibilityNodeInfo?,
        procurado: String,
        exato: Boolean
    ): AccessibilityNodeInfo? {

        if (
            node == null
        ) {
            return null
        }

        /*
         * Não inspeciona conteúdo de senha.
         */
        if (
            !node.isPassword
        ) {

            val texto =
                node.text
                    ?.toString()
                    ?.trim()

            val descricao =
                node
                    .contentDescription
                    ?.toString()
                    ?.trim()

            if (
                textoCombina(
                    texto,
                    procurado,
                    exato
                ) ||
                textoCombina(
                    descricao,
                    procurado,
                    exato
                )
            ) {

                return node
            }
        }

        for (
            i in 0 until
            node.childCount
        ) {

            val resultado =
                encontrarNodePorTexto(
                    node.getChild(i),
                    procurado,
                    exato
                )

            if (
                resultado != null
            ) {
                return resultado
            }
        }

        return null
    }

    private fun textoCombina(
        valor: String?,
        procurado: String,
        exato: Boolean
    ): Boolean {

        if (
            valor.isNullOrBlank()
        ) {
            return false
        }

        return if (
            exato
        ) {

            valor.equals(
                procurado,
                ignoreCase = true
            )

        } else {

            valor.contains(
                procurado,
                ignoreCase = true
            )
        }
    }

    private fun clicarNodeOuPai(
        original: AccessibilityNodeInfo
    ): Boolean {

        var node:
            AccessibilityNodeInfo? =
            original

        var nivel =
            0

        while (
            node != null &&
            nivel < 7
        ) {

            if (
                node.isClickable
            ) {

                return try {

                    node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                } catch (
                    _: Exception
                ) {

                    false
                }
            }

            node =
                node.parent

            nivel++
        }

        return false
    }

    private fun coletarTextos(
        node: AccessibilityNodeInfo?,
        resultado:
            MutableList<String>
    ) {

        if (
            node == null
        ) {
            return
        }

        if (
            !node.isPassword
        ) {

            val texto =
                node.text
                    ?.toString()
                    ?.trim()

            if (
                !texto.isNullOrBlank()
            ) {

                resultado.add(
                    texto
                )
            }

            val descricao =
                node
                    .contentDescription
                    ?.toString()
                    ?.trim()

            if (
                !descricao.isNullOrBlank() &&
                descricao != texto
            ) {

                resultado.add(
                    descricao
                )
            }
        }

        for (
            i in 0 until
            node.childCount
        ) {

            coletarTextos(
                node.getChild(i),
                resultado
            )
        }
    }

    private fun concluirImportacao() {

        if (
            importCompleted
        ) {
            return
        }

        val quantidade =
            SpxSessionState
                .packageCount
                .value

        if (
            quantidade <= 0
        ) {
            return
        }

        importCompleted =
            true

        handler.removeCallbacks(
            scanRunnable
        )

        SpxSessionState
            .updateState(
                SpxState.IMPORT_COMPLETE
            )

        SpxSessionState
            .updateMessage(
                "Importação concluída."
            )

        Log.d(
            TAG,
            "IMPORT_COMPLETE | TOTAL=$quantidade"
        )

        /*
         * Marca a tela de gestão ANTES de abrir
         * o MainActivity.
         */
        SpxSessionState
            .requestOpenManagement()

        voltarParaCopilot()
    }

    private fun voltarParaCopilot() {

        SpxSessionState.updateState(
            SpxState.RETURNING_TO_COPILOT
        )

        Log.d(
            TAG,
            "RETORNO=COPILOT_SOLICITADO"
        )

        val intent =
            Intent(
                applicationContext,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

                putExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    true
                )
            }

        try {

            startActivity(
                intent
            )

            SpxSessionState
                .updateState(
                    SpxState.ROUTE_READY
                )

            SpxSessionState
                .updateMessage(
                    "Rota pronta para gestão."
                )

            Log.d(
                TAG,
                "RETORNO=COPILOT_OK"
            )

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "RETORNO=COPILOT_ERRO",
                e
            )

            /*
             * Mesmo que o Android não deixe o serviço
             * trazer a Activity automaticamente naquele
             * instante, o estado fica marcado.
             *
             * Ao abrir/retomar o RouteCopilot,
             * MainActivity vai direto para Gestão.
             */
            SpxSessionState
                .requestOpenManagement()
        }
    }

    private fun resetInternalImport() {

        handler.removeCallbacks(
            scanRunnable
        )

        importCompleted =
            false

        loginAvisado =
            false

        ultimoEstadoLogado =
            null

        ultimoAtLogado =
            null

        ultimaQuantidade =
            0

        stagnantPasses =
            0

        mesmaPaginaConsecutiva =
            0

        ultimoFingerprint =
            ""

        ultimoNovoPacoteTime =
            SystemClock.elapsedRealtime()

        ultimoNavigationTime =
            0L

        ultimoGestureTime =
            0L

        Log.d(
            TAG,
            "IMPORT=RESET"
        )
    }

    override fun onInterrupt() {

        Log.d(
            TAG,
            "SERVICO=INTERROMPIDO"
        )
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            scanRunnable
        )

        super.onDestroy()
    }
}