package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.routecopilot.MainActivity

class SpxAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RouteCopilotACC"
        private const val SPX_PACKAGE = "com.shopee.spx.driver.brazil"
        private const val SCAN_DELAY_MS = 650L
        private const val NAVIGATION_DELAY_MS = 1200L
        private const val DOWNLOAD_WAIT_MS = 2500L
        private const val MIN_TIME_WITHOUT_NEW_MS = 15000L
        private const val STAGNANT_LIMIT = 18
    }

    private val handler = Handler(Looper.getMainLooper())
    private var importFinished = false
    private var downloadClicked = false
    private var deliveriesOpened = false
    private var inRouteOpened = false
    private var lastNavigationAt = 0L
    private var lastGestureAt = 0L
    private var lastNewPackageAt = SystemClock.elapsedRealtime()
    private var stagnantPasses = 0
    private var lastPackageCount = 0
    private var overlayView: View? = null
    private var overlayMessage: TextView? = null

    private val scanRunnable = Runnable { scanCurrentScreen() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "SERVICO=ATIVO")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != SPX_PACKAGE) return

        if (SpxSessionState.state.value == SpxState.STARTING_IMPORT && importFinished) resetInternal()
        if (importFinished) return

        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 160L)
    }

    private fun scanCurrentScreen() {
        if (importFinished) return
        val root = rootInActiveWindow ?: run {
            update(SpxState.CHECKING_SESSION, "Aguardando o SPX carregar...")
            schedule()
            return
        }

        val texts = mutableListOf<String>()
        SpxParser.collectTexts(root, texts)
        if (texts.isEmpty()) {
            update(SpxState.CHECKING_SESSION, "Aguardando conteúdo do SPX...")
            schedule()
            return
        }

        val screen = SpxParser.normalizeScreen(texts)

        if (SpxParser.isLoginScreen(screen)) {
            hideOverlay()
            update(SpxState.LOGIN_REQUIRED, "Faça o login normalmente no SPX.")
            Log.d(TAG, "STATUS=LOGIN_REQUIRED")
            schedule(900L)
            return
        }

        if (SpxParser.isConsentScreen(screen)) {
            hideOverlay()
            update(SpxState.CONSENT_REQUIRED, "Confirme o aceite no SPX para continuar.")
            Log.d(TAG, "STATUS=CONSENT_REQUIRED")
            schedule(900L)
            return
        }

        if (SpxParser.isFaceCheckScreen(screen)) {
            hideOverlay()
            update(SpxState.FACE_CHECK_REQUIRED, "Conclua o reconhecimento facial no SPX.")
            Log.d(TAG, "STATUS=FACE_CHECK_REQUIRED")
            schedule(900L)
            return
        }

        showOverlay()

        if (!downloadClicked) {
            update(SpxState.FINDING_DOWNLOAD_BUTTON, "Localizando a seta de download da rota...")
            val downloadButton = SpxParser.findClickableByKeywords(
                root,
                listOf("download", "baixar", "carregar rota", "sincronizar rota", "atualizar rota", "offline", "⬇", "↓")
            )

            if (downloadButton != null && canNavigate()) {
                update(SpxState.DOWNLOAD_BUTTON_FOUND, "Rota localizada. Iniciando download...")
                val clicked = runCatching {
                    downloadButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)

                if (clicked) {
                    downloadClicked = true
                    lastNavigationAt = SystemClock.elapsedRealtime()
                    update(SpxState.DOWNLOADING_ROUTE, "Baixando a árvore da rota...")
                    Log.d(TAG, "DOWNLOAD=CLICKED")
                    schedule(DOWNLOAD_WAIT_MS)
                    return
                }
            }

            Log.d(TAG, "DOWNLOAD=NOT_FOUND")
            schedule(900L)
            return
        }

        if (!deliveriesOpened) {
            update(SpxState.OPENING_DELIVERIES, "Abrindo Entrega...")
            val delivery = SpxParser.findClickableExactText(root, "Entrega")
                ?: SpxParser.findClickableExactText(root, "Entregas")

            if (delivery != null && canNavigate()) {
                val clicked = runCatching {
                    delivery.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                if (clicked) {
                    deliveriesOpened = true
                    lastNavigationAt = SystemClock.elapsedRealtime()
                    Log.d(TAG, "NAV=ENTREGA")
                    schedule(1200L)
                    return
                }
            }

            if (screen.contains("em rota")) deliveriesOpened = true
            else {
                schedule(900L)
                return
            }
        }

        if (!inRouteOpened) {
            update(SpxState.OPENING_IN_ROUTE, "Abrindo pedidos Em Rota...")
            val inRoute = SpxParser.findClickableExactText(root, "Em Rota")
                ?: SpxParser.findClickableByKeywords(root, listOf("em rota"))

            if (inRoute != null && canNavigate()) {
                val clicked = runCatching {
                    inRoute.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                if (clicked) {
                    inRouteOpened = true
                    lastNavigationAt = SystemClock.elapsedRealtime()
                    Log.d(TAG, "NAV=EM_ROTA")
                    schedule(1400L)
                    return
                }
            }

            if (screen.contains("em rota")) inRouteOpened = true
            else {
                schedule(900L)
                return
            }
        }

        update(SpxState.READING_ROUTE, "Importando pedidos da rota...")

        SpxParser.findAt(texts)?.let { at ->
            SpxSessionState.updateAtCode(at)
            SpxSessionState.updateDataCarregamento(SpxParser.dateFromAt(at))
        }

        SpxParser.findExpectedTotal(texts)?.let(SpxSessionState::updateTotalEsperado)

        val newPackages = SpxSessionState.addPackageCodes(SpxParser.findBrCodes(texts))
        val count = SpxSessionState.packageCount.value
        val expected = SpxSessionState.totalEsperado.value

        if (newPackages > 0) {
            stagnantPasses = 0
            lastNewPackageAt = SystemClock.elapsedRealtime()
            if (count != lastPackageCount) {
                lastPackageCount = count
                Log.d(TAG, "PACOTES_TOTAL=$count")
            }
        } else if (count > 0) {
            stagnantPasses++
        }

        update(
            SpxState.VALIDATING_ROUTE,
            if (expected != null) "Validando pedidos: $count de $expected" else "Validando pedidos: $count encontrados"
        )

        if (expected != null && expected > 0 && count >= expected) {
            finishImport()
            return
        }

        if (count > 0) {
            if (tryNodeScroll(root)) {
                Log.d(TAG, "SCROLL=NODE")
                schedule(950L)
                return
            }
            if (tryGestureScroll()) {
                Log.d(TAG, "SCROLL=GESTURE")
                schedule(1100L)
                return
            }

            if (expected == null) {
                val stalledFor = SystemClock.elapsedRealtime() - lastNewPackageAt
                if (stalledFor >= MIN_TIME_WITHOUT_NEW_MS && stagnantPasses >= STAGNANT_LIMIT) {
                    finishImport()
                    return
                }
            }
        }

        schedule(1000L)
    }

    private fun finishImport() {
        val count = SpxSessionState.packageCount.value
        if (count <= 0) {
            SpxSessionState.fail("Não foi possível importar os pedidos.")
            return
        }

        importFinished = true
        handler.removeCallbacks(scanRunnable)
        update(SpxState.CALCULATING_ROUTE, "Preparando a rota no Copilot...")

        handler.postDelayed({
            update(SpxState.IMPORT_COMPLETE, "Rota importada com sucesso.")
            hideOverlay()
            returnToCopilot()
        }, 800L)
    }

    private fun returnToCopilot() {
        update(SpxState.RETURNING_TO_COPILOT, "Voltando ao RouteCopilot...")
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("OPEN_ROUTE_MANAGEMENT", true)
        }

        runCatching { startActivity(intent) }
            .onSuccess {
                update(SpxState.ROUTE_READY, "Rota pronta.")
                Log.d(TAG, "RETURN=COPILOT")
            }
            .onFailure {
                Log.e(TAG, "RETURN=FAILED", it)
                SpxSessionState.fail("Rota importada, mas não foi possível retornar ao Copilot.")
            }
    }

    private fun update(state: SpxState, message: String) {
        SpxSessionState.updateState(state, message)
        overlayMessage?.text = message
    }

    private fun canNavigate(): Boolean =
        SystemClock.elapsedRealtime() - lastNavigationAt >= NAVIGATION_DELAY_MS

    private fun tryNodeScroll(root: AccessibilityNodeInfo): Boolean {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectScrollableNodes(root, candidates)
        for (node in candidates) {
            val moved = runCatching {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }.getOrDefault(false)
            if (moved) return true
        }
        return false
    }

    private fun collectScrollableNodes(node: AccessibilityNodeInfo?, output: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isScrollable) output.add(node)
        for (i in 0 until node.childCount) collectScrollableNodes(node.getChild(i), output)
    }

    private fun tryGestureScroll(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGestureAt < 800L) return false
        lastGestureAt = now

        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.50f
        val startY = metrics.heightPixels * 0.76f
        val endY = metrics.heightPixels * 0.30f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 430L))
            .build()

        return runCatching { dispatchGesture(gesture, null, handler) }.getOrDefault(false)
    }

    private fun showOverlay() {
        if (overlayView != null) {
            overlayMessage?.text = SpxSessionState.statusMessage.value
            return
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        val brand = TextView(this).apply {
            text = "RouteCopilot"
            textSize = 28f
            setTextColor(Color.rgb(18, 103, 227))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "Sincronizando rota"
            textSize = 22f
            setTextColor(Color.rgb(15, 23, 42))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 12)
        }
        val progress = ProgressBar(this).apply { isIndeterminate = true }
        val message = TextView(this).apply {
            text = SpxSessionState.statusMessage.value
            textSize = 16f
            setTextColor(Color.rgb(71, 85, 105))
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0)
        }
        overlayMessage = message

        container.addView(brand)
        container.addView(title)
        container.addView(progress)
        container.addView(message)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            windowManager.addView(container, params)
            overlayView = container
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
        overlayView = null
        overlayMessage = null
    }

    private fun schedule(delay: Long = SCAN_DELAY_MS) {
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, delay)
    }

    private fun resetInternal() {
        importFinished = false
        downloadClicked = false
        deliveriesOpened = false
        inRouteOpened = false
        lastNavigationAt = 0L
        lastGestureAt = 0L
        lastNewPackageAt = SystemClock.elapsedRealtime()
        stagnantPasses = 0
        lastPackageCount = 0
        hideOverlay()
        Log.d(TAG, "IMPORT=RESET")
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICO=INTERROMPIDO")
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        hideOverlay()
        super.onDestroy()
    }
}
