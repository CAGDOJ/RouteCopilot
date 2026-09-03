package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
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
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var lastGestureTime =
        0L

    private var lastNewPackageTime =
        SystemClock.elapsedRealtime()

    private var stagnantPasses =
        0

    private var loginWarningShown =
        false

    private var photoWarningShown =
        false

    private var returnInProgress =
        false

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
            event?.packageName
                ?.toString() !=
            SPX_PACKAGE
        ) {
            return
        }

        when (
            SpxSessionState
                .automationMode
                .value
        ) {
            SpxAutomationMode.IMPORT_ROUTE -> {
                schedule {
                    scanImport()
                }
            }

            SpxAutomationMode.OUT_OF_ROUTE -> {
                schedule {
                    scanOutOfRoute()
                }
            }

            SpxAutomationMode.NONE -> Unit
        }
    }

    private fun schedule(
        delay: Long = 250L,
        action: () -> Unit
    ) {
        handler.postDelayed(
            action,
            delay
        )
    }

    private fun scanImport() {
        if (
            SpxSessionState
                .automationMode
                .value !=
            SpxAutomationMode.IMPORT_ROUTE
        ) {
            return
        }

        val root =
            rootInActiveWindow
                ?: return

        val texts =
            collectTexts(root)

        if (
            texts.isEmpty()
        ) {
            return
        }

        val fullText =
            texts.joinToString(" ")

        val lower =
            fullText.lowercase()

        if (
            looksLikeLogin(lower)
        ) {
            SpxSessionState.setState(
                SpxState.LOGIN_REQUIRED,
                "Faça o login no SPX."
            )

            if (
                !loginWarningShown
            ) {
                loginWarningShown =
                    true

                Toast.makeText(
                    applicationContext,
                    "Faça o login normalmente no SPX. Depois o Copilot continuará sozinho.",
                    Toast.LENGTH_LONG
                ).show()
            }

            return
        }

        loginWarningShown =
            false

        val at =
            findAt(
                fullText
            )

        if (
            at != null
        ) {
            SpxSessionState.setAt(
                at
            )

            SpxSessionState.setLoadDate(
                parseDateFromAt(
                    at
                )
            )
        }

        val expected =
            findExpectedTotal(
                fullText
            )

        SpxSessionState.setExpectedTotal(
            expected
        )

        val visibleBrs =
            findBrs(
                texts
            )

        val newCount =
            SpxSessionState.addBrs(
                visibleBrs
            )

        val count =
            SpxSessionState
                .packageCount
                .value

        val expectedNow =
            SpxSessionState
                .totalEsperado
                .value

        if (
            newCount > 0
        ) {
            stagnantPasses =
                0

            lastNewPackageTime =
                SystemClock.elapsedRealtime()

            Log.d(
                TAG,
                "PACOTES_TOTAL=$count"
            )
        } else if (
            count > 0
        ) {
            stagnantPasses++
        }

        if (
            expectedNow != null &&
            expectedNow > 0 &&
            count >= expectedNow
        ) {
            completeImport()

            return
        }

        if (
            count > 0
        ) {
            SpxSessionState.setState(
                SpxState.SCANNING_PACKAGES,
                if (
                    expectedNow != null
                ) {
                    "Importando $count de $expectedNow"
                } else {
                    "Importando $count pedidos"
                }
            )

            val moved =
                scrollNode(
                    root
                ) ||
                swipeUp()

            if (
                !moved &&
                expectedNow == null &&
                stagnantPasses >= 20 &&
                SystemClock.elapsedRealtime() -
                lastNewPackageTime >
                15000L
            ) {
                completeImport()
            }

            return
        }

        if (
            at != null
        ) {
            SpxSessionState.setState(
                SpxState.ROUTE_DETECTED,
                "Rota encontrada. Abrindo pedidos..."
            )

            findText(
                root,
                at,
                exact = true
            )?.let {
                clickSelfOrParent(
                    it
                )
            }

            return
        }

        if (
            lower.contains(
                "entrega"
            ) ||
            lower.contains(
                "em rota"
            )
        ) {
            SpxSessionState.setState(
                SpxState.FINDING_ROUTE,
                "Localizando rota..."
            )

            findText(
                root,
                "Entrega",
                exact = false
            )?.let {
                clickSelfOrParent(
                    it
                )
            }
        }
    }

    private fun completeImport() {
        if (
            returnInProgress
        ) {
            return
        }

        returnInProgress =
            true

        val total =
            SpxSessionState
                .packageCount
                .value

        SpxSessionState.setState(
            SpxState.IMPORT_COMPLETE,
            "Importação concluída."
        )

        Log.d(
            TAG,
            "IMPORT_COMPLETE | TOTAL=$total"
        )

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )

                putExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    true
                )
            }

        try {
            startActivity(
                intent
            )

            handler.postDelayed(
                {
                    val activePackage =
                        rootInActiveWindow
                            ?.packageName
                            ?.toString()

                    if (
                        activePackage ==
                        SPX_PACKAGE
                    ) {
                        performGlobalAction(
                            GLOBAL_ACTION_HOME
                        )

                        handler.postDelayed(
                            {
                                try {
                                    startActivity(
                                        intent
                                    )
                                } catch (
                                    e: Exception
                                ) {
                                    Log.e(
                                        TAG,
                                        "RETORNO_COPILOT_2",
                                        e
                                    )
                                }
                            },
                            350L
                        )
                    }

                    SpxSessionState
                        .finishImport()

                    returnInProgress =
                        false
                },
                900L
            )
        } catch (
            e: Exception
        ) {
            returnInProgress =
                false

            Log.e(
                TAG,
                "RETORNO_COPILOT",
                e
            )
        }
    }

    private fun scanOutOfRoute() {
        val root =
            rootInActiveWindow
                ?: return

        val texts =
            collectTexts(root)

        val fullText =
            texts.joinToString(" ")

        val lower =
            fullText.lowercase()

        val targetBr =
            SpxSessionState
                .targetBr
                .value

        if (
            looksLikeLogin(lower)
        ) {
            return
        }

        if (
            lower.contains(
                "motivo da ocorrência"
            ) ||
            lower.contains(
                "motivo da ocorrencia"
            )
        ) {
            val outside =
                findText(
                    root,
                    "Fora de Rota",
                    exact = true
                )

            if (
                outside == null
            ) {
                if (
                    !scrollNode(root)
                ) {
                    swipeUp()
                }

                return
            }

            clickSelfOrParent(
                outside
            )

            SpxSessionState
                .setOccurrencePhase(
                    OccurrencePhase.REASON_SELECTED
                )

            schedule(
                500L
            ) {
                rootInActiveWindow
                    ?.let { current ->
                        (
                            findText(
                                current,
                                "Próximo",
                                true
                            )
                                ?: findText(
                                    current,
                                    "Proximo",
                                    true
                                )
                            )?.let {
                                clickSelfOrParent(
                                    it
                                )
                            }
                    }
            }

            return
        }

        if (
            lower.contains(
                "comprovante de ocorrência"
            ) ||
            lower.contains(
                "comprovante de ocorrencia"
            )
        ) {
            fillFirstEditable(
                root,
                "FORA DE ROTA"
            )

            val invalid =
                lower.contains(
                    "não encontramos o código do pacote"
                ) ||
                lower.contains(
                    "nao encontramos o codigo do pacote"
                )

            if (
                invalid
            ) {
                SpxSessionState
                    .setOccurrencePhase(
                        OccurrencePhase.INVALID_PHOTO
                    )

                return
            }

            val photos =
                Regex(
                    """\b(\d+)\s*/\s*3\b"""
                )
                    .find(
                        fullText
                    )
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0

            if (
                photos < 1
            ) {
                SpxSessionState
                    .setOccurrencePhase(
                        OccurrencePhase.WAITING_PHOTO
                    )

                if (
                    !photoWarningShown
                ) {
                    photoWarningShown =
                        true

                    Toast.makeText(
                        applicationContext,
                        "Tire a foto real do pacote com o código visível. Depois o Copilot continua.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                return
            }

            photoWarningShown =
                false

            findText(
                root,
                "Confirmar",
                true
            )?.let {
                if (
                    clickSelfOrParent(
                        it
                    )
                ) {
                    SpxSessionState
                        .setOccurrencePhase(
                            OccurrencePhase.CONFIRMING
                        )
                }
            }

            return
        }

        if (
            lower.contains(
                "informações do pedido"
            ) ||
            lower.contains(
                "informacoes do pedido"
            )
        ) {
            if (
                targetBr == null ||
                lower.contains(
                    targetBr.lowercase()
                )
            ) {
                (
                    findText(
                        root,
                        "Ocorrência",
                        true
                    )
                        ?: findText(
                            root,
                            "Ocorrencia",
                            true
                        )
                    )?.let {
                        clickSelfOrParent(
                            it
                        )
                    }
            }

            return
        }

        if (
            SpxSessionState
                .occurrencePhase
                .value ==
            OccurrencePhase.CONFIRMING &&
            (
                lower.contains(
                    "em rota"
                ) ||
                lower.contains(
                    "entrega"
                )
                )
        ) {
            SpxSessionState
                .finishOutOfRoute()

            returnToCopilot()

            return
        }

        if (
            targetBr != null &&
            lower.contains(
                "em rota"
            )
        ) {
            val node =
                findText(
                    root,
                    targetBr,
                    true
                )

            if (
                node != null
            ) {
                clickSelfOrParent(
                    node
                )
            } else {
                if (
                    !scrollNode(root)
                ) {
                    swipeUp()
                }
            }
        }
    }

    private fun returnToCopilot() {
        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )

                putExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    true
                )
            }

        try {
            startActivity(
                intent
            )
        } catch (
            e: Exception
        ) {
            Log.e(
                TAG,
                "RETORNO_OCORRENCIA",
                e
            )
        }
    }

    private fun findAt(
        text: String
    ): String? {
        return Regex(
            """\bAT[A-Z0-9]{8,}\b""",
            RegexOption.IGNORE_CASE
        )
            .find(
                text.replace(
                    " ",
                    ""
                )
            )
            ?.value
            ?.uppercase()
    }

    private fun findExpectedTotal(
        text: String
    ): Int? {
        val emRota =
            Regex(
                """\bEm\s+Rota\s*\(\s*(\d{1,4})\s*\)""",
                RegexOption.IGNORE_CASE
            )
                .find(
                    text
                )
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        if (
            emRota != null &&
            emRota > 0
        ) {
            return emRota
        }

        val fraction =
            Regex(
                """(?<!\d)(\d{1,4})\s*/\s*(\d{1,4})(?!\d)"""
            )
                .find(
                    text
                )

        val current =
            fraction
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        val total =
            fraction
                ?.groupValues
                ?.getOrNull(2)
                ?.toIntOrNull()

        if (
            current != null &&
            total != null &&
            total > 3 &&
            current <= total
        ) {
            return total
        }

        return null
    }

    private fun findBrs(
        texts: List<String>
    ): Set<String> {
        val regex =
            Regex(
                """\bBR[A-Z0-9]{8,}\b""",
                RegexOption.IGNORE_CASE
            )

        val result =
            linkedSetOf<String>()

        texts.forEach { text ->
            regex.findAll(
                text
                    .replace(
                        " ",
                        ""
                    )
                    .replace(
                        "\n",
                        ""
                    )
            ).forEach {
                result +=
                    it.value.uppercase()
            }
        }

        return result
    }

    private fun collectTexts(
        root: AccessibilityNodeInfo
    ): List<String> {
        val result =
            mutableListOf<String>()

        fun walk(
            node: AccessibilityNodeInfo?
        ) {
            if (
                node == null
            ) {
                return
            }

            if (
                !node.isPassword
            ) {
                node.text
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        result += it
                    }

                node.contentDescription
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        result += it
                    }
            }

            for (
                index in
                0 until node.childCount
            ) {
                walk(
                    node.getChild(index)
                )
            }
        }

        walk(
            root
        )

        return result
    }

    private fun findText(
        node: AccessibilityNodeInfo?,
        target: String,
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
                listOf(
                    node.text
                        ?.toString(),
                    node.contentDescription
                        ?.toString()
                )

            val matches =
                values.any { value ->
                    if (
                        value == null
                    ) {
                        false
                    } else if (
                        exact
                    ) {
                        value
                            .trim()
                            .equals(
                                target,
                                ignoreCase = true
                            )
                    } else {
                        value.contains(
                            target,
                            ignoreCase = true
                        )
                    }
                }

            if (
                matches
            ) {
                return node
            }
        }

        for (
            index in
            0 until node.childCount
        ) {
            val result =
                findText(
                    node.getChild(index),
                    target,
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

    private fun clickSelfOrParent(
        start: AccessibilityNodeInfo
    ): Boolean {
        var current:
            AccessibilityNodeInfo? =
            start

        repeat(8) {
            if (
                current?.isClickable ==
                true &&
                current?.isEnabled ==
                true
            ) {
                return try {
                    current?.performAction(
                        AccessibilityNodeInfo
                            .ACTION_CLICK
                    ) == true
                } catch (
                    _: Exception
                ) {
                    false
                }
            }

            current =
                current?.parent
        }

        return false
    }

    private fun scrollNode(
        root: AccessibilityNodeInfo
    ): Boolean {
        val scrollables =
            mutableListOf<
                AccessibilityNodeInfo
            >()

        fun walk(
            node: AccessibilityNodeInfo?
        ) {
            if (
                node == null
            ) {
                return
            }

            if (
                node.isScrollable
            ) {
                scrollables +=
                    node
            }

            for (
                index in
                0 until node.childCount
            ) {
                walk(
                    node.getChild(index)
                )
            }
        }

        walk(root)

        return scrollables.any {
            try {
                it.performAction(
                    AccessibilityNodeInfo
                        .ACTION_SCROLL_FORWARD
                )
            } catch (
                _: Exception
            ) {
                false
            }
        }
    }

    private fun swipeUp(): Boolean {
        val now =
            SystemClock.elapsedRealtime()

        if (
            now -
            lastGestureTime <
            750L
        ) {
            return false
        }

        lastGestureTime =
            now

        val metrics =
            resources.displayMetrics

        val path =
            Path().apply {
                moveTo(
                    metrics.widthPixels *
                        0.50f,
                    metrics.heightPixels *
                        0.78f
                )

                lineTo(
                    metrics.widthPixels *
                        0.50f,
                    metrics.heightPixels *
                        0.28f
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
                            420L
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

    private fun fillFirstEditable(
        root: AccessibilityNodeInfo,
        value: String
    ) {
        fun walk(
            node: AccessibilityNodeInfo?
        ): Boolean {
            if (
                node == null
            ) {
                return false
            }

            if (
                !node.isPassword &&
                node.isEditable &&
                node.isEnabled
            ) {
                val current =
                    node.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (
                    current.equals(
                        value,
                        ignoreCase = true
                    )
                ) {
                    return true
                }

                val args =
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo
                                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            value
                        )
                    }

                if (
                    node.performAction(
                        AccessibilityNodeInfo
                            .ACTION_SET_TEXT,
                        args
                    )
                ) {
                    return true
                }
            }

            for (
                index in
                0 until node.childCount
            ) {
                if (
                    walk(
                        node.getChild(index)
                    )
                ) {
                    return true
                }
            }

            return false
        }

        walk(root)
    }

    private fun looksLikeLogin(
        lower: String
    ): Boolean {
        val signals =
            listOf(
                "senha",
                "login",
                "entrar",
                "email",
                "e-mail"
            )

        return signals.count {
            lower.contains(it)
        } >= 2
    }

    private fun parseDateFromAt(
        at: String
    ): String? {
        val match =
            Regex(
                """^AT(\d{4})(\d{2})(\d{2})"""
            )
                .find(at)
                ?: return null

        val year =
            match.groupValues[1]
                .toIntOrNull()
                ?: return null

        val month =
            match.groupValues[2]
                .toIntOrNull()
                ?: return null

        val day =
            match.groupValues[3]
                .toIntOrNull()
                ?: return null

        return try {
            GregorianCalendar().apply {
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

            "%02d/%02d/%04d"
                .format(
                    day,
                    month,
                    year
                )
        } catch (
            _: Exception
        ) {
            null
        }
    }

    override fun onInterrupt() {
        Log.d(
            TAG,
            "SERVICO=INTERROMPIDO"
        )
    }
}
