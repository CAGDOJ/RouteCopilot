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
import com.routecopilot.data.model.DeliveryStatus
import com.routecopilot.data.repository.RouteRepository

import java.util.Calendar
import java.util.GregorianCalendar

class SpxAccessibilityService :
    AccessibilityService() {

    companion object {

        private const val TAG =
            "RouteCopilotACC"

        private const val SPX_PACKAGE =
            "com.shopee.spx.driver.brazil"

        private const val IMPORT_DELAY =
            700L

        private const val MONITOR_DELAY =
            350L

        private const val NAV_DELAY =
            1500L

        private const val MIN_GESTURE_INTERVAL =
            750L

        private const val END_WITHOUT_NEW_MS =
            15_000L

        private const val SAME_PAGE_LIMIT =
            10

        private const val STAGNANT_LIMIT =
            16
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var loginAvisado =
        false

    private var returnedInitialSync =
        false

    private var lastAt:
        String? = null

    private var lastPackageCount =
        0

    private var lastNewPackageTime =
        SystemClock.elapsedRealtime()

    private var lastNavigationTime =
        0L

    private var lastGestureTime =
        0L

    private var stagnant =
        0

    private var lastFingerprint =
        ""

    private var samePageCount =
        0

    private var lastReturnedCode:
        String? = null

    private var lastReturnedStatus:
        DeliveryStatus? = null

    private val scanRunnable =
        Runnable {

            scanCurrentScreen()
        }

    override fun onServiceConnected() {

        super.onServiceConnected()

        SpxSessionState.updateState(
            SpxState.SERVICE_READY
        )

        SpxSessionState.updateMessage(
            "Serviço SPX ativo."
        )

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
            event
                .packageName
                ?.toString()
                ?: return

        if (
            packageName !=
            SPX_PACKAGE
        ) {
            return
        }

        SpxSessionState
            .updatePackageName(
                packageName
            )

        scheduleScan(
            if (
                SpxSessionState
                    .syncMode
                    .value ==
                SpxSyncMode.MONITORING
            ) {
                MONITOR_DELAY
            } else {
                150L
            }
        )
    }

    private fun scheduleScan(
        delay: Long
    ) {

        handler.removeCallbacks(
            scanRunnable
        )

        handler.postDelayed(
            scanRunnable,
            delay
        )
    }

    private fun scanCurrentScreen() {

        val root =
            rootInActiveWindow
                ?: run {

                    scheduleScan(
                        currentDelay()
                    )

                    return
                }

        val textos =
            mutableListOf<String>()

        collectTexts(
            root,
            textos
        )

        if (textos.isEmpty()) {

            SpxSessionState.updateState(
                SpxState.WAITING_CONTENT
            )

            scheduleScan(
                currentDelay()
            )

            return
        }

        val snapshot =
            SpxParser.parse(
                textos
            )

        if (
            snapshot.looksLikeLogin
        ) {

            SpxSessionState.updateState(
                SpxState.LOGIN_REQUIRED
            )

            SpxSessionState.updateMessage(
                "Faça login normalmente no SPX."
            )

            if (
                !loginAvisado
            ) {

                loginAvisado =
                    true

                Toast.makeText(
                    applicationContext,
                    "Faça o login no SPX. O RouteCopilot continua automaticamente.",
                    Toast.LENGTH_LONG
                ).show()
            }

            scheduleScan(
                800L
            )

            return
        }

        if (
            loginAvisado &&
            snapshot.looksAuthenticated
        ) {

            loginAvisado =
                false

            SpxSessionState.updateState(
                SpxState.AUTHENTICATED
            )

            SpxSessionState.updateMessage(
                "Login concluído. Sincronizando..."
            )

            Log.d(
                TAG,
                "STATUS=AUTHENTICATED"
            )
        }

        snapshot.at?.let { at ->

            SpxSessionState.updateAtCode(
                at
            )

            SpxSessionState
                .updateDataCarregamento(
                    extractDateFromAt(
                        at
                    )
                )

            if (
                lastAt != at
            ) {

                lastAt =
                    at

                Log.d(
                    TAG,
                    "ROTA_AT=DETECTADA"
                )
            }
        }

        snapshot
            .expectedTotal
            ?.let { total ->

                val before =
                    SpxSessionState
                        .totalEsperado
                        .value

                SpxSessionState
                    .updateTotalEsperado(
                        total
                    )

                if (
                    before != total
                ) {

                    Log.d(
                        TAG,
                        "TOTAL_ESPERADO=$total"
                    )
                }
            }

        val novos =
            SpxSessionState
                .addPackageCodes(
                    snapshot
                        .trackingCodes
                )

        snapshot
            .trackingCodes
            .forEach { code ->

                RouteRepository
                    .ensureDelivery(
                        trackingCode =
                            code
                    )
            }

        val quantidade =
            SpxSessionState
                .packageCount
                .value

        if (
            novos > 0
        ) {

            stagnant =
                0

            samePageCount =
                0

            lastNewPackageTime =
                SystemClock.elapsedRealtime()

            if (
                quantidade !=
                lastPackageCount
            ) {

                lastPackageCount =
                    quantidade

                Log.d(
                    TAG,
                    "PACOTES_TOTAL=$quantidade"
                )
            }

        } else if (
            quantidade > 0
        ) {

            stagnant++
        }

        updateFingerprint(
            snapshot.trackingCodes
        )

        snapshot
            .currentTrackingCode
            ?.let { code ->

                RouteRepository
                    .upsertDelivery(

                        trackingCode =
                            code,

                        customerName =
                            snapshot
                                .customerName,

                        phone =
                            snapshot
                                .phone,

                        address =
                            snapshot
                                .address,

                        neighborhood =
                            snapshot
                                .neighborhood,

                        status =
                            snapshot
                                .status
                    )

                snapshot
                    .status
                    ?.let { status ->

                        SpxSessionState
                            .touchSync()

                        Log.d(
                            TAG,
                            "PEDIDO_STATUS=$status"
                        )

                        when (
                            status
                        ) {

                            DeliveryStatus.DELIVERED,

                            DeliveryStatus.OCCURRENCE,

                            DeliveryStatus.RETURN_LATER -> {

                                if (
                                    lastReturnedCode != code ||
                                    lastReturnedStatus != status
                                ) {

                                    lastReturnedCode =
                                        code

                                    lastReturnedStatus =
                                        status

                                    SpxSessionState
                                        .markMonitoring()

                                    Log.d(
                                        TAG,
                                        "SPX_UPDATE_APPLIED"
                                    )

                                    returnToCopilot(
                                        "STATUS_UPDATED"
                                    )
                                }
                            }

                            else -> {
                            }
                        }
                    }
            }

        if (
            SpxSessionState
                .syncMode
                .value ==
            SpxSyncMode.MONITORING
        ) {

            SpxSessionState
                .markMonitoring()

            scheduleScan(
                MONITOR_DELAY
            )

            return
        }

        val totalEsperado =
            SpxSessionState
                .totalEsperado
                .value

        if (
            totalEsperado != null &&
            totalEsperado > 0 &&
            quantidade >= totalEsperado
        ) {

            finishInitialSync()

            return
        }

        if (
            shouldFinishWithoutKnownTotal(
                quantidade,
                totalEsperado
            )
        ) {

            finishInitialSync()

            return
        }

        if (
            quantidade > 0
        ) {

            SpxSessionState.updateState(
                SpxState.SCANNING_PACKAGES
            )

            SpxSessionState.updateSyncMode(
                SpxSyncMode.IMPORTING
            )

            SpxSessionState.updateMessage(

                if (
                    totalEsperado != null
                ) {

                    "Sincronizando $quantidade de $totalEsperado..."

                } else {

                    "Sincronizando $quantidade pedidos..."
                }
            )

            if (
                scrollByNode(
                    root
                )
            ) {

                Log.d(
                    TAG,
                    "SCROLL=NODE"
                )

                scheduleScan(
                    900L
                )

                return
            }

            if (
                swipeUp()
            ) {

                Log.d(
                    TAG,
                    "SCROLL=GESTURE"
                )

                scheduleScan(
                    1100L
                )

                return
            }

            scheduleScan(
                900L
            )

            return
        }

        snapshot
            .at
            ?.let { at ->

                SpxSessionState
                    .updateState(
                        SpxState.ROUTE_DETECTED
                    )

                SpxSessionState
                    .updateMessage(
                        "Rota encontrada. Abrindo..."
                    )

                tryOpenAt(
                    root,
                    at
                )

                scheduleScan(
                    850L
                )

                return
            }

        if (
            snapshot
                .looksAuthenticated
        ) {

            SpxSessionState.updateState(
                SpxState.FINDING_ROUTE
            )

            SpxSessionState.updateMessage(
                "Localizando rota..."
            )

            tryOpenDeliveries(
                root
            )

            scheduleScan(
                850L
            )

            return
        }

        SpxSessionState.updateState(
            SpxState.CHECKING_SESSION
        )

        scheduleScan(
            800L
        )
    }

    private fun shouldFinishWithoutKnownTotal(
        quantidade: Int,
        totalEsperado: Int?
    ): Boolean {

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

        val elapsed =
            SystemClock.elapsedRealtime() -
                lastNewPackageTime

        return (
            elapsed >=
                END_WITHOUT_NEW_MS &&

            stagnant >=
                STAGNANT_LIMIT &&

            samePageCount >=
                SAME_PAGE_LIMIT
        )
    }

    private fun finishInitialSync() {

        SpxSessionState
            .markSynced()

        Log.d(
            TAG,
            "SYNC_INITIAL_COMPLETE | TOTAL=${SpxSessionState.packageCount.value}"
        )

        if (
            !returnedInitialSync
        ) {

            returnedInitialSync =
                true

            Toast.makeText(
                applicationContext,
                "SPX sincronizado.",
                Toast.LENGTH_SHORT
            ).show()

            returnToCopilot(
                "INITIAL_SYNC"
            )
        }
    }

    private fun updateFingerprint(
        codes: Set<String>
    ) {

        if (
            codes.isEmpty()
        ) {
            return
        }

        val fingerprint =
            codes
                .sorted()
                .joinToString(
                    "|"
                )

        if (
            fingerprint ==
            lastFingerprint
        ) {

            samePageCount++

        } else {

            lastFingerprint =
                fingerprint

            samePageCount =
                0
        }
    }

    private fun scrollByNode(
        root: AccessibilityNodeInfo
    ): Boolean {

        val nodes =
            mutableListOf<
                AccessibilityNodeInfo
            >()

        collectScrollables(
            root,
            nodes
        )

        val sorted =
            nodes
                .sortedByDescending { node ->

                    val rect =
                        Rect()

                    node.getBoundsInScreen(
                        rect
                    )

                    rect.height()
                }

        for (
            node in sorted
        ) {

            try {

                if (
                    node.performAction(
                        AccessibilityNodeInfo
                            .ACTION_SCROLL_FORWARD
                    )
                ) {

                    return true
                }

            } catch (
                _: Exception
            ) {
            }
        }

        return false
    }

    private fun swipeUp():
        Boolean {

        val now =
            SystemClock.elapsedRealtime()

        if (
            now -
            lastGestureTime <
            MIN_GESTURE_INTERVAL
        ) {

            return false
        }

        lastGestureTime =
            now

        val metrics =
            resources
                .displayMetrics

        val x =
            metrics
                .widthPixels *
                0.5f

        val startY =
            metrics
                .heightPixels *
                0.78f

        val endY =
            metrics
                .heightPixels *
                0.28f

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

        return try {

            dispatchGesture(
                gesture,
                null,
                handler
            )

        } catch (
            _: Exception
        ) {

            false
        }
    }

    private fun tryOpenDeliveries(
        root: AccessibilityNodeInfo
    ) {

        if (
            !canNavigate()
        ) {
            return
        }

        for (
            text in
            listOf(
                "Entregas",
                "Entrega"
            )
        ) {

            val node =
                findNodeByText(
                    root,
                    text,
                    false
                )

            if (
                node != null &&
                clickNodeOrParent(
                    node
                )
            ) {

                registerNavigation()

                Log.d(
                    TAG,
                    "NAV=ENTREGAS"
                )

                return
            }
        }
    }

    private fun tryOpenAt(
        root: AccessibilityNodeInfo,
        at: String
    ) {

        if (
            !canNavigate()
        ) {
            return
        }

        val node =
            findNodeByText(
                root,
                at,
                true
            )

        if (
            node != null &&
            clickNodeOrParent(
                node
            )
        ) {

            registerNavigation()

            Log.d(
                TAG,
                "NAV=ROTA"
            )
        }
    }

    private fun canNavigate():
        Boolean {

        return (
            SystemClock.elapsedRealtime() -
                lastNavigationTime
        ) >= NAV_DELAY
    }

    private fun registerNavigation() {

        lastNavigationTime =
            SystemClock.elapsedRealtime()
    }

    private fun findNodeByText(
        node: AccessibilityNodeInfo?,
        text: String,
        exact: Boolean
    ): AccessibilityNodeInfo? {

        if (
            node == null
        ) {
            return null
        }

        if (
            !node.isPassword
        ) {

            val values =
                listOfNotNull(

                    node
                        .text
                        ?.toString()
                        ?.trim(),

                    node
                        .contentDescription
                        ?.toString()
                        ?.trim()
                )

            if (
                values.any { value ->

                    if (
                        exact
                    ) {

                        value.equals(
                            text,
                            true
                        )

                    } else {

                        value.contains(
                            text,
                            true
                        )
                    }
                }
            ) {

                return node
            }
        }

        for (
            i in
            0 until
                node.childCount
        ) {

            val result =
                findNodeByText(

                    node.getChild(
                        i
                    ),

                    text,

                    exact
                )

            if (
                result != null
            ) {

                return result
            }
        }

        return null
    }

    private fun clickNodeOrParent(
        original: AccessibilityNodeInfo
    ): Boolean {

        var node:
            AccessibilityNodeInfo? =
            original

        var depth =
            0

        while (
            node != null &&
            depth < 7
        ) {

            if (
                node.isClickable
            ) {

                return try {

                    node.performAction(
                        AccessibilityNodeInfo
                            .ACTION_CLICK
                    )

                } catch (
                    _: Exception
                ) {

                    false
                }
            }

            node =
                node.parent

            depth++
        }

        return false
    }

    private fun collectTexts(
        node: AccessibilityNodeInfo?,
        result: MutableList<String>
    ) {

        if (
            node == null
        ) {
            return
        }

        if (
            !node.isPassword
        ) {

            node
                .text
                ?.toString()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {

                    result.add(
                        it
                    )
                }

            node
                .contentDescription
                ?.toString()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {

                    if (
                        it !in result
                    ) {

                        result.add(
                            it
                        )
                    }
                }
        }

        for (
            i in
            0 until
                node.childCount
        ) {

            collectTexts(

                node.getChild(
                    i
                ),

                result
            )
        }
    }

    private fun collectScrollables(
        node: AccessibilityNodeInfo?,
        result:
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

            result.add(
                node
            )
        }

        for (
            i in
            0 until
                node.childCount
        ) {

            collectScrollables(

                node.getChild(
                    i
                ),

                result
            )
        }
    }

    private fun extractDateFromAt(
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

        val year =
            match
                .groupValues[1]
                .toIntOrNull()
                ?: return null

        val month =
            match
                .groupValues[2]
                .toIntOrNull()
                ?: return null

        val day =
            match
                .groupValues[3]
                .toIntOrNull()
                ?: return null

        if (
            year !in 2020..2100
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
                        year
                    )

                    set(
                        Calendar.MONTH,
                        month - 1
                    )

                    set(
                        Calendar.DAY_OF_MONTH,
                        day
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
            day,
            month,
            year
        )
    }

    private fun currentDelay():
        Long {

        return if (
            SpxSessionState
                .syncMode
                .value ==
            SpxSyncMode.MONITORING
        ) {

            MONITOR_DELAY

        } else {

            IMPORT_DELAY
        }
    }

    private fun returnToCopilot(
        reason: String
    ) {

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

                putExtra(
                    "SPX_RETURN_REASON",
                    reason
                )
            }

        try {

            startActivity(
                intent
            )

            SpxSessionState
                .markMonitoring()

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "ERRO_RETORNO_COPILOT",
                e
            )
        }
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
