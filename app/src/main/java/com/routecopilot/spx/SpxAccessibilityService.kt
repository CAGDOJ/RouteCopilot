package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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
        private const val TAG = "RouteCopilotACC"
        private const val SPX_PACKAGE = "com.shopee.spx.driver.brazil"

        private const val SCAN_DELAY_MS = 450L
        private const val NAVIGATION_DELAY_MS = 1400L

        private const val MAX_STAGNANT_PASSES = 7
        private const val MAX_SCROLL_FAILURES = 3
    }

    private val handler = Handler(Looper.getMainLooper())

    private var importCompleted = false
    private var loginAvisado = false

    private var ultimoEstadoLogado: SpxState? = null
    private var ultimoAtLogado: String? = null

    private var ultimaQuantidade = 0
    private var stagnantPasses = 0
    private var scrollFailures = 0
    private var ultimoNavigationTime = 0L

    private val scanRunnable = Runnable {
        executarScan()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.d(TAG, "SERVICO=ATIVO")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        val packageName =
            event.packageName?.toString() ?: return

        if (packageName != SPX_PACKAGE) {
            return
        }

        if (
            importCompleted &&
            SpxSessionState.state.value == SpxState.UNKNOWN
        ) {
            resetInternalImport()
        }

        if (importCompleted) {
            return
        }

        SpxSessionState.updatePackageName(packageName)

        scheduleScan(120L)
    }

    private fun scheduleScan(delay: Long = SCAN_DELAY_MS) {

        handler.removeCallbacks(scanRunnable)

        handler.postDelayed(
            scanRunnable,
            delay
        )
    }

    private fun executarScan() {

        if (importCompleted) return

        val root = rootInActiveWindow ?: run {
            scheduleScan()
            return
        }

        val textos = mutableListOf<String>()

        coletarTextos(
            root,
            textos
        )

        if (textos.isEmpty()) {

            alterarEstado(
                SpxState.WAITING_CONTENT,
                "Aguardando o SPX carregar..."
            )

            scheduleScan()
            return
        }

        val tela =
            textos
                .joinToString(" ")
                .lowercase()

        // ====================================================
        // LOGIN
        // ====================================================

        if (pareceTelaLogin(tela)) {

            alterarEstado(
                SpxState.LOGIN_REQUIRED,
                "Autentique-se normalmente no SPX."
            )

            if (!loginAvisado) {

                loginAvisado = true

                Toast.makeText(
                    applicationContext,
                    "Autentique-se no SPX. O RouteCopilot continuará automaticamente.",
                    Toast.LENGTH_LONG
                ).show()
            }

            scheduleScan(700L)
            return
        }

        loginAvisado = false

        // ====================================================
        // SESSÃO AUTENTICADA
        // ====================================================

        if (pareceTelaAutenticada(tela)) {

            if (
                SpxSessionState.state.value ==
                SpxState.LOGIN_REQUIRED
            ) {

                alterarEstado(
                    SpxState.AUTHENTICATED,
                    "Autenticação concluída."
                )
            }
        }

        // ====================================================
        // TOTAL DE PEDIDOS
        // ====================================================

        val total =
            encontrarTotalPedidos(textos)

        if (total != null) {
            SpxSessionState.updateTotalEsperado(total)
        }

        // ====================================================
        // AT
        // ====================================================

        val at =
            encontrarCodigoAT(textos)

        if (at != null) {

            SpxSessionState.updateAtCode(at)

            /*
             * Só preenchemos a data quando a parte inicial
             * da AT formar uma data de calendário válida.
             *
             * Isso ainda deve ser validado com mais ATs reais.
             */
            val data =
                extrairDataCandidataDaAT(at)

            SpxSessionState.updateDataCarregamento(data)

            if (ultimoAtLogado != at) {

                ultimoAtLogado = at

                Log.d(
                    TAG,
                    "ROTA_AT=DETECTADA"
                )
            }
        }

        // ====================================================
        // BRs VISÍVEIS
        // ====================================================

        val brs =
            encontrarCodigosBR(textos)

        val novos =
            SpxSessionState.addPackageCodes(brs)

        val quantidadeAtual =
            SpxSessionState.packageCount.value

        val totalEsperado =
            SpxSessionState.totalEsperado.value

        if (novos > 0) {

            stagnantPasses = 0
            scrollFailures = 0

            if (quantidadeAtual != ultimaQuantidade) {

                ultimaQuantidade = quantidadeAtual

                /*
                 * Não registramos os códigos BR no log.
                 * Apenas a quantidade.
                 */
                Log.d(
                    TAG,
                    "PACOTES_TOTAL=$quantidadeAtual"
                )
            }

        } else if (quantidadeAtual > 0) {

            stagnantPasses++
        }

        // ====================================================
        // ATINGIU O TOTAL INFORMADO PELO SPX
        // ====================================================

        if (
            totalEsperado != null &&
            totalEsperado > 0 &&
            quantidadeAtual >= totalEsperado
        ) {

            concluirImportacao()
            return
        }

        // ====================================================
        // PERCORRER LISTA DE PEDIDOS
        // ====================================================

        if (quantidadeAtual > 0) {

            alterarEstado(
                SpxState.SCANNING_PACKAGES,
                criarMensagemImportacao(
                    quantidadeAtual,
                    totalEsperado
                )
            )

            val scrollable =
                encontrarMelhorScrollable(root)

            if (scrollable != null) {

                val rolou =
                    scrollable.performAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    )

                if (rolou) {

                    scrollFailures = 0

                    if (
                        stagnantPasses >=
                        MAX_STAGNANT_PASSES
                    ) {

                        concluirImportacao()
                        return
                    }

                    scheduleScan(SCAN_DELAY_MS)
                    return

                } else {

                    scrollFailures++
                }

            } else {

                scrollFailures++
            }

            if (
                scrollFailures >=
                MAX_SCROLL_FAILURES
            ) {

                concluirImportacao()
                return
            }

            scheduleScan()
            return
        }

        // ====================================================
        // ENCONTROU AT MAS AINDA NÃO ENCONTROU BR
        // ====================================================

        if (at != null) {

            alterarEstado(
                SpxState.ROUTE_DETECTED,
                "Rota localizada. Abrindo pedidos..."
            )

            tentarAbrirAT(
                root,
                at
            )

            scheduleScan(650L)
            return
        }

        // ====================================================
        // AUTENTICADO, PROCURAR ÁREA DE ENTREGAS
        // ====================================================

        if (pareceTelaAutenticada(tela)) {

            alterarEstado(
                SpxState.FINDING_ROUTE,
                "Localizando sua rota no SPX..."
            )

            tentarAbrirEntregas(root)

            scheduleScan(700L)
            return
        }

        alterarEstado(
            SpxState.CHECKING_SESSION,
            "Verificando sessão do SPX..."
        )

        scheduleScan(700L)
    }

    private fun criarMensagemImportacao(
        quantidade: Int,
        total: Int?
    ): String {

        return if (
            total != null &&
            total > 0
        ) {
            "Importando pedidos: $quantidade de $total"
        } else {
            "Importando pedidos: $quantidade encontrados"
        }
    }

    private fun alterarEstado(
        estado: SpxState,
        mensagem: String
    ) {

        SpxSessionState.updateState(estado)
        SpxSessionState.updateMessage(mensagem)

        if (ultimoEstadoLogado != estado) {

            ultimoEstadoLogado = estado

            Log.d(
                TAG,
                "STATUS=$estado"
            )
        }
    }

    private fun pareceTelaLogin(
        tela: String
    ): Boolean {

        val sinaisFortes =
            listOf(
                "esqueci minha senha",
                "fazer login",
                "iniciar sessão",
                "código de verificação",
                "codigo de verificacao",
                "código de confirmação",
                "codigo de confirmacao"
            )

        if (
            sinaisFortes.any {
                tela.contains(it)
            }
        ) {
            return true
        }

        val sinais =
            listOf(
                "login",
                "senha",
                "e-mail",
                "email",
                "telefone",
                "entrar"
            )

        return sinais.count {
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
                    .replace(" ", "")
                    .uppercase()

            val resultado =
                regex.find(normalizado)

            if (resultado != null) {
                return resultado.value.uppercase()
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
                    .replace(" ", "")
                    .uppercase()

            regex
                .findAll(normalizado)
                .forEach { resultado ->

                    encontrados.add(
                        resultado.value.uppercase()
                    )
                }
        }

        return encontrados
    }

    private fun encontrarTotalPedidos(
        textos: List<String>
    ): Int? {

        var maiorTotal: Int? = null

        val regexFracao =
            Regex(
                """\b(\d{1,4})\s*/\s*(\d{1,4})\b"""
            )

        textos.forEach { texto ->

            regexFracao
                .findAll(texto)
                .forEach { resultado ->

                    val total =
                        resultado
                            .groupValues
                            .getOrNull(2)
                            ?.toIntOrNull()

                    if (
                        total != null &&
                        total > 0 &&
                        (maiorTotal == null || total > maiorTotal!!)
                    ) {
                        maiorTotal = total
                    }
                }
        }

        val regexTexto =
            Regex(
                """\b(\d{1,4})\s+(?:pedidos?|pacotes?)\b""",
                RegexOption.IGNORE_CASE
            )

        textos.forEach { texto ->

            regexTexto
                .findAll(texto)
                .forEach { resultado ->

                    val total =
                        resultado
                            .groupValues
                            .getOrNull(1)
                            ?.toIntOrNull()

                    if (
                        total != null &&
                        total > 0 &&
                        (maiorTotal == null || total > maiorTotal!!)
                    ) {
                        maiorTotal = total
                    }
                }
        }

        return maiorTotal
    }

    private fun extrairDataCandidataDaAT(
        at: String
    ): String? {

        val regex =
            Regex(
                """^AT(\d{4})(\d{2})(\d{2})"""
            )

        val resultado =
            regex.find(at.uppercase())
                ?: return null

        val ano =
            resultado.groupValues[1].toIntOrNull()
                ?: return null

        val mes =
            resultado.groupValues[2].toIntOrNull()
                ?: return null

        val dia =
            resultado.groupValues[3].toIntOrNull()
                ?: return null

        if (ano !in 2020..2100) {
            return null
        }

        try {

            GregorianCalendar().apply {

                isLenient = false

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

    private fun encontrarMelhorScrollable(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        val candidatos =
            mutableListOf<AccessibilityNodeInfo>()

        coletarScrollables(
            root,
            candidatos
        )

        if (candidatos.isEmpty()) {
            return null
        }

        return candidatos.maxByOrNull { node ->

            val rect = Rect()

            node.getBoundsInScreen(rect)

            rect.height()
        }
    }

    private fun coletarScrollables(
        node: AccessibilityNodeInfo?,
        resultado: MutableList<AccessibilityNodeInfo>
    ) {

        if (node == null) return

        if (node.isScrollable) {

            val possuiScrollForward =
                node.actionList.any {
                    it.id ==
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }

            if (possuiScrollForward) {
                resultado.add(node)
            }
        }

        for (i in 0 until node.childCount) {

            coletarScrollables(
                node.getChild(i),
                resultado
            )
        }
    }

    private fun tentarAbrirEntregas(
        root: AccessibilityNodeInfo
    ) {

        if (!podeNavegarAgora()) return

        val palavras =
            listOf(
                "entrega",
                "entregas"
            )

        for (palavra in palavras) {

            val node =
                encontrarNodePorTexto(
                    root,
                    palavra,
                    false
                )

            if (
                node != null &&
                clicarNodeOuPai(node)
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

        if (!podeNavegarAgora()) return

        val node =
            encontrarNodePorTexto(
                root,
                at,
                true
            )

        if (
            node != null &&
            clicarNodeOuPai(node)
        ) {

            registrarNavegacao()

            Log.d(
                TAG,
                "NAV=ROTA"
            )
        }
    }

    private fun podeNavegarAgora(): Boolean {

        val agora =
            SystemClock.elapsedRealtime()

        return agora - ultimoNavigationTime >=
            NAVIGATION_DELAY_MS
    }

    private fun registrarNavegacao() {

        ultimoNavigationTime =
            SystemClock.elapsedRealtime()
    }

    private fun encontrarNodePorTexto(
        node: AccessibilityNodeInfo?,
        textoProcurado: String,
        exato: Boolean
    ): AccessibilityNodeInfo? {

        if (node == null) return null

        if (!node.isPassword) {

            val texto =
                node.text
                    ?.toString()
                    ?.trim()

            val descricao =
                node.contentDescription
                    ?.toString()
                    ?.trim()

            if (
                textoCombina(
                    texto,
                    textoProcurado,
                    exato
                ) ||
                textoCombina(
                    descricao,
                    textoProcurado,
                    exato
                )
            ) {
                return node
            }
        }

        for (i in 0 until node.childCount) {

            val encontrado =
                encontrarNodePorTexto(
                    node.getChild(i),
                    textoProcurado,
                    exato
                )

            if (encontrado != null) {
                return encontrado
            }
        }

        return null
    }

    private fun textoCombina(
        valor: String?,
        procurado: String,
        exato: Boolean
    ): Boolean {

        if (valor.isNullOrBlank()) {
            return false
        }

        return if (exato) {

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
        nodeOriginal: AccessibilityNodeInfo
    ): Boolean {

        var node: AccessibilityNodeInfo? =
            nodeOriginal

        var profundidade = 0

        while (
            node != null &&
            profundidade < 6
        ) {

            if (node.isClickable) {

                return node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }

            node = node.parent
            profundidade++
        }

        return false
    }

    private fun coletarTextos(
        node: AccessibilityNodeInfo?,
        resultado: MutableList<String>
    ) {

        if (node == null) return

        /*
         * Campos marcados como senha não entram
         * no processamento do RouteCopilot.
         */
        if (!node.isPassword) {

            val texto =
                node.text
                    ?.toString()
                    ?.trim()

            if (!texto.isNullOrBlank()) {
                resultado.add(texto)
            }

            val descricao =
                node.contentDescription
                    ?.toString()
                    ?.trim()

            if (
                !descricao.isNullOrBlank() &&
                descricao != texto
            ) {
                resultado.add(descricao)
            }
        }

        for (i in 0 until node.childCount) {

            coletarTextos(
                node.getChild(i),
                resultado
            )
        }
    }

    private fun concluirImportacao() {

        if (importCompleted) return

        val quantidade =
            SpxSessionState.packageCount.value

        if (quantidade <= 0) return

        importCompleted = true

        SpxSessionState.updateState(
            SpxState.IMPORT_COMPLETE
        )

        SpxSessionState.updateMessage(
            "Importação concluída."
        )

        Log.d(
            TAG,
            "IMPORT_COMPLETE | TOTAL=$quantidade"
        )

        Toast.makeText(
            applicationContext,
            "Rota importada: $quantidade pedidos.",
            Toast.LENGTH_SHORT
        ).show()

        voltarParaCopilot()
    }

    private fun voltarParaCopilot() {

        SpxSessionState.updateState(
            SpxState.RETURNING_TO_COPILOT
        )

        val intent =
            Intent(
                applicationContext,
                MainActivity::class.java
            ).apply {

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

                putExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    true
                )
            }

        try {

            startActivity(intent)

            SpxSessionState.updateState(
                SpxState.ROUTE_READY
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "ERRO_RETORNO_COPILOT",
                e
            )
        }
    }

    private fun resetInternalImport() {

        importCompleted = false
        loginAvisado = false

        ultimoEstadoLogado = null
        ultimoAtLogado = null

        ultimaQuantidade = 0
        stagnantPasses = 0
        scrollFailures = 0

        ultimoNavigationTime = 0L
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