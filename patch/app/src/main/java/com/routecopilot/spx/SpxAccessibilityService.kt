package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.routecopilot.MainActivity
import com.routecopilot.route.RouteRepository

class SpxAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RouteCopilotACC"
        private const val SPX_PACKAGE = "com.shopee.spx.driver.brazil"
        private const val SCAN_DELAY_MS = 700L
        private const val MIN_STAGNANT_MS = 20_000L
        private const val STAGNANT_PASSES = 20
        private const val MAX_TOTAL_SYNC_MS = 120_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { scan() }

    private var startedAt = 0L
    private var lastNewAt = 0L
    private var stagnant = 0
    private var lastCount = 0
    private var finished = false
    private var openedDelivery = false
    private var openedInRoute = false
    private var lastGestureAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        RouteRepository.initialize(applicationContext)
        Log.d(TAG, "SERVICO=ATIVO")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != SPX_PACKAGE) return

        if (SpxSessionState.state.value == SpxState.STARTING_IMPORT) {
            resetIfNeeded()
        }
        if (finished) return

        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 180L)
    }

    private fun resetIfNeeded() {
        if (startedAt != 0L && !finished) return
        startedAt = SystemClock.elapsedRealtime()
        lastNewAt = startedAt
        stagnant = 0
        lastCount = 0
        finished = false
        openedDelivery = false
        openedInRoute = false
        RouteRepository.clear()
        Log.d(TAG, "IMPORT=START")
    }

    private fun scan() {
        if (finished) return
        if (startedAt == 0L) resetIfNeeded()

        if (SystemClock.elapsedRealtime() - startedAt > MAX_TOTAL_SYNC_MS) {
            failAndReturn("A sincronização demorou demais. Abra Entrega / Em Rota no SPX e tente novamente.")
            return
        }

        val root = rootInActiveWindow ?: run {
            update(SpxState.CHECKING_SESSION, "Aguardando o SPX carregar...")
            schedule()
            return
        }

        if (root.packageName?.toString() != SPX_PACKAGE) return

        val texts = mutableListOf<String>()
        SpxParser.collectTexts(root, texts)
        if (texts.isEmpty()) {
            update(SpxState.CHECKING_SESSION, "Aguardando conteúdo do SPX...")
            schedule()
            return
        }

        val screen = SpxParser.normalizeScreen(texts)

        if (SpxParser.isLoginScreen(screen)) {
            update(SpxState.LOGIN_REQUIRED, "Faça o login normalmente no SPX.")
            schedule(900L)
            return
        }

        if (SpxParser.isConsentScreen(screen)) {
            update(SpxState.CONSENT_REQUIRED, "Confirme o aceite normalmente no SPX.")
            schedule(900L)
            return
        }

        if (SpxParser.isFaceCheckScreen(screen)) {
            update(SpxState.FACE_CHECK_REQUIRED, "Conclua o reconhecimento facial normalmente no SPX.")
            schedule(900L)
            return
        }

        val at = SpxParser.findAt(texts)
        if (at != null) {
            SpxSessionState.setAt(at)
            SpxSessionState.setDate(SpxParser.dateFromAt(at))
            update(SpxState.ROUTE_DETECTED, "Rota localizada.")
            Log.d(TAG, "ROTA_AT=DETECTADA")
        }

        val expected = SpxParser.findExpectedTotal(texts)
        if (expected != null) {
            SpxSessionState.setExpectedTotal(expected)
            Log.d(TAG, "TOTAL_ESPERADO=$expected")
        }

        val candidates = SpxParser.findPackageCandidates(root)
        if (candidates.isNotEmpty()) {
            RouteRepository.mergeCandidates(candidates)
            val newCodes = SpxSessionState.addPackageCodes(candidates.map { it.br })
            if (newCodes > 0) {
                lastNewAt = SystemClock.elapsedRealtime()
                stagnant = 0
                val count = SpxSessionState.packageCount.value
                if (count != lastCount) {
                    lastCount = count
                    Log.d(TAG, "PACOTES_TOTAL=$count")
                }
            } else {
                stagnant++
            }

            update(
                SpxState.IMPORTING_PACKAGES,
                if (SpxSessionState.expectedTotal.value != null)
                    "Importando ${SpxSessionState.packageCount.value} de ${SpxSessionState.expectedTotal.value} pedidos"
                else
                    "Importando ${SpxSessionState.packageCount.value} pedidos"
            )

            val count = SpxSessionState.packageCount.value
            val total = SpxSessionState.expectedTotal.value
            if (total != null && count >= total) {
                finishImport()
                return
            }

            if (
                total == null &&
                count > 0 &&
                SystemClock.elapsedRealtime() - lastNewAt >= MIN_STAGNANT_MS &&
                stagnant >= STAGNANT_PASSES
            ) {
                finishImport()
                return
            }

            if (tryScroll(root)) {
                schedule(950L)
                return
            }

            if (tryGesture()) {
                schedule(1100L)
                return
            }

            schedule()
            return
        }

        // Navegação simples: só abre áreas, nunca confirma login/aceite/facial.
        if (!openedDelivery) {
            val delivery =
                SpxParser.findClickableExact(root, "Entrega")
                    ?: SpxParser.findClickableExact(root, "Entregas")
            if (delivery != null) {
                if (runCatching {
                        delivery.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }.getOrDefault(false)
                ) {
                    openedDelivery = true
                    update(SpxState.FINDING_ROUTE, "Abrindo Entrega...")
                    Log.d(TAG, "NAV=ENTREGA")
                    schedule(1200L)
                    return
                }
            }
        }

        if (!openedInRoute) {
            val inRoute = SpxParser.findClickableStartsWith(root, "Em Rota")
            if (inRoute != null) {
                if (runCatching {
                        inRoute.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }.getOrDefault(false)
                ) {
                    openedInRoute = true
                    update(SpxState.READING_ROUTE, "Abrindo pedidos Em Rota...")
                    Log.d(TAG, "NAV=EM_ROTA")
                    schedule(1200L)
                    return
                }
            }
        }

        update(
            SpxState.FINDING_ROUTE,
            "Aguardando Entrega / Em Rota no SPX..."
        )
        schedule(900L)
    }

    private fun tryScroll(root: AccessibilityNodeInfo): Boolean {
        val node = SpxParser.findLargestScrollable(root) ?: return false
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }.getOrDefault(false).also {
            if (it) Log.d(TAG, "SCROLL=NODE")
        }
    }

    private fun tryGesture(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < 900L) return false
        lastGestureAt = now

        val m = resources.displayMetrics
        val x = m.widthPixels * 0.50f
        val startY = m.heightPixels * 0.78f
        val endY = m.heightPixels * 0.28f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 450L))
            .build()

        return runCatching {
            dispatchGesture(gesture, null, handler)
        }.getOrDefault(false).also {
            if (it) Log.d(TAG, "SCROLL=GESTURE")
        }
    }

    private fun finishImport() {
        val count = SpxSessionState.packageCount.value
        if (count <= 0) {
            failAndReturn("Nenhum pedido Em Rota foi identificado.")
            return
        }

        finished = true
        handler.removeCallbacks(scanRunnable)

        update(SpxState.IMPORT_COMPLETE, "Rota importada com $count pedidos.")
        Log.d(TAG, "IMPORT_COMPLETE | TOTAL=$count")
        returnToCopilot(true)
    }

    private fun failAndReturn(message: String) {
        finished = true
        handler.removeCallbacks(scanRunnable)
        SpxSessionState.fail(message)
        Log.d(TAG, "IMPORT=ERROR")
        returnToCopilot(false)
    }

    private fun returnToCopilot(success: Boolean) {
        if (success) {
            update(SpxState.RETURNING_TO_COPILOT, "Voltando ao RouteCopilot...")
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("OPEN_ROUTE_MANAGEMENT", success)
        }

        runCatching { startActivity(intent) }
            .onSuccess {
                if (success) {
                    update(SpxState.ROUTE_READY, "Rota pronta.")
                    Log.d(TAG, "RETURN=COPILOT")
                }
            }
            .onFailure {
                Log.e(TAG, "RETURN=FAILED", it)
            }

        // Fallback Samsung: se o SPX continuar em primeiro plano, volta e abre de novo.
        handler.postDelayed({
            if (rootInActiveWindow?.packageName?.toString() == SPX_PACKAGE) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    runCatching { startActivity(intent) }
                }, 500L)
            }
        }, 1200L)
    }

    private fun update(state: SpxState, message: String) {
        SpxSessionState.update(state, message)
    }

    private fun schedule(delay: Long = SCAN_DELAY_MS) {
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, delay)
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICO=INTERROMPIDO")
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        super.onDestroy()
    }
}
