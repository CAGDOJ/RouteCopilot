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

class SpxAccessibilityService :
    AccessibilityService() {

    companion object {
        private const val TAG =
            "RouteCopilotACC"

        private const val SPX_PACKAGE =
            "com.shopee.spx.driver.brazil"

        private const val SCAN_DELAY_MS =
            700L

        private const val NAVIGATION_DELAY_MS =
            1200L

        private const val DOWNLOAD_SEARCH_TIMEOUT_MS =
            15000L

        private const val TAB_NAVIGATION_TIMEOUT_MS =
            15000L

        private const val TOTAL_SYNC_TIMEOUT_MS =
            60000L

        private const val MIN_TIME_WITHOUT_NEW_MS =
            15000L

        private const val STAGNANT_LIMIT =
            18
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var importFinished =
        false

    private var downloadClicked =
        false

    private var deliveryAreaOpened =
        false

    private var inRouteClicked =
        false

    private var syncStartedAt =
        0L

    private var downloadSearchStartedAt =
        0L

    private var tabNavigationStartedAt =
        0L

    private var lastNavigationAt =
        0L

    private var lastGestureAt =
        0L

    private var lastNewPackageAt =
        0L

    private var stagnantPasses =
        0

    private var lastPackageCount =
        0

    private var loggedClickableCandidates =
        false

    private var bannerView:
        View? =
        null

    private var bannerMessage:
        TextView? =
        null

    private val scanRunnable =
        Runnable {
            scanCurrentScreen()
        }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // HOTFIX_V6_INIT_SESSION
        SpxSessionState.initialize(applicationContext)

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

        // HOTFIX_V6_IMPORT_GATE_EVENT
        // Se o usuÃ¡rio apenas abriu/reabriu o app ou abriu o SPX manualmente,
        // o serviÃ§o NÃƒO inicia uma sincronizaÃ§Ã£o sozinho.
        if (!SpxSessionState.isImportActive()) {
            handler.removeCallbacks(scanRunnable)
            hideBanner()
            return
        }

        if (
            SpxSessionState.state.value ==
            SpxState.STARTING_IMPORT
        ) {
            if (importFinished) {
                resetInternal()
            }

            if (syncStartedAt == 0L) {
                syncStartedAt =
                    SystemClock.elapsedRealtime()

                downloadSearchStartedAt =
                    syncStartedAt

                lastNewPackageAt =
                    syncStartedAt
            }
        }

        if (importFinished) {
            return
        }

        handler.removeCallbacks(
            scanRunnable
        )

        handler.postDelayed(
            scanRunnable,
            180L
        )
    }

    private fun scanCurrentScreen() {
        if (importFinished) {
            return
        }

        // HOTFIX_V6_IMPORT_GATE_SCAN
        if (!SpxSessionState.isImportActive()) {
            handler.removeCallbacks(scanRunnable)
            hideBanner()
            return
        }

        ensureTimers()

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - syncStartedAt >=
            TOTAL_SYNC_TIMEOUT_MS
        ) {
            failAndReturn(
                "A sincronizaÃ§Ã£o demorou demais. Tente novamente."
            )
            return
        }

        val root =
            rootInActiveWindow
                ?: run {
                    update(
                        SpxState.CHECKING_SESSION,
                        "Aguardando o SPX carregar..."
                    )
                    schedule()
                    return
                }

        val texts =
            mutableListOf<String>()

        SpxParser.collectTexts(
            root,
            texts
        )

        if (texts.isEmpty()) {
            update(
                SpxState.CHECKING_SESSION,
                "Aguardando conteÃºdo do SPX..."
            )
            schedule()
            return
        }

        val screen =
            SpxParser.normalizeScreen(
                texts
            )

        // ------------------------------------------------
        // LOGIN
        // ------------------------------------------------
        if (
            SpxParser.isLoginScreen(
                screen
            )
        ) {
            hideBanner()

            update(
                SpxState.LOGIN_REQUIRED,
                "FaÃ§a o login normalmente no SPX."
            )

            Log.d(
                TAG,
                "STATUS=LOGIN_REQUIRED"
            )

            schedule(
                900L
            )
            return
        }

        // ------------------------------------------------
        // ACEITE / TERMOS
        // ------------------------------------------------
        if (
            SpxParser.isConsentScreen(
                screen
            )
        ) {
            hideBanner()

            update(
                SpxState.CONSENT_REQUIRED,
                "Confirme o aceite no SPX."
            )

            Log.d(
                TAG,
                "STATUS=CONSENT_REQUIRED"
            )

            schedule(
                900L
            )
            return
        }

        // ------------------------------------------------
        // RECONHECIMENTO FACIAL
        // ------------------------------------------------
        if (
            SpxParser.isFaceCheckScreen(
                screen
            )
        ) {
            hideBanner()

            update(
                SpxState.FACE_CHECK_REQUIRED,
                "Conclua o reconhecimento facial no SPX."
            )

            Log.d(
                TAG,
                "STATUS=FACE_CHECK_REQUIRED"
            )

            schedule(
                900L
            )
            return
        }

        showBanner()

        // ------------------------------------------------
        // 1Âº: LOCALIZAR A SETA / BOTÃƒO DE DOWNLOAD
        // ------------------------------------------------
        if (!downloadClicked) {
            update(
                SpxState.FINDING_DOWNLOAD_BUTTON,
                "Localizando a seta de download da rota..."
            )

            val downloadButton =
                SpxParser.findDownloadButton(
                    root
                )

            if (
                downloadButton != null &&
                canNavigate()
            ) {
                update(
                    SpxState.DOWNLOAD_BUTTON_FOUND,
                    "Download localizado."
                )

                val clicked =
                    runCatching {
                        downloadButton.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    }.getOrDefault(
                        false
                    )

                if (clicked) {
                    downloadClicked =
                        true

                    tabNavigationStartedAt =
                        SystemClock.elapsedRealtime()

                    lastNavigationAt =
                        SystemClock.elapsedRealtime()

                    update(
                        SpxState.DOWNLOADING_ROUTE,
                        "Baixando a rota..."
                    )

                    Log.d(
                        TAG,
                        "DOWNLOAD=CLICKED"
                    )

                    schedule(
                        2500L
                    )
                    return
                }
            }

            if (!loggedClickableCandidates) {
                loggedClickableCandidates =
                    true

                SpxParser.logSafeClickableCandidates(
                    root
                ) { line ->
                    Log.d(
                        TAG,
                        line
                    )
                }
            }

            if (
                SystemClock.elapsedRealtime() -
                downloadSearchStartedAt >=
                DOWNLOAD_SEARCH_TIMEOUT_MS
            ) {
                failAndReturn(
                    "NÃ£o encontrei a seta de download da rota no SPX."
                )
                return
            }

            Log.d(
                TAG,
                "DOWNLOAD=NOT_FOUND"
            )

            schedule(
                900L
            )
            return
        }

        // ------------------------------------------------
        // 2Âº: APÃ“S DOWNLOAD, IR PARA ENTREGA / EM ROTA.
        //
        // REGRA CRÃTICA:
        // NUNCA aceitar sÃ³ porque o texto "Em Rota" existe
        // na tela. A aba precisa ser clicada de verdade.
        // Isso evita importar "Encerrado/Finalizados".
        // ------------------------------------------------
        if (!inRouteClicked) {

            update(
                SpxState.OPENING_IN_ROUTE,
                "Abrindo Entrega / Em Rota..."
            )

            if (
                SpxParser.hasDeliveryTabs(
                    screen
                )
            ) {
                val emRota =
                    SpxParser.findClickableStartsWith(
                        root,
                        "Em Rota"
                    )

                if (
                    emRota != null &&
                    canNavigate()
                ) {
                    val clicked =
                        runCatching {
                            emRota.performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )
                        }.getOrDefault(
                            false
                        )

                    if (clicked) {
                        inRouteClicked =
                            true

                        lastNavigationAt =
                            SystemClock.elapsedRealtime()

                        Log.d(
                            TAG,
                            "NAV=EM_ROTA"
                        )

                        schedule(
                            1500L
                        )
                        return
                    }
                }
            } else if (!deliveryAreaOpened) {

                val screenHeight =
                    resources.displayMetrics
                        .heightPixels

                val entrega =
                    SpxParser.findTopClickableExactText(
                        root,
                        "Entrega",
                        screenHeight
                    ) ?: SpxParser.findTopClickableExactText(
                        root,
                        "Entregas",
                        screenHeight
                    )

                if (
                    entrega != null &&
                    canNavigate()
                ) {
                    val clicked =
                        runCatching {
                            entrega.performAction(
                                AccessibilityNodeInfo.ACTION_CLICK
                            )
                        }.getOrDefault(
                            false
                        )

                    if (clicked) {
                        deliveryAreaOpened =
                            true

                        lastNavigationAt =
                            SystemClock.elapsedRealtime()

                        Log.d(
                            TAG,
                            "NAV=ENTREGA"
                        )

                        schedule(
                            1300L
                        )
                        return
                    }
                }
            }

            if (
                SystemClock.elapsedRealtime() -
                tabNavigationStartedAt >=
                TAB_NAVIGATION_TIMEOUT_MS
            ) {
                failAndReturn(
                    "NÃ£o consegui abrir a aba Em Rota. A sincronizaÃ§Ã£o foi cancelada."
                )
                return
            }

            schedule(
                900L
            )
            return
        }

        // ------------------------------------------------
        // 3Âº: SOMENTE DEPOIS DO CLIQUE EM "EM ROTA"
        // LER AT, TOTAL E BRs.
        // ------------------------------------------------
        update(
            SpxState.READING_ROUTE,
            "Importando os pedidos Em Rota..."
        )

        val at =
            SpxParser.findAt(
                texts
            )

        if (at != null) {
            SpxSessionState.updateAtCode(
                at
            )

            SpxSessionState.updateDataCarregamento(
                SpxParser.dateFromAt(
                    at
                )
            )
        }

        val expectedTotal =
            SpxParser.findExpectedTotal(
                texts
            )

        if (expectedTotal != null) {
            SpxSessionState.updateTotalEsperado(
                expectedTotal
            )
        }

        val newPackages =
            SpxSessionState.addPackageCodes(
                SpxParser.findBrCodes(
                    texts
                )
            )

        val count =
            SpxSessionState.packageCount.value

        val expected =
            SpxSessionState.totalEsperado.value

        if (newPackages > 0) {
            stagnantPasses =
                0

            lastNewPackageAt =
                SystemClock.elapsedRealtime()

            if (
                count !=
                lastPackageCount
            ) {
                lastPackageCount =
                    count

                Log.d(
                    TAG,
                    "PACOTES_TOTAL=$count"
                )
            }
        } else if (
            count > 0
        ) {
            stagnantPasses++
        }

        update(
            SpxState.VALIDATING_ROUTE,
            if (expected != null) {
                "Validando pedidos: $count de $expected"
            } else {
                "Validando pedidos: $count encontrados"
            }
        )

        if (
            expected != null &&
            expected > 0 &&
            count >= expected
        ) {
            finishImport()
            return
        }

        if (count > 0) {
            if (
                tryNodeScroll(
                    root
                )
            ) {
                Log.d(
                    TAG,
                    "SCROLL=NODE"
                )

                schedule(
                    950L
                )
                return
            }

            if (
                tryGestureScroll()
            ) {
                Log.d(
                    TAG,
                    "SCROLL=GESTURE"
                )

                schedule(
                    1100L
                )
                return
            }

            if (expected == null) {
                val stalledFor =
                    SystemClock.elapsedRealtime() -
                        lastNewPackageAt

                if (
                    stalledFor >=
                    MIN_TIME_WITHOUT_NEW_MS &&
                    stagnantPasses >=
                    STAGNANT_LIMIT
                ) {
                    finishImport()
                    return
                }
            }
        }

        schedule(
            1000L
        )
    }

    private fun ensureTimers() {
        if (syncStartedAt == 0L) {
            val now =
                SystemClock.elapsedRealtime()

            syncStartedAt =
                now

            downloadSearchStartedAt =
                now

            lastNewPackageAt =
                now
        }
    }

    private fun finishImport() {
        val count =
            SpxSessionState.packageCount.value

        if (count <= 0) {
            failAndReturn(
                "Nenhum pedido Em Rota foi importado."
            )
            return
        }

        importFinished =
            true

        handler.removeCallbacks(
            scanRunnable
        )

        update(
            SpxState.CALCULATING_ROUTE,
            "Preparando a rota no Copilot..."
        )

        handler.postDelayed(
            {
                update(
                    SpxState.IMPORT_COMPLETE,
                    "Rota importada com sucesso."
                )

                hideBanner()

                returnToCopilot(
                    success = true
                )
            },
            700L
        )
    }

    private fun failAndReturn(
        message: String
    ) {
        importFinished =
            true

        handler.removeCallbacks(
            scanRunnable
        )

        hideBanner()

        SpxSessionState.fail(
            message
        )

        Log.d(
            TAG,
            "IMPORT=ERROR | $message"
        )

        returnToCopilot(
            success = false
        )
    }

    private fun returnToCopilot(
        success: Boolean
    ) {
        if (success) {
            update(
                SpxState.RETURNING_TO_COPILOT,
                "Voltando ao RouteCopilot..."
            )
        }

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
                    success
                )
            }

        runCatching {
            startActivity(
                intent
            )
        }.onSuccess {
            if (success) {
                update(
                    SpxState.ROUTE_READY,
                    "Rota pronta."
                )

                Log.d(
                    TAG,
                    "RETURN=COPILOT"
                )
            } else {
                Log.d(
                    TAG,
                    "RETURN=COPILOT_ERROR"
                )
            }
        }.onFailure {
            Log.e(
                TAG,
                "RETURN=FAILED",
                it
            )
        }
    }

    private fun update(
        state: SpxState,
        message: String
    ) {
        SpxSessionState.updateState(
            state,
            message
        )

        bannerMessage?.text =
            message
    }

    private fun canNavigate(): Boolean {
        return SystemClock.elapsedRealtime() -
            lastNavigationAt >=
            NAVIGATION_DELAY_MS
    }

    private fun tryNodeScroll(
        root: AccessibilityNodeInfo
    ): Boolean {
        val candidates =
            mutableListOf<AccessibilityNodeInfo>()

        collectScrollableNodes(
            root,
            candidates
        )

        for (node in candidates) {
            val moved =
                runCatching {
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    )
                }.getOrDefault(
                    false
                )

            if (moved) {
                return true
            }
        }

        return false
    }

    private fun collectScrollableNodes(
        node: AccessibilityNodeInfo?,
        output: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) {
            return
        }

        if (node.isScrollable) {
            output.add(
                node
            )
        }

        for (
            i in 0 until node.childCount
        ) {
            collectScrollableNodes(
                node.getChild(i),
                output
            )
        }
    }

    private fun tryGestureScroll(): Boolean {
        val now =
            SystemClock.elapsedRealtime()

        if (
            now - lastGestureAt <
            850L
        ) {
            return false
        }

        lastGestureAt =
            now

        val metrics =
            resources.displayMetrics

        val x =
            metrics.widthPixels *
                0.50f

        val startY =
            metrics.heightPixels *
                0.75f

        val endY =
            metrics.heightPixels *
                0.32f

        val path =
            Path().apply {
                moveTo(
                    x,
                    startY
                )
                lineTo(
                    x,
                    endY
                )
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0L,
                        430L
                    )
                )
                .build()

        return runCatching {
            dispatchGesture(
                gesture,
                null,
                handler
            )
        }.getOrDefault(
            false
        )
    }

    // ----------------------------------------------------
    // BANNER PEQUENO E NÃƒO-BLOQUEANTE.
    //
    // NÃ£o cobre a tela inteira, nÃ£o captura toques e nÃ£o
    // impede Voltar/Home.
    // ----------------------------------------------------
    private fun showBanner() {
        if (bannerView != null) {
            bannerMessage?.text =
                SpxSessionState
                    .statusMessage
                    .value
            return
        }

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        val container =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    28,
                    20,
                    28,
                    20
                )

                setBackgroundColor(
                    Color.rgb(
                        248,
                        250,
                        252
                    )
                )
            }

        val progress =
            ProgressBar(this).apply {
                isIndeterminate =
                    true
            }

        val textContainer =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    20,
                    0,
                    0,
                    0
                )
            }

        val brand =
            TextView(this).apply {
                text =
                    "RouteCopilot"

                textSize =
                    15f

                setTextColor(
                    Color.rgb(
                        18,
                        103,
                        227
                    )
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }

        val message =
            TextView(this).apply {
                text =
                    SpxSessionState
                        .statusMessage
                        .value

                textSize =
                    13f

                setTextColor(
                    Color.rgb(
                        71,
                        85,
                        105
                    )
                )
            }

        bannerMessage =
            message

        textContainer.addView(
            brand
        )

        textContainer.addView(
            message
        )

        container.addView(
            progress
        )

        container.addView(
            textContainer
        )

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP

        params.y =
            80

        runCatching {
            windowManager.addView(
                container,
                params
            )

            bannerView =
                container
        }
    }

    private fun hideBanner() {
        val view =
            bannerView
                ?: return

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        runCatching {
            windowManager.removeView(
                view
            )
        }

        bannerView =
            null

        bannerMessage =
            null
    }

    private fun schedule(
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

    private fun resetInternal() {
        importFinished =
            false

        downloadClicked =
            false

        deliveryAreaOpened =
            false

        inRouteClicked =
            false

        syncStartedAt =
            0L

        downloadSearchStartedAt =
            0L

        tabNavigationStartedAt =
            0L

        lastNavigationAt =
            0L

        lastGestureAt =
            0L

        lastNewPackageAt =
            0L

        stagnantPasses =
            0

        lastPackageCount =
            0

        loggedClickableCandidates =
            false

        hideBanner()

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

        hideBanner()

        super.onDestroy()
    }
}
