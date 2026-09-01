package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.PendingIntent
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

class SpxAccessibilityService :
    AccessibilityService() {

    companion object {

        private const val TAG =
            "RouteCopilotACC"

        private const val SPX_PACKAGE =
            "com.shopee.spx.driver.brazil"

        private const val SCAN_DELAY =
            700L

        private const val NAV_DELAY =
            1300L

        /*
         * Sem total conhecido, só consideramos final
         * depois de várias páginas realmente iguais.
         */
        private const val SAME_VIEW_LIMIT =
            12

        private const val MIN_NO_NEW_TIME =
            12_000L
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var importCompleted =
        false

    private var loginAvisado =
        false

    private var ultimoEstado:
        SpxState? =
        null

    private var ultimoAt:
        String? =
        null

    private var ultimoTotalLogado:
        Int? =
        null

    private var ultimaQuantidade =
        0

    private var ultimoNavigationTime =
        0L

    private var ultimoGestureTime =
        0L

    private var ultimoNovoPacoteTime =
        SystemClock.elapsedRealtime()

    private var ultimoFingerprint =
        ""

    private var mesmaViewport =
        0

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

        if (event == null) {
            return
        }

        val packageName =
            event.packageName
                ?.toString()
                ?: return

        if (
            packageName !=
            SPX_PACKAGE
        ) {
            return
        }

        if (
            importCompleted &&
            SpxSessionState.state.value ==
            SpxState.UNKNOWN
        ) {
            resetInternal()
        }

        if (importCompleted) {
            return
        }

        scheduleScan(
            180L
        )
    }

    private fun scheduleScan(
        delay: Long = SCAN_DELAY
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

        if (importCompleted) {
            return
        }

        val root =
            rootInActiveWindow
                ?: run {

                    alterarEstado(
                        SpxState.WAITING_CONTENT,
                        "Aguardando SPX..."
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

        if (textos.isEmpty()) {

            scheduleScan()

            return
        }

        val tela =
            textos
                .joinToString(" ")
                .lowercase()

        // ==================================================
        // LOGIN
        // ==================================================

        if (
            pareceTelaLogin(
                tela
            )
        ) {

            alterarEstado(
                SpxState.LOGIN_REQUIRED,
                "Faça login normalmente no SPX."
            )

            if (!loginAvisado) {

                loginAvisado =
                    true

                Toast.makeText(
                    applicationContext,
                    "Faça login no SPX. O Copilot continua automaticamente.",
                    Toast.LENGTH_LONG
                ).show()
            }

            scheduleScan(
                900L
            )

            return
        }

        loginAvisado =
            false

        // ==================================================
        // TOTAL REAL DA ROTA
        // ==================================================

        val total =
            encontrarTotalPedidos(
                textos
            )

        if (total != null) {

            SpxSessionState
                .updateTotalEsperado(
                    total
                )

            if (
                ultimoTotalLogado !=
                total
            ) {

                ultimoTotalLogado =
                    total

                Log.d(
                    TAG,
                    "TOTAL_ESPERADO=$total"
                )
            }
        }

        // ==================================================
        // AT
        // ==================================================

        val at =
            encontrarAT(
                textos
            )

        if (at != null) {

            SpxSessionState
                .updateAtCode(
                    at
                )

            SpxSessionState
                .updateDataCarregamento(
                    extrairDataAT(
                        at
                    )
                )

            if (
                ultimoAt !=
                at
            ) {

                ultimoAt =
                    at

                Log.d(
                    TAG,
                    "ROTA_AT=DETECTADA"
                )
            }
        }

        // ==================================================
        // PEDIDOS + ENDEREÇOS
        // ==================================================

        val encontrados =
            encontrarPacotes(
                root,
                textos
            )

        val fingerprint =
            encontrados
                .keys
                .sorted()
                .joinToString("|")

        if (
            fingerprint.isNotBlank() &&
            fingerprint ==
            ultimoFingerprint
        ) {

            mesmaViewport++

        } else if (
            fingerprint.isNotBlank()
        ) {

            ultimoFingerprint =
                fingerprint

            mesmaViewport =
                0
        }

        val novos =
            SpxSessionState
                .addOrUpdatePackages(
                    encontrados
                )

        val quantidade =
            SpxSessionState
                .packageCount
                .value

        val totalEsperado =
            SpxSessionState
                .totalEsperado
                .value

        if (novos > 0) {

            ultimoNovoPacoteTime =
                SystemClock.elapsedRealtime()

            mesmaViewport =
                0
        }

        if (
            quantidade !=
            ultimaQuantidade
        ) {

            ultimaQuantidade =
                quantidade

            Log.d(
                TAG,
                "PACOTES_TOTAL=$quantidade"
            )
        }

        // ==================================================
        // FINAL CORRETO PELO TOTAL
        // ==================================================

        if (
            totalEsperado != null &&
            totalEsperado > 0 &&
            quantidade >=
            totalEsperado
        ) {

            Log.d(
                TAG,
                "FIM=TOTAL_ATINGIDO"
            )

            concluirImportacao()

            return
        }

        // ==================================================
        // JÁ ESTAMOS NA LISTA
        // ==================================================

        if (quantidade > 0) {

            alterarEstado(
                SpxState.SCANNING_PACKAGES,
                if (
                    totalEsperado != null
                ) {
                    "Lendo pedidos: $quantidade de $totalEsperado"
                } else {
                    "Lendo pedidos: $quantidade encontrados"
                }
            )

            /*
             * Se a viewport ficou igual duas vezes,
             * não confiamos no ACTION_SCROLL_FORWARD.
             *
             * Forçamos swipe físico.
             */
            val forceGesture =
                mesmaViewport >= 2

            var movimentou =
                false

            if (!forceGesture) {

                movimentou =
                    tentarScrollNode(
                        root
                    )

                if (movimentou) {

                    Log.d(
                        TAG,
                        "SCROLL=NODE"
                    )
                }
            }

            if (!movimentou) {

                movimentou =
                    tentarSwipe()

                if (movimentou) {

                    Log.d(
                        TAG,
                        "SCROLL=GESTURE"
                    )
                }
            }

            /*
             * Se conhecemos o total, NUNCA finalizamos
             * antes dele.
             */
            if (
                totalEsperado == null
            ) {

                verificarFimSemTotal(
                    quantidade
                )
            }

            scheduleScan(
                1000L
            )

            return
        }

        // ==================================================
        // AT ENCONTRADA
        // ==================================================

        if (at != null) {

            alterarEstado(
                SpxState.ROUTE_DETECTED,
                "Abrindo rota..."
            )

            tentarAbrirAT(
                root,
                at
            )

            scheduleScan(
                900L
            )

            return
        }

        // ==================================================
        // PROCURAR ENTREGAS
        // ==================================================

        if (
            pareceTelaAutenticada(
                tela
            )
        ) {

            alterarEstado(
                SpxState.FINDING_ROUTE,
                "Localizando rota..."
            )

            tentarAbrirEntregas(
                root
            )

            scheduleScan(
                900L
            )

            return
        }

        alterarEstado(
            SpxState.CHECKING_SESSION,
            "Verificando SPX..."
        )

        scheduleScan(
            900L
        )
    }

    // ======================================================
    // TOTAL DE PEDIDOS
    // ======================================================

    private fun encontrarTotalPedidos(
        textos: List<String>
    ): Int? {

        var maior:
            Int? =
            null

        fun considerar(
            valor: Int?
        ) {

            if (
                valor == null ||
                valor <= 0 ||
                valor > 1000
            ) {
                return
            }

            if (
                maior == null ||
                valor > maior!!
            ) {
                maior = valor
            }
        }

        val tudo =
            textos.joinToString(
                " | "
            )

        /*
         * 3/5
         * 64 / 66
         */
        Regex(
            """(?<!\d)(\d{1,4})\s*/\s*(\d{1,4})(?!\d)"""
        )
            .findAll(tudo)
            .forEach {

                val atual =
                    it.groupValues[1]
                        .toIntOrNull()

                val total =
                    it.groupValues[2]
                        .toIntOrNull()

                if (
                    atual != null &&
                    total != null &&
                    atual <= total
                ) {

                    considerar(
                        total
                    )
                }
            }

        /*
         * "5 pedidos"
         */
        Regex(
            """(?<!\d)(\d{1,4})\s*(?:pedidos?|pacotes?|entregas?)\b""",
            RegexOption.IGNORE_CASE
        )
            .findAll(tudo)
            .forEach {

                considerar(
                    it.groupValues[1]
                        .toIntOrNull()
                )
            }

        /*
         * "Pedidos: 5"
         */
        Regex(
            """\b(?:pedidos?|pacotes?|entregas?)\s*[:\-]?\s*(\d{1,4})(?!\d)""",
            RegexOption.IGNORE_CASE
        )
            .findAll(tudo)
            .forEach {

                considerar(
                    it.groupValues[1]
                        .toIntOrNull()
                )
            }

        /*
         * Alguns apps colocam:
         *
         * TextView = PEDIDOS
         * próximo TextView = 5
         */
        for (
            i in 0 until
                textos.size - 1
        ) {

            val primeiro =
                textos[i]
                    .trim()
                    .lowercase()

            val segundo =
                textos[i + 1]
                    .trim()

            if (
                primeiro.matches(
                    Regex(
                        """pedidos?|pacotes?|entregas?"""
                    )
                )
            ) {

                considerar(
                    segundo
                        .toIntOrNull()
                )
            }

            val numero =
                textos[i]
                    .trim()
                    .toIntOrNull()

            val label =
                textos[i + 1]
                    .trim()
                    .lowercase()

            if (
                numero != null &&
                label.matches(
                    Regex(
                        """pedidos?|pacotes?|entregas?"""
                    )
                )
            ) {

                considerar(
                    numero
                )
            }
        }

        return maior
    }

    // ======================================================
    // AT
    // ======================================================

    private fun encontrarAT(
        textos: List<String>
    ): String? {

        val regex =
            Regex(
                """\bAT[A-Z0-9]{8,}\b""",
                RegexOption.IGNORE_CASE
            )

        textos.forEach {

            val texto =
                it.replace(
                    " ",
                    ""
                )
                    .uppercase()

            val match =
                regex.find(
                    texto
                )

            if (match != null) {

                return match
                    .value
                    .uppercase()
            }
        }

        return null
    }

    // ======================================================
    // BR + ENDEREÇO
    // ======================================================

    private fun encontrarPacotes(
        root: AccessibilityNodeInfo,
        textosTela: List<String>
    ): Map<String, String?> {

        val encontrados =
            linkedMapOf<String, String?>()

        percorrerNodes(
            root
        ) { node ->

            if (node.isPassword) {
                return@percorrerNodes
            }

            val valores =
                listOfNotNull(
                    node.text
                        ?.toString(),
                    node.contentDescription
                        ?.toString()
                )

            valores.forEach { valor ->

                encontrarBRs(
                    valor
                )
                    .forEach { br ->

                        val endereco =
                            encontrarEnderecoProximo(
                                node,
                                br
                            )

                        val existente =
                            encontrados[br]

                        if (
                            existente.isNullOrBlank() ||
                            !endereco.isNullOrBlank()
                        ) {

                            encontrados[br] =
                                endereco
                        }
                    }
            }
        }

        /*
         * Tela de detalhes:
         * se só existe um BR visível, tenta associar
         * um endereço presente em qualquer região da tela.
         */
        if (
            encontrados.size == 1
        ) {

            val br =
                encontrados.keys.first()

            if (
                encontrados[br]
                    .isNullOrBlank()
            ) {

                selecionarEndereco(
                    textosTela,
                    br
                )?.let {

                    encontrados[br] =
                        it
                }
            }
        }

        return encontrados
    }

    private fun encontrarBRs(
        texto: String
    ): Set<String> {

        val resultado =
            linkedSetOf<String>()

        Regex(
            """\bBR[A-Z0-9]{8,}\b""",
            RegexOption.IGNORE_CASE
        )
            .findAll(
                texto
                    .replace(
                        " ",
                        ""
                    )
                    .uppercase()
            )
            .forEach {

                resultado.add(
                    it.value
                        .uppercase()
                )
            }

        return resultado
    }

    private fun encontrarEnderecoProximo(
        original: AccessibilityNodeInfo,
        br: String
    ): String? {

        var node:
            AccessibilityNodeInfo? =
            original

        repeat(5) {

            if (node == null) {
                return@repeat
            }

            val textos =
                mutableListOf<String>()

            coletarTextos(
                node,
                textos
            )

            selecionarEndereco(
                textos,
                br
            )?.let {

                return it
            }

            node =
                node?.parent
        }

        return null
    }

    private fun selecionarEndereco(
        textos: List<String>,
        br: String
    ): String? {

        val ignorar =
            listOf(
                "entregue",
                "ocorrência",
                "ocorrencia",
                "escanear",
                "ligar",
                "mensagem",
                "telefone",
                "pedido",
                "pedidos",
                "rota",
                "iniciar entregas",
                "voltar",
                br.lowercase()
            )

        val linhas =
            textos
                .map {
                    it.trim()
                }
                .filter {
                    it.length >= 4
                }
                .distinct()
                .filter { linha ->

                    val lower =
                        linha.lowercase()

                    ignorar.none {
                        lower ==
                            it ||
                            lower.startsWith(
                                "$it:"
                            )
                    }
                }

        val ruaRegex =
            Regex(
                """\b(rua|r\.|avenida|av\.|travessa|tv\.|passagem|estrada|rodovia|alameda|conjunto|residencial|vila|br-\d+)\b""",
                RegexOption.IGNORE_CASE
            )

        val indice =
            linhas.indexOfFirst {
                ruaRegex.containsMatchIn(
                    it
                )
            }

        if (indice >= 0) {

            return linhas
                .drop(indice)
                .take(3)
                .joinToString(
                    ", "
                )
                .take(240)
        }

        /*
         * Fallback: linha com número + CEP/localidade.
         */
        val alternativa =
            linhas.firstOrNull {

                it.any(
                    Char::isDigit
                ) &&
                    (
                        it.contains(",") ||
                            it.contains("-")
                        )
            }

        return alternativa
    }

    // ======================================================
    // SCROLL
    // ======================================================

    private fun tentarScrollNode(
        root: AccessibilityNodeInfo
    ): Boolean {

        val scrollables =
            mutableListOf<AccessibilityNodeInfo>()

        percorrerNodes(
            root
        ) {

            if (it.isScrollable) {

                scrollables.add(
                    it
                )
            }
        }

        val ordenados =
            scrollables
                .sortedByDescending {

                    val rect =
                        Rect()

                    it.getBoundsInScreen(
                        rect
                    )

                    rect.height()
                }

        ordenados.forEach {

            try {

                if (
                    it.performAction(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD
                    )
                ) {

                    return true
                }

            } catch (_: Exception) {
            }
        }

        return false
    }

    private fun tentarSwipe(): Boolean {

        val agora =
            SystemClock.elapsedRealtime()

        if (
            agora -
            ultimoGestureTime <
            700L
        ) {

            return false
        }

        ultimoGestureTime =
            agora

        val largura =
            resources
                .displayMetrics
                .widthPixels
                .toFloat()

        val altura =
            resources
                .displayMetrics
                .heightPixels
                .toFloat()

        /*
         * Alterna posição horizontal.
         * Ajuda em telas onde o centro intercepta
         * algum componente.
         */
        val posicoes =
            floatArrayOf(
                0.50f,
                0.75f,
                0.25f
            )

        val indice =
            (
                (
                    agora /
                        1000L
                    ) %
                    posicoes.size
                )
                .toInt()

        val x =
            largura *
                posicoes[indice]

        val inicio =
            altura * 0.80f

        val fim =
            altura * 0.25f

        val path =
            Path().apply {

                moveTo(
                    x,
                    inicio
                )

                lineTo(
                    x,
                    fim
                )
            }

        val gesture =
            GestureDescription
                .Builder()
                .addStroke(
                    GestureDescription
                        .StrokeDescription(
                            path,
                            0L,
                            480L
                        )
                )
                .build()

        return try {

            dispatchGesture(
                gesture,
                null,
                handler
            )

        } catch (_: Exception) {

            false
        }
    }

    private fun verificarFimSemTotal(
        quantidade: Int
    ) {

        if (
            quantidade <= 0
        ) {
            return
        }

        val tempoSemNovo =
            SystemClock.elapsedRealtime() -
                ultimoNovoPacoteTime

        if (
            mesmaViewport >=
            SAME_VIEW_LIMIT &&
            tempoSemNovo >=
            MIN_NO_NEW_TIME
        ) {

            Log.d(
                TAG,
                "FIM=LISTA_CONFIRMADA | TOTAL=$quantidade"
            )

            concluirImportacao()
        }
    }

    // ======================================================
    // NAVEGAÇÃO SPX
    // ======================================================

    private fun tentarAbrirEntregas(
        root: AccessibilityNodeInfo
    ) {

        if (!podeNavegar()) {
            return
        }

        listOf(
            "entregas",
            "entrega"
        )
            .forEach {

                val node =
                    encontrarNodeTexto(
                        root,
                        it,
                        false
                    )

                if (
                    node != null &&
                    clicarNodeOuPai(
                        node
                    )
                ) {

                    ultimoNavigationTime =
                        SystemClock
                            .elapsedRealtime()

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

        if (!podeNavegar()) {
            return
        }

        val node =
            encontrarNodeTexto(
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

            ultimoNavigationTime =
                SystemClock
                    .elapsedRealtime()

            Log.d(
                TAG,
                "NAV=ROTA"
            )
        }
    }

    private fun podeNavegar(): Boolean {

        return (
            SystemClock.elapsedRealtime() -
                ultimoNavigationTime
            ) >=
            NAV_DELAY
    }

    private fun encontrarNodeTexto(
        node: AccessibilityNodeInfo?,
        procurado: String,
        exato: Boolean
    ): AccessibilityNodeInfo? {

        if (node == null) {
            return null
        }

        if (!node.isPassword) {

            val valores =
                listOfNotNull(
                    node.text
                        ?.toString(),
                    node.contentDescription
                        ?.toString()
                )

            valores.forEach {

                val bate =
                    if (exato) {

                        it.trim()
                            .equals(
                                procurado,
                                true
                            )

                    } else {

                        it.contains(
                            procurado,
                            true
                        )
                    }

                if (bate) {

                    return node
                }
            }
        }

        for (
            i in 0 until
                node.childCount
        ) {

            encontrarNodeTexto(
                node.getChild(i),
                procurado,
                exato
            )?.let {

                return it
            }
        }

        return null
    }

    private fun clicarNodeOuPai(
        original: AccessibilityNodeInfo
    ): Boolean {

        var node:
            AccessibilityNodeInfo? =
            original

        repeat(7) {

            if (node == null) {
                return false
            }

            if (
                node?.isClickable ==
                true
            ) {

                return try {

                    node!!.performAction(
                        AccessibilityNodeInfo
                            .ACTION_CLICK
                    )

                } catch (_: Exception) {

                    false
                }
            }

            node =
                node?.parent
        }

        return false
    }

    // ======================================================
    // RETORNO AO ROUTECOPILOT
    // ======================================================

    private fun concluirImportacao() {

        if (importCompleted) {
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

        Toast.makeText(
            applicationContext,
            "$quantidade pedidos importados",
            Toast.LENGTH_SHORT
        ).show()

        retornarAoCopilot()
    }

    private fun retornarAoCopilot() {

        SpxSessionState
            .updateState(
                SpxState.RETURNING_TO_COPILOT
            )

        /*
         * Primeiro sai da tela atual do SPX.
         */
        performGlobalAction(
            GLOBAL_ACTION_BACK
        )

        handler.postDelayed(
            {

                val intent =
                    Intent(
                        applicationContext,
                        MainActivity::class.java
                    ).apply {

                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP

                        putExtra(
                            "OPEN_ROUTE_MANAGEMENT",
                            true
                        )
                    }

                try {

                    /*
                     * PendingIntent costuma ser mais confiável
                     * para trazer Activity à frente a partir
                     * de AccessibilityService.
                     */
                    val pending =
                        PendingIntent
                            .getActivity(
                                applicationContext,
                                1001,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or
                                    PendingIntent.FLAG_IMMUTABLE
                            )

                    pending.send()

                    SpxSessionState
                        .updateState(
                            SpxState.ROUTE_READY
                        )

                    Log.d(
                        TAG,
                        "RETORNO_COPILOT=OK"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "PENDING_INTENT_FALHOU",
                        e
                    )

                    try {

                        startActivity(
                            intent
                        )

                        SpxSessionState
                            .updateState(
                                SpxState.ROUTE_READY
                            )

                        Log.d(
                            TAG,
                            "RETORNO_COPILOT=FALLBACK_OK"
                        )

                    } catch (
                        erro:
                        Exception
                    ) {

                        Log.e(
                            TAG,
                            "RETORNO_COPILOT=ERRO",
                            erro
                        )
                    }
                }
            },
            550L
        )
    }

    // ======================================================
    // HELPERS
    // ======================================================

    private fun pareceTelaLogin(
        tela: String
    ): Boolean {

        val fortes =
            listOf(
                "esqueci minha senha",
                "fazer login",
                "iniciar sessão",
                "codigo de verificação",
                "código de verificação"
            )

        if (
            fortes.any {
                tela.contains(it)
            }
        ) {
            return true
        }

        val sinais =
            listOf(
                "login",
                "senha",
                "email",
                "e-mail",
                "entrar"
            )

        return sinais.count {
            tela.contains(it)
        } >= 2
    }

    private fun pareceTelaAutenticada(
        tela: String
    ): Boolean {

        return listOf(
            "entrega",
            "entregas",
            "rota",
            "pacote",
            "em rota",
            "escanear",
            "ocorrência",
            "entregue"
        )
            .any {
                tela.contains(it)
            }
    }

    private fun alterarEstado(
        estado: SpxState,
        mensagem: String
    ) {

        SpxSessionState
            .updateState(
                estado
            )

        SpxSessionState
            .updateMessage(
                mensagem
            )

        if (
            ultimoEstado !=
            estado
        ) {

            ultimoEstado =
                estado

            Log.d(
                TAG,
                "STATUS=$estado"
            )
        }
    }

    private fun coletarTextos(
        node: AccessibilityNodeInfo?,
        resultado: MutableList<String>
    ) {

        if (node == null) {
            return
        }

        if (!node.isPassword) {

            node.text
                ?.toString()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    resultado.add(
                        it
                    )
                }

            node.contentDescription
                ?.toString()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    resultado.add(
                        it
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

    private fun percorrerNodes(
        node: AccessibilityNodeInfo?,
        bloco: (
            AccessibilityNodeInfo
        ) -> Unit
    ) {

        if (node == null) {
            return
        }

        bloco(
            node
        )

        for (
            i in 0 until
                node.childCount
        ) {

            percorrerNodes(
                node.getChild(i),
                bloco
            )
        }
    }

    private fun extrairDataAT(
        at: String
    ): String? {

        val match =
            Regex(
                """^AT(\d{4})(\d{2})(\d{2})"""
            )
                .find(
                    at.uppercase()
                )
                ?: return null

        val ano =
            match.groupValues[1]
                .toIntOrNull()
                ?: return null

        val mes =
            match.groupValues[2]
                .toIntOrNull()
                ?: return null

        val dia =
            match.groupValues[3]
                .toIntOrNull()
                ?: return null

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

        } catch (_: Exception) {

            return null
        }

        return String.format(
            "%02d/%02d/%04d",
            dia,
            mes,
            ano
        )
    }

    private fun resetInternal() {

        importCompleted =
            false

        loginAvisado =
            false

        ultimoEstado =
            null

        ultimoAt =
            null

        ultimoTotalLogado =
            null

        ultimaQuantidade =
            0

        ultimoFingerprint =
            ""

        mesmaViewport =
            0

        ultimoNavigationTime =
            0L

        ultimoGestureTime =
            0L

        ultimoNovoPacoteTime =
            SystemClock.elapsedRealtime()

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