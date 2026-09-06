package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.routecopilot.MainActivity
import com.routecopilot.route.RouteRepository

class SpxAccessibilityService :
    AccessibilityService() {

    companion object {
        private const val TAG =
            "RouteCopilotACC"

        private const val SPX_PACKAGE =
            "com.shopee.spx.driver.brazil"

        private const val SCAN_DELAY_MS =
            650L

        private const val GESTURE_INTERVAL_MS =
            850L

        private const val MIN_NO_NEW_MS =
            15_000L

        private const val SAME_PAGE_LIMIT =
            10

        private const val MAX_IDLE_WITHOUT_PROGRESS_MS =
            300_000L

        /*
         * O usuário informou que Ocorrências pode aparecer 0
         * e só depois carregar.
         *
         * Por isso zero não é aceito imediatamente.
         */
        private const val STATUS_TAB_MIN_WAIT_MS =
            8_000L

        private const val STATUS_TAB_MAX_WAIT_MS =
            16_000L
    }

    private enum class DailyPhase {
        NONE,
        OCCURRENCES,
        CLOSED
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val scanRunnable =
        Runnable {
            scan()
        }

    private var activeSessionId =
        -1L

    private var lastProgressAt =
        0L

    private var lastNewPackageAt =
        0L

    private var lastGestureAt =
        0L

    private var lastCount =
        0

    private var lastAt:
        String? =
        null

    private var lastFingerprint =
        ""

    private var sameFingerprintPasses =
        0

    private var clickedDelivery =
        false

    private var clickedAt =
        false

    private var clickedDownload =
        false

    private var clickedInRoute =
        false

    private var routePackagesDone =
        false

    private var dailyPhase =
        DailyPhase.NONE

    private var phaseStartedAt =
        0L

    private var statusStablePasses =
        0

    private var lastStatusCount:
        Int? =
        null

    private var statusTabClicked =
        false

    private var finished =
        false

    /*
     * Botão pequeno por cima do SPX durante a sincronização.
     * Resolve o caso em que o usuário fica preso no SPX sem conseguir
     * retornar para cancelar a importação.
     */
    private var syncOverlayView: View? =
        null

    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        SpxSessionState.initialize(
            applicationContext
        )

        RouteRepository.initialize(
            applicationContext
        )

        Log.d(
            TAG,
            "SERVICO=ATIVO"
        )
    }

    override fun onAccessibilityEvent(
        event:
            AccessibilityEvent?
    ) {
        if (
            event == null
        ) {
            return
        }

        SpxSessionState.initialize(
            applicationContext
        )

        if (
            event.packageName
                ?.toString() !=
            SPX_PACKAGE
        ) {
            return
        }

        if (
            !SpxSessionState
                .syncActive
                .value
        ) {
            hideSyncOverlay()
            handler.removeCallbacks(scanRunnable)
            return
        }

        val session =
            SpxSessionState
                .sessionId
                .value

        if (
            session !=
            activeSessionId
        ) {
            resetForSession(
                session
            )
        }

        if (
            finished
        ) {
            return
        }

        SpxSessionState
            .updatePackageName(
                SPX_PACKAGE
            )

        handler
            .removeCallbacks(
                scanRunnable
            )

        handler
            .postDelayed(
                scanRunnable,
                150L
            )
    }

    private fun resetForSession(
        session:
            Long
    ) {
        handler
            .removeCallbacks(
                scanRunnable
            )

        activeSessionId =
            session

        val now =
            SystemClock
                .elapsedRealtime()

        lastProgressAt =
            now

        lastNewPackageAt =
            now

        lastGestureAt =
            0L

        lastCount =
            0

        lastAt =
            null

        lastFingerprint =
            ""

        sameFingerprintPasses =
            0

        clickedDelivery =
            false

        clickedAt =
            false

        clickedDownload =
            false

        clickedInRoute =
            false

        routePackagesDone =
            false

        dailyPhase =
            DailyPhase.NONE

        phaseStartedAt =
            0L

        statusStablePasses =
            0

        lastStatusCount =
            null

        statusTabClicked =
            false

        finished =
            false

        showSyncOverlay()

        RouteRepository.clear()

        Log.d(
            TAG,
            "IMPORT=START | SESSION=$session"
        )
    }

    private fun scan() {
        if (
            !SpxSessionState
                .syncActive
                .value
        ) {
            hideSyncOverlay()
            handler.removeCallbacks(scanRunnable)
            return
        }

        if (finished) {
            return
        }

        val root =
            rootInActiveWindow
                ?: run {
                    update(
                        SpxState.WAITING_CONTENT,
                        "Aguardando o SPX carregar..."
                    )

                    schedule()
                    return
                }

        if (
            root.packageName
                ?.toString() !=
            SPX_PACKAGE
        ) {
            return
        }

        val texts =
            mutableListOf<String>()

        SpxParser.collectTexts(
            root,
            texts
        )

        if (
            texts.isEmpty()
        ) {
            update(
                SpxState.WAITING_CONTENT,
                "Aguardando conteúdo do SPX..."
            )

            schedule()
            return
        }

        val screen =
            SpxParser.normalizeScreen(
                texts
            )

        if (
            SpxParser.isLoginScreen(
                screen
            )
        ) {
            update(
                SpxState.LOGIN_REQUIRED,
                "Faça o login normalmente no SPX. O Copilot continua depois."
            )

            lastProgressAt =
                SystemClock
                    .elapsedRealtime()

            schedule(
                900L
            )

            return
        }

        if (
            SpxParser.isConsentScreen(
                screen
            )
        ) {
            update(
                SpxState.CONSENT_REQUIRED,
                "Confirme o aceite na tela oficial do SPX."
            )

            lastProgressAt =
                SystemClock
                    .elapsedRealtime()

            schedule(
                900L
            )

            return
        }

        if (
            SpxParser.isFaceCheckScreen(
                screen
            )
        ) {
            update(
                SpxState.FACE_CHECK_REQUIRED,
                "Conclua o reconhecimento facial no SPX."
            )

            lastProgressAt =
                SystemClock
                    .elapsedRealtime()

            schedule(
                900L
            )

            return
        }

        /*
         * Atualiza os contadores sempre que eles estiverem visíveis,
         * mas a rotina pós-importação é quem espera o carregamento
         * das abas antes de aceitar zero como valor final.
         */
        SpxParser
            .findOccurrenceCount(
                texts
            )
            ?.takeIf {
                it > 0
            }
            ?.let {
                SpxSessionState
                    .setOccurrenceCount(
                        it
                    )
            }

        SpxParser
            .findClosedCount(
                texts
            )
            ?.takeIf {
                it > 0
            }
            ?.let {
                SpxSessionState
                    .setClosedCount(
                        it
                    )
            }

        if (
            routePackagesDone
        ) {
            scanDailyStatus(
                root,
                texts
            )

            return
        }

        val at =
            SpxParser.findAt(
                texts
            )

        if (
            at != null
        ) {
            SpxSessionState.setAt(
                at
            )

            SpxSessionState.setDate(
                SpxParser.dateFromAt(
                    at
                )
            )

            if (
                at !=
                lastAt
            ) {
                lastAt =
                    at

                lastProgressAt =
                    SystemClock
                        .elapsedRealtime()

                Log.d(
                    TAG,
                    "ROTA_AT=DETECTADA"
                )
            }
        }

        val expected =
            SpxParser.findExpectedTotal(
                texts
            )

        if (
            expected != null
        ) {
            val old =
                SpxSessionState
                    .expectedTotal
                    .value

            SpxSessionState
                .setExpectedTotal(
                    expected
                )

            if (
                old !=
                expected
            ) {
                lastProgressAt =
                    SystemClock
                        .elapsedRealtime()

                Log.d(
                    TAG,
                    "TOTAL_ESPERADO=$expected"
                )
            }
        }

        val candidates =
            SpxParser
                .findPackageCandidates(
                    root
                )

        if (
            candidates.isNotEmpty()
        ) {
            RouteRepository
                .mergeCandidates(
                    candidates
                )

            val newCodes =
                SpxSessionState
                    .addPackageCodes(
                        candidates
                            .map {
                                it.br
                            }
                    )

            val fingerprint =
                candidates
                    .map {
                        it.br
                    }
                    .distinct()
                    .sorted()
                    .joinToString(
                        "|"
                    )

            if (
                fingerprint.isNotBlank() &&
                fingerprint ==
                lastFingerprint
            ) {
                sameFingerprintPasses++
            } else {
                lastFingerprint =
                    fingerprint

                sameFingerprintPasses =
                    0
            }

            if (
                newCodes >
                0
            ) {
                val now =
                    SystemClock
                        .elapsedRealtime()

                lastNewPackageAt =
                    now

                lastProgressAt =
                    now

                sameFingerprintPasses =
                    0

                val count =
                    SpxSessionState
                        .packageCount
                        .value

                if (
                    count !=
                    lastCount
                ) {
                    lastCount =
                        count

                    Log.d(
                        TAG,
                        "PACOTES_TOTAL=$count"
                    )
                }
            }

            update(
                SpxState.IMPORTING_PACKAGES,
                importMessage()
            )

            val count =
                SpxSessionState
                    .packageCount
                    .value

            val total =
                SpxSessionState
                    .expectedTotal
                    .value

            if (
                total != null &&
                total > 0 &&
                count >= total
            ) {
                beginDailyStatusSync()
                return
            }

            if (
                total == null &&
                count > 0 &&
                SystemClock
                    .elapsedRealtime() -
                lastNewPackageAt >=
                MIN_NO_NEW_MS &&
                sameFingerprintPasses >=
                SAME_PAGE_LIMIT
            ) {
                beginDailyStatusSync()
                return
            }

            if (
                tryScroll(
                    root
                )
            ) {
                schedule(
                    950L
                )

                return
            }

            if (
                tryGesture()
            ) {
                schedule(
                    1100L
                )

                return
            }

            schedule()
            return
        }

        val download =
            SpxParser
                .findClickableContaining(
                    root,
                    listOf(
                        "baixar rota",
                        "download",
                        "baixar pedidos",
                        "baixar dados"
                    )
                )

        if (
            download != null &&
            !clickedDownload
        ) {
            update(
                SpxState.DOWNLOAD_BUTTON_FOUND,
                "Download da rota localizado."
            )

            val clicked =
                runCatching {
                    download
                        .performAction(
                            AccessibilityNodeInfo
                                .ACTION_CLICK
                        )
                }
                    .getOrDefault(
                        false
                    )

            if (
                clicked
            ) {
                clickedDownload =
                    true

                lastProgressAt =
                    SystemClock
                        .elapsedRealtime()

                update(
                    SpxState.DOWNLOADING_ROUTE,
                    "Baixando a rota no SPX..."
                )

                Log.d(
                    TAG,
                    "NAV=DOWNLOAD"
                )

                schedule(
                    1500L
                )

                return
            }
        }

        if (
            at != null &&
            !clickedAt
        ) {
            val atNode =
                SpxParser
                    .findClickableExact(
                        root,
                        at
                    )

            if (
                atNode != null
            ) {
                update(
                    SpxState.ROUTE_DETECTED,
                    "Rota localizada. Abrindo AT..."
                )

                val clicked =
                    runCatching {
                        atNode
                            .performAction(
                                AccessibilityNodeInfo
                                    .ACTION_CLICK
                            )
                    }
                        .getOrDefault(
                            false
                        )

                if (
                    clicked
                ) {
                    clickedAt =
                        true

                    lastProgressAt =
                        SystemClock
                            .elapsedRealtime()

                    Log.d(
                        TAG,
                        "NAV=AT"
                    )

                    schedule(
                        1300L
                    )

                    return
                }
            }
        }

        if (
            !clickedInRoute
        ) {
            val inRoute =
                SpxParser
                    .findClickableStartsWith(
                        root,
                        "Em Rota"
                    )

            if (
                inRoute != null
            ) {
                update(
                    SpxState.OPENING_IN_ROUTE,
                    "Abrindo pedidos Em Rota..."
                )

                val clicked =
                    runCatching {
                        inRoute
                            .performAction(
                                AccessibilityNodeInfo
                                    .ACTION_CLICK
                            )
                    }
                        .getOrDefault(
                            false
                        )

                if (
                    clicked
                ) {
                    clickedInRoute =
                        true

                    lastProgressAt =
                        SystemClock
                            .elapsedRealtime()

                    Log.d(
                        TAG,
                        "NAV=EM_ROTA"
                    )

                    schedule(
                        1300L
                    )

                    return
                }
            }
        }

        if (
            !clickedDelivery
        ) {
            val delivery =
                SpxParser
                    .findClickableExact(
                        root,
                        "Entrega"
                    )
                    ?: SpxParser
                        .findClickableExact(
                            root,
                            "Entregas"
                        )

            if (
                delivery != null
            ) {
                update(
                    SpxState.OPENING_DELIVERIES,
                    "Abrindo Entrega..."
                )

                val clicked =
                    runCatching {
                        delivery
                            .performAction(
                                AccessibilityNodeInfo
                                    .ACTION_CLICK
                            )
                    }
                        .getOrDefault(
                            false
                        )

                if (
                    clicked
                ) {
                    clickedDelivery =
                        true

                    lastProgressAt =
                        SystemClock
                            .elapsedRealtime()

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

        update(
            SpxState.FINDING_ROUTE,
            "Procurando Entrega, AT e pedidos Em Rota..."
        )

        if (
            SystemClock
                .elapsedRealtime() -
            lastProgressAt >
            MAX_IDLE_WITHOUT_PROGRESS_MS
        ) {
            failAndReturn(
                "O SPX não avançou por alguns minutos. Abra Entrega / Em Rota e tente novamente."
            )

            return
        }

        schedule(
            900L
        )
    }

    private fun beginDailyStatusSync() {
        routePackagesDone =
            true

        dailyPhase =
            DailyPhase.OCCURRENCES

        phaseStartedAt =
            SystemClock
                .elapsedRealtime()

        statusStablePasses =
            0

        lastStatusCount =
            null

        statusTabClicked =
            false

        update(
            SpxState.SYNCING_OCCURRENCES,
            "Atualizando ocorrências do SPX..."
        )

        Log.d(
            TAG,
            "STATUS_SYNC=OCCURRENCES"
        )

        schedule(
            700L
        )
    }

    private fun scanDailyStatus(
        root:
            AccessibilityNodeInfo,

        texts:
            List<String>
    ) {
        when (
            dailyPhase
        ) {
            DailyPhase.OCCURRENCES ->
                scanOccurrences(
                    root,
                    texts
                )

            DailyPhase.CLOSED ->
                scanClosed(
                    root,
                    texts
                )

            DailyPhase.NONE ->
                finishImport()
        }
    }

    private fun scanOccurrences(
        root:
            AccessibilityNodeInfo,

        texts:
            List<String>
    ) {
        update(
            SpxState.SYNCING_OCCURRENCES,
            "Atualizando ocorrências do SPX..."
        )

        if (
            !statusTabClicked
        ) {
            val tab =
                SpxParser
                    .findClickableContaining(
                        root,
                        listOf(
                            "Ocorrência",
                            "Ocorrências",
                            "Ocorrencia",
                            "Ocorrencias"
                        )
                    )

            if (
                tab != null
            ) {
                val clicked =
                    runCatching {
                        tab.performAction(
                            AccessibilityNodeInfo
                                .ACTION_CLICK
                        )
                    }
                        .getOrDefault(
                            false
                        )

                if (
                    clicked
                ) {
                    statusTabClicked =
                        true

                    phaseStartedAt =
                        SystemClock
                            .elapsedRealtime()

                    Log.d(
                        TAG,
                        "NAV=OCORRENCIAS"
                    )

                    schedule(
                        1500L
                    )

                    return
                }
            }
        }

        val count =
            SpxParser
                .findOccurrenceCount(
                    texts
                )

        if (
            count != null
        ) {
            updateStableCount(
                count
            )

            /*
             * Valor positivo pode atualizar imediatamente.
             * Zero só é consolidado após a espera mínima.
             */
            if (
                count > 0
            ) {
                SpxSessionState
                    .setOccurrenceCount(
                        count
                    )
            }
        }

        val descriptions =
            SpxParser
                .findOccurrenceDescriptions(
                    texts
                )

        if (
            descriptions.isNotEmpty()
        ) {
            SpxSessionState
                .addOccurrenceDescriptions(
                    descriptions
                )
        }

        val elapsed =
            SystemClock
                .elapsedRealtime() -
            phaseStartedAt

        val ready =
            (
                count != null &&
                count > 0 &&
                statusStablePasses >= 2
                ) ||
                (
                    elapsed >=
                    STATUS_TAB_MIN_WAIT_MS &&
                    statusStablePasses >=
                    3
                    ) ||
                elapsed >=
                STATUS_TAB_MAX_WAIT_MS

        if (
            ready
        ) {
            SpxSessionState
                .setOccurrenceCount(
                    count
                        ?: SpxSessionState
                            .occurrenceCount
                            .value
                )

            startClosedPhase()
            return
        }

        schedule(
            900L
        )
    }

    private fun startClosedPhase() {
        dailyPhase =
            DailyPhase.CLOSED

        phaseStartedAt =
            SystemClock
                .elapsedRealtime()

        statusStablePasses =
            0

        lastStatusCount =
            null

        statusTabClicked =
            false

        update(
            SpxState.SYNCING_CLOSED,
            "Atualizando pedidos encerrados do dia..."
        )

        Log.d(
            TAG,
            "STATUS_SYNC=CLOSED"
        )

        schedule(
            700L
        )
    }

    private fun scanClosed(
        root:
            AccessibilityNodeInfo,

        texts:
            List<String>
    ) {
        update(
            SpxState.SYNCING_CLOSED,
            "Atualizando pedidos encerrados do dia..."
        )

        if (
            !statusTabClicked
        ) {
            val tab =
                SpxParser
                    .findClickableContaining(
                        root,
                        listOf(
                            "Encerrado",
                            "Encerrados",
                            "Finalizado",
                            "Finalizados",
                            "Concluído",
                            "Concluídos"
                        )
                    )

            if (
                tab != null
            ) {
                val clicked =
                    runCatching {
                        tab.performAction(
                            AccessibilityNodeInfo
                                .ACTION_CLICK
                        )
                    }
                        .getOrDefault(
                            false
                        )

                if (
                    clicked
                ) {
                    statusTabClicked =
                        true

                    phaseStartedAt =
                        SystemClock
                            .elapsedRealtime()

                    Log.d(
                        TAG,
                        "NAV=ENCERRADOS"
                    )

                    schedule(
                        1500L
                    )

                    return
                }
            }
        }

        val count =
            SpxParser
                .findClosedCount(
                    texts
                )

        if (
            count != null
        ) {
            updateStableCount(
                count
            )

            if (
                count > 0
            ) {
                SpxSessionState
                    .setClosedCount(
                        count
                    )
            }
        }

        val elapsed =
            SystemClock
                .elapsedRealtime() -
            phaseStartedAt

        val ready =
            (
                count != null &&
                count > 0 &&
                statusStablePasses >= 2
                ) ||
                (
                    elapsed >=
                    STATUS_TAB_MIN_WAIT_MS &&
                    statusStablePasses >=
                    3
                    ) ||
                elapsed >=
                STATUS_TAB_MAX_WAIT_MS

        if (
            ready
        ) {
            SpxSessionState
                .setClosedCount(
                    count
                        ?: SpxSessionState
                            .closedCount
                            .value
                )

            dailyPhase =
                DailyPhase.NONE

            finishImport()
            return
        }

        schedule(
            900L
        )
    }

    private fun updateStableCount(
        count:
            Int
    ) {
        if (
            count ==
            lastStatusCount
        ) {
            statusStablePasses++
        } else {
            lastStatusCount =
                count

            statusStablePasses =
                0
        }
    }

    private fun importMessage():
        String {
        val count =
            SpxSessionState
                .packageCount
                .value

        val total =
            SpxSessionState
                .expectedTotal
                .value

        return if (
            total != null
        ) {
            "Importando $count de $total pedidos..."
        } else {
            "Importando pedidos: $count encontrados..."
        }
    }

    private fun tryScroll(
        root:
            AccessibilityNodeInfo
    ): Boolean {
        val node =
            SpxParser
                .findLargestScrollable(
                    root
                )
                ?: return false

        val ok =
            runCatching {
                node.performAction(
                    AccessibilityNodeInfo
                        .ACTION_SCROLL_FORWARD
                )
            }
                .getOrDefault(
                    false
                )

        if (
            ok
        ) {
            Log.d(
                TAG,
                "SCROLL=NODE"
            )
        }

        return ok
    }

    private fun tryGesture():
        Boolean {
        val now =
            SystemClock
                .elapsedRealtime()

        if (
            now -
            lastGestureAt <
            GESTURE_INTERVAL_MS
        ) {
            return false
        }

        lastGestureAt =
            now

        val m =
            resources
                .displayMetrics

        val x =
            m.widthPixels *
            0.50f

        val startY =
            m.heightPixels *
            0.78f

        val endY =
            m.heightPixels *
            0.28f

        val path =
            Path()
                .apply {
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
            GestureDescription
                .Builder()
                .addStroke(
                    GestureDescription
                        .StrokeDescription(
                            path,
                            0L,
                            450L
                        )
                )
                .build()

        val ok =
            runCatching {
                dispatchGesture(
                    gesture,
                    null,
                    handler
                )
            }
                .getOrDefault(
                    false
                )

        if (
            ok
        ) {
            Log.d(
                TAG,
                "SCROLL=GESTURE"
            )
        }

        return ok
    }

    private fun finishImport() {
        val count =
            SpxSessionState
                .packageCount
                .value

        if (
            count <= 0
        ) {
            failAndReturn(
                "Nenhum pedido Em Rota foi identificado."
            )

            return
        }

        finished =
            true

        hideSyncOverlay()

        handler
            .removeCallbacks(
                scanRunnable
            )

        update(
            SpxState.VALIDATING_ROUTE,
            "Validando $count pedidos..."
        )

        Log.d(
            TAG,
            "IMPORT_COMPLETE | TOTAL=$count | OCORRENCIAS=${SpxSessionState.occurrenceCount.value} | ENCERRADOS=${SpxSessionState.closedCount.value}"
        )

        handler
            .postDelayed(
                {
                    update(
                        SpxState.CALCULATING_ROUTE,
                        "Preparando a rota no RouteCopilot..."
                    )

                    handler
                        .postDelayed(
                            {
                                returnToCopilot()
                            },
                            450L
                        )
                },
                450L
            )
    }

    private fun returnToCopilot() {
        update(
            SpxState.RETURNING_TO_COPILOT,
            "Voltando ao RouteCopilot..."
        )

        val intent =
            Intent(
                applicationContext,
                MainActivity::class.java
            )
                .apply {
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

        runCatching {
            startActivity(
                intent
            )
        }
            .onSuccess {
                SpxSessionState
                    .markRouteReady()

                Log.d(
                    TAG,
                    "RETURN=COPILOT"
                )
            }
            .onFailure {
                Log.e(
                    TAG,
                    "RETURN=FAILED",
                    it
                )
            }

        handler
            .postDelayed(
                {
                    if (
                        rootInActiveWindow
                            ?.packageName
                            ?.toString() ==
                        SPX_PACKAGE
                    ) {
                        Log.d(
                            TAG,
                            "RETURN=FALLBACK"
                        )

                        performGlobalAction(
                            GLOBAL_ACTION_HOME
                        )

                        handler
                            .postDelayed(
                                {
                                    runCatching {
                                        startActivity(
                                            intent
                                        )
                                    }
                                },
                                300L
                            )
                    }
                },
                1000L
            )
    }

    private fun failAndReturn(
        message:
            String
    ) {
        finished =
            true

        hideSyncOverlay()

        handler
            .removeCallbacks(
                scanRunnable
            )

        SpxSessionState.fail(
            message
        )

        Log.d(
            TAG,
            "IMPORT=ERROR"
        )

        val intent =
            Intent(
                applicationContext,
                MainActivity::class.java
            )
                .apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }

        runCatching {
            startActivity(
                intent
            )
        }
    }

    private fun showSyncOverlay() {
        if (syncOverlayView != null) return

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val button = TextView(this).apply {
            text = "← VOLTAR / CANCELAR"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
            )

            background = GradientDrawable().apply {
                setColor(Color.rgb(18, 103, 227))
                cornerRadius = dp(22).toFloat()
            }

            elevation = dp(8).toFloat()

            setOnClickListener {
                cancelSyncAndReturn()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(56)
        }

        runCatching {
            windowManager.addView(button, params)
            syncOverlayView = button
            Log.d(TAG, "SYNC_OVERLAY=SHOW")
        }.onFailure {
            Log.e(TAG, "SYNC_OVERLAY=FAILED", it)
        }
    }

    private fun hideSyncOverlay() {
        val view = syncOverlayView ?: return
        syncOverlayView = null

        runCatching {
            windowManager.removeView(view)
        }
    }

    private fun cancelSyncAndReturn() {
        finished = true

        handler.removeCallbacks(scanRunnable)
        SpxSessionState.reset()
        hideSyncOverlay()

        Log.d(TAG, "IMPORT=CANCELLED")

        val intent = Intent(
            applicationContext,
            MainActivity::class.java
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )

            putExtra("SYNC_CANCELLED", true)
        }

        /*
         * Em alguns Samsung o startActivity() pode retornar sucesso,
         * mas o SPX continuar visualmente em primeiro plano.
         * Forçamos HOME e então trazemos o RouteCopilot para frente.
         */
        performGlobalAction(GLOBAL_ACTION_HOME)

        handler.postDelayed({
            runCatching {
                startActivity(intent)
            }.onFailure {
                Log.e(TAG, "CANCEL_RETURN=FAILED", it)
            }
        }, 250L)

        /*
         * Segundo fallback: se o SPX ainda estiver em primeiro plano,
         * repetimos a ida para HOME + RouteCopilot.
         */
        handler.postDelayed({
            if (
                rootInActiveWindow
                    ?.packageName
                    ?.toString() == SPX_PACKAGE
            ) {
                Log.d(TAG, "CANCEL_RETURN=FALLBACK")

                performGlobalAction(GLOBAL_ACTION_HOME)

                handler.postDelayed({
                    runCatching {
                        startActivity(intent)
                    }.onFailure {
                        Log.e(TAG, "CANCEL_RETURN=FAILED_2", it)
                    }
                }, 250L)
            }
        }, 1100L)
    }

    private fun update(
        state:
            SpxState,

        message:
            String
    ) {
        if (
            SpxSessionState
                .state
                .value !=
            state ||
            SpxSessionState
                .message
                .value !=
            message
        ) {
            SpxSessionState.update(
                state,
                message
            )

            Log.d(
                TAG,
                "STATUS=$state"
            )
        }
    }

    private fun schedule(
        delay:
            Long =
            SCAN_DELAY_MS
    ) {
        handler
            .removeCallbacks(
                scanRunnable
            )

        handler
            .postDelayed(
                scanRunnable,
                delay
            )
    }

    override fun onInterrupt() {
        Log.d(
            TAG,
            "SERVICO=INTERROMPIDO"
        )
    }

    override fun onDestroy() {
        handler
            .removeCallbacks(
                scanRunnable
            )

        hideSyncOverlay()

        super.onDestroy()
    }
}
