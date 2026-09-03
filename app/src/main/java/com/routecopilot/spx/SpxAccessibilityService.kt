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
        private const val TAG = "RouteCopilotACC"
        private const val SPX_PACKAGE = "com.shopee.spx.driver.brazil"
        private const val SCAN_DELAY_MS = 700L
        private const val NAVIGATION_DELAY_MS = 1500L
        private const val MIN_GESTURE_INTERVAL_MS = 750L
        private const val NO_ROUTE_CONFIRM_PASSES = 3
        private const val NO_ROUTE_MIN_TIME_MS = 1800L
        private const val MIN_TIME_WITHOUT_NEW_MS = 15000L
        private const val SAME_PAGE_LIMIT = 12
        private const val STAGNANT_LIMIT = 20
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fluxoFinalizado = false
    private var loginAvisado = false
    private var ultimoEstadoLogado: SpxState? = null
    private var ultimoAtLogado: String? = null
    private var ultimaQuantidade = 0
    private var ultimoNovoPacoteTime = SystemClock.elapsedRealtime()
    private var ultimoNavigationTime = 0L
    private var ultimoGestureTime = 0L
    private var stagnantPasses = 0
    private var ultimoFingerprint = ""
    private var mesmaPaginaConsecutiva = 0
    private var noRoutePasses = 0
    private var noRouteFirstSeen = 0L

    private val scanRunnable = Runnable { executarScan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "SERVICO=ATIVO")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != SPX_PACKAGE) return

        if (fluxoFinalizado && SpxSessionState.state.value == SpxState.UNKNOWN) {
            resetInternalImport()
        }
        if (fluxoFinalizado) return

        SpxSessionState.updatePackageName(packageName)
        scheduleScan(180L)
    }

    private fun scheduleScan(delay: Long = SCAN_DELAY_MS) {
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, delay)
    }

    private fun executarScan() {
        if (fluxoFinalizado) return

        val root = rootInActiveWindow ?: run {
            alterarEstado(SpxState.WAITING_CONTENT, "Aguardando o SPX carregar...")
            scheduleScan()
            return
        }

        val textos = mutableListOf<String>()
        coletarTextos(root, textos)

        if (textos.isEmpty()) {
            alterarEstado(SpxState.WAITING_CONTENT, "Aguardando o SPX carregar...")
            scheduleScan()
            return
        }

        val tela = textos.joinToString(" ").lowercase()

        if (pareceTelaLogin(tela)) {
            resetNoRouteDetection()
            alterarEstado(SpxState.LOGIN_REQUIRED, "Autentique-se normalmente no SPX.")
            if (!loginAvisado) {
                loginAvisado = true
                Toast.makeText(applicationContext, "Faça o login normalmente no SPX.", Toast.LENGTH_LONG).show()
            }
            scheduleScan(900L)
            return
        }

        loginAvisado = false

        val at = encontrarCodigoAT(textos)
        val brsVisiveis = encontrarCodigosBR(textos)
        val totalDetectado = encontrarTotalPedidos(textos)

        // Trata explicitamente a tela autenticada sem rota: "Em Rota (0)".
        if (
            pareceTelaSemRota(textos, tela) &&
            at == null &&
            brsVisiveis.isEmpty() &&
            SpxSessionState.packageCount.value == 0
        ) {
            if (noRoutePasses == 0) noRouteFirstSeen = SystemClock.elapsedRealtime()
            noRoutePasses++
            val tempo = SystemClock.elapsedRealtime() - noRouteFirstSeen
            Log.d(TAG, "SEM_ROTA_CANDIDATO=$noRoutePasses | TEMPO=$tempo")
            alterarEstado(SpxState.FINDING_ROUTE, "Verificando rota no SPX...")

            if (noRoutePasses >= NO_ROUTE_CONFIRM_PASSES && tempo >= NO_ROUTE_MIN_TIME_MS) {
                confirmarSemRota()
                return
            }

            scheduleScan(750L)
            return
        } else {
            resetNoRouteDetection()
        }

        if (pareceTelaAutenticada(tela) && SpxSessionState.state.value == SpxState.LOGIN_REQUIRED) {
            alterarEstado(SpxState.AUTHENTICATED, "Login concluído.")
        }

        if (totalDetectado != null) {
            val anterior = SpxSessionState.totalEsperado.value
            SpxSessionState.updateTotalEsperado(totalDetectado)
            if (anterior != SpxSessionState.totalEsperado.value) {
                Log.d(TAG, "TOTAL_ESPERADO=$totalDetectado")
            }
        }

        if (at != null) {
            SpxSessionState.updateAtCode(at)
            SpxSessionState.updateDataCarregamento(extrairDataCandidataDaAT(at))
            if (ultimoAtLogado != at) {
                ultimoAtLogado = at
                Log.d(TAG, "ROTA_AT=DETECTADA")
            }
        }

        atualizarFingerprint(brsVisiveis)
        val novos = SpxSessionState.addPackageCodes(brsVisiveis)
        val quantidadeAtual = SpxSessionState.packageCount.value
        val totalEsperado = SpxSessionState.totalEsperado.value

        if (novos > 0) {
            stagnantPasses = 0
            mesmaPaginaConsecutiva = 0
            ultimoNovoPacoteTime = SystemClock.elapsedRealtime()
            if (quantidadeAtual != ultimaQuantidade) {
                ultimaQuantidade = quantidadeAtual
                Log.d(TAG, "PACOTES_TOTAL=$quantidadeAtual")
            }
        } else if (quantidadeAtual > 0) {
            stagnantPasses++
        }

        if (totalEsperado != null && totalEsperado > 0 && quantidadeAtual >= totalEsperado) {
            Log.d(TAG, "FIM=TOTAL_ESPERADO_ATINGIDO")
            concluirImportacao()
            return
        }

        if (quantidadeAtual > 0) {
            alterarEstado(
                SpxState.SCANNING_PACKAGES,
                if (totalEsperado != null) "Importando pedidos: $quantidadeAtual de $totalEsperado"
                else "Importando pedidos: $quantidadeAtual encontrados"
            )

            if (tentarScrollPorNodes(root)) {
                Log.d(TAG, "SCROLL=NODE")
                scheduleScan(900L)
                return
            }

            if (tentarSwipeVertical()) {
                Log.d(TAG, "SCROLL=GESTURE")
                scheduleScan(1100L)
                return
            }

            verificarFimSemTotal(quantidadeAtual, totalEsperado)
            scheduleScan(1000L)
            return
        }

        if (at != null) {
            alterarEstado(SpxState.ROUTE_DETECTED, "Rota localizada. Abrindo pedidos...")
            tentarAbrirAT(root, at)
            scheduleScan(900L)
            return
        }

        if (pareceTelaAutenticada(tela)) {
            alterarEstado(SpxState.FINDING_ROUTE, "Localizando rota...")
            tentarAbrirEntregas(root)
            scheduleScan(900L)
            return
        }

        alterarEstado(SpxState.CHECKING_SESSION, "Verificando sessão do SPX...")
        scheduleScan(900L)
    }

    private fun pareceTelaSemRota(textos: List<String>, tela: String): Boolean {
        if (Regex("""em\s*rota\s*\(\s*0\s*\)""", RegexOption.IGNORE_CASE).containsMatchIn(tela)) {
            return true
        }

        val temEmRota = textos.any { it.trim().contains("Em Rota", ignoreCase = true) }
        val temZero = textos.any {
            val v = it.trim().replace(" ", "")
            v == "(0)" || v == "0"
        }
        val contextoEntrega = tela.contains("escanear") || tela.contains("encerrado") || tela.contains("mostrar no mapa")
        return temEmRota && temZero && contextoEntrega
    }

    private fun resetNoRouteDetection() {
        noRoutePasses = 0
        noRouteFirstSeen = 0L
    }

    private fun confirmarSemRota() {
        if (fluxoFinalizado) return
        fluxoFinalizado = true
        handler.removeCallbacks(scanRunnable)
        SpxSessionState.setNoActiveRoute()
        Log.d(TAG, "STATUS=NO_ACTIVE_ROUTE")
        Toast.makeText(applicationContext, "Nenhuma rota ativa encontrada no SPX.", Toast.LENGTH_SHORT).show()
        voltarParaCopilotSemRota()
    }

    private fun voltarParaCopilotSemRota() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("NO_ACTIVE_ROUTE", true)
        }
        try {
            startActivity(intent)
            Log.d(TAG, "RETORNO_COPILOT=SEM_ROTA")
        } catch (e: Exception) {
            Log.e(TAG, "ERRO_RETORNO_SEM_ROTA", e)
        }
    }

    private fun atualizarFingerprint(brsVisiveis: Set<String>) {
        if (brsVisiveis.isEmpty()) return
        val fingerprint = brsVisiveis.sorted().joinToString("|")
        if (fingerprint == ultimoFingerprint) mesmaPaginaConsecutiva++
        else {
            ultimoFingerprint = fingerprint
            mesmaPaginaConsecutiva = 0
        }
    }

    private fun verificarFimSemTotal(quantidadeAtual: Int, totalEsperado: Int?) {
        if (totalEsperado != null && totalEsperado > 0) return
        val tempoSemNovos = SystemClock.elapsedRealtime() - ultimoNovoPacoteTime
        if (
            quantidadeAtual > 0 &&
            tempoSemNovos >= MIN_TIME_WITHOUT_NEW_MS &&
            stagnantPasses >= STAGNANT_LIMIT &&
            mesmaPaginaConsecutiva >= SAME_PAGE_LIMIT
        ) {
            Log.d(TAG, "FIM=FINAL_CONFIRMADO_SEM_TOTAL")
            concluirImportacao()
        }
    }

    private fun tentarScrollPorNodes(root: AccessibilityNodeInfo): Boolean {
        val candidatos = mutableListOf<AccessibilityNodeInfo>()
        coletarScrollables(root, candidatos)
        val ordenados = candidatos.sortedByDescending { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            rect.height()
        }
        for (node in ordenados) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
            } catch (_: Exception) { }
        }
        return false
    }

    private fun tentarSwipeVertical(): Boolean {
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimoGestureTime < MIN_GESTURE_INTERVAL_MS) return false
        ultimoGestureTime = agora

        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.50f
        val startY = metrics.heightPixels * 0.78f
        val endY = metrics.heightPixels * 0.28f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 450L))
            .build()

        return try {
            dispatchGesture(gesture, null, handler)
        } catch (e: Exception) {
            Log.e(TAG, "ERRO_GESTURE", e)
            false
        }
    }

    private fun pareceTelaLogin(tela: String): Boolean {
        val fortes = listOf(
            "esqueci minha senha",
            "fazer login",
            "iniciar sessão",
            "código de verificação",
            "codigo de verificacao",
            "código de confirmação",
            "codigo de confirmacao"
        )
        if (fortes.any { tela.contains(it) }) return true
        val sinais = listOf("login", "senha", "e-mail", "email", "telefone", "entrar")
        return sinais.count { tela.contains(it) } >= 2
    }

    private fun pareceTelaAutenticada(tela: String): Boolean {
        val sinais = listOf(
            "entrega", "entregas", "em rota", "ocorrência", "encerrado",
            "escanear", "mostrar no mapa", "rota", "pacote", "entregue"
        )
        return sinais.any { tela.contains(it) }
    }

    private fun encontrarCodigoAT(textos: List<String>): String? {
        val regex = Regex("""\bAT[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)
        textos.forEach { texto ->
            regex.find(texto.replace(" ", "").uppercase())?.let { return it.value.uppercase() }
        }
        return null
    }

    private fun encontrarCodigosBR(textos: List<String>): Set<String> {
        val encontrados = linkedSetOf<String>()
        val regex = Regex("""\bBR[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)
        textos.forEach { texto ->
            val normalizado = texto.replace(" ", "").replace("\n", "").uppercase()
            regex.findAll(normalizado).forEach { encontrados.add(it.value.uppercase()) }
        }
        return encontrados
    }

    private fun encontrarTotalPedidos(textos: List<String>): Int? {
        var maior: Int? = null
        val fracao = Regex("""(?<!\d)(\d{1,4})\s*/\s*(\d{1,4})(?!\d)""")
        textos.forEach { texto ->
            fracao.findAll(texto).forEach {
                val atual = it.groupValues[1].toIntOrNull()
                val total = it.groupValues[2].toIntOrNull()
                if (atual != null && total != null && atual >= 0 && atual <= total && total in 1..1000) {
                    if (maior == null || total > maior!!) maior = total
                }
            }
        }

        val textoTotal = Regex("""(?<!\d)(\d{1,4})\s*(?:pedidos?|pacotes?)(?!\w)""", RegexOption.IGNORE_CASE)
        textos.forEach { texto ->
            textoTotal.findAll(texto).forEach {
                val total = it.groupValues[1].toIntOrNull()
                if (total != null && total in 1..1000 && (maior == null || total > maior!!)) maior = total
            }
        }
        return maior
    }

    private fun extrairDataCandidataDaAT(at: String): String? {
        val resultado = Regex("""^AT(\d{4})(\d{2})(\d{2})""").find(at.uppercase()) ?: return null
        val ano = resultado.groupValues[1].toIntOrNull() ?: return null
        val mes = resultado.groupValues[2].toIntOrNull() ?: return null
        val dia = resultado.groupValues[3].toIntOrNull() ?: return null
        if (ano !in 2020..2100) return null

        try {
            GregorianCalendar().apply {
                isLenient = false
                set(Calendar.YEAR, ano)
                set(Calendar.MONTH, mes - 1)
                set(Calendar.DAY_OF_MONTH, dia)
                time
            }
        } catch (_: Exception) {
            return null
        }

        return String.format("%02d/%02d/%04d", dia, mes, ano)
    }

    private fun coletarScrollables(node: AccessibilityNodeInfo?, resultado: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isScrollable) resultado.add(node)
        for (i in 0 until node.childCount) coletarScrollables(node.getChild(i), resultado)
    }

    private fun tentarAbrirEntregas(root: AccessibilityNodeInfo) {
        if (!podeNavegarAgora()) return
        for (palavra in listOf("entregas", "entrega")) {
            val node = encontrarNodePorTexto(root, palavra, false)
            if (node != null && clicarNodeOuPai(node)) {
                registrarNavegacao()
                Log.d(TAG, "NAV=ENTREGAS")
                return
            }
        }
    }

    private fun tentarAbrirAT(root: AccessibilityNodeInfo, at: String) {
        if (!podeNavegarAgora()) return
        val node = encontrarNodePorTexto(root, at, true)
        if (node != null && clicarNodeOuPai(node)) {
            registrarNavegacao()
            Log.d(TAG, "NAV=ROTA")
        }
    }

    private fun podeNavegarAgora(): Boolean =
        SystemClock.elapsedRealtime() - ultimoNavigationTime >= NAVIGATION_DELAY_MS

    private fun registrarNavegacao() {
        ultimoNavigationTime = SystemClock.elapsedRealtime()
    }

    private fun encontrarNodePorTexto(
        node: AccessibilityNodeInfo?,
        procurado: String,
        exato: Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (!node.isPassword) {
            val texto = node.text?.toString()?.trim()
            val descricao = node.contentDescription?.toString()?.trim()
            if (textoCombina(texto, procurado, exato) || textoCombina(descricao, procurado, exato)) return node
        }
        for (i in 0 until node.childCount) {
            val achado = encontrarNodePorTexto(node.getChild(i), procurado, exato)
            if (achado != null) return achado
        }
        return null
    }

    private fun textoCombina(valor: String?, procurado: String, exato: Boolean): Boolean {
        if (valor.isNullOrBlank()) return false
        return if (exato) valor.equals(procurado, ignoreCase = true)
        else valor.contains(procurado, ignoreCase = true)
    }

    private fun clicarNodeOuPai(original: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = original
        var nivel = 0
        while (node != null && nivel < 7) {
            if (node.isClickable) {
                return try {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }
            }
            node = node.parent
            nivel++
        }
        return false
    }

    private fun coletarTextos(node: AccessibilityNodeInfo?, resultado: MutableList<String>) {
        if (node == null) return
        if (!node.isPassword) {
            val texto = node.text?.toString()?.trim()
            if (!texto.isNullOrBlank()) resultado.add(texto)
            val descricao = node.contentDescription?.toString()?.trim()
            if (!descricao.isNullOrBlank() && descricao != texto) resultado.add(descricao)
        }
        for (i in 0 until node.childCount) coletarTextos(node.getChild(i), resultado)
    }

    private fun concluirImportacao() {
        if (fluxoFinalizado) return
        val quantidade = SpxSessionState.packageCount.value
        if (quantidade <= 0) return

        fluxoFinalizado = true
        handler.removeCallbacks(scanRunnable)
        SpxSessionState.updateState(SpxState.IMPORT_COMPLETE)
        SpxSessionState.updateMessage("Importação concluída.")
        Log.d(TAG, "IMPORT_COMPLETE | TOTAL=$quantidade")
        Toast.makeText(applicationContext, "Rota importada: $quantidade pedidos.", Toast.LENGTH_SHORT).show()
        voltarParaCopilotComRota()
    }

    private fun voltarParaCopilotComRota() {
        SpxSessionState.updateState(SpxState.RETURNING_TO_COPILOT)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            putExtra("OPEN_ROUTE_MANAGEMENT", true)
        }
        try {
            startActivity(intent)
            SpxSessionState.updateState(SpxState.ROUTE_READY)
            Log.d(TAG, "RETORNO_COPILOT=ROTA")
        } catch (e: Exception) {
            Log.e(TAG, "ERRO_RETORNO_COPILOT", e)
        }
    }

    private fun resetInternalImport() {
        handler.removeCallbacks(scanRunnable)
        fluxoFinalizado = false
        loginAvisado = false
        ultimoEstadoLogado = null
        ultimoAtLogado = null
        ultimaQuantidade = 0
        ultimoNovoPacoteTime = SystemClock.elapsedRealtime()
        ultimoNavigationTime = 0L
        ultimoGestureTime = 0L
        stagnantPasses = 0
        ultimoFingerprint = ""
        mesmaPaginaConsecutiva = 0
        resetNoRouteDetection()
        Log.d(TAG, "IMPORT=RESET")
    }

    private fun alterarEstado(estado: SpxState, mensagem: String) {
        SpxSessionState.updateState(estado)
        SpxSessionState.updateMessage(mensagem)
        if (ultimoEstadoLogado != estado) {
            ultimoEstadoLogado = estado
            Log.d(TAG, "STATUS=$estado")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICO=INTERROMPIDO")
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        super.onDestroy()
    }
}
