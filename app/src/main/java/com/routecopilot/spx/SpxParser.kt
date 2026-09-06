package com.routecopilot.spx

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar
import java.util.GregorianCalendar

object SpxParser {

    private val atRegex =
        Regex("""\bAT[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)

    private val brRegex =
        Regex("""\bBR[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)

    fun normalizeScreen(texts: List<String>): String {
        return texts
            .joinToString(" ")
            .lowercase()
    }

    fun isLoginScreen(screen: String): Boolean {
        val strong =
            listOf(
                "esqueci minha senha",
                "fazer login",
                "iniciar sessão",
                "iniciar sessao",
                "código de verificação",
                "codigo de verificacao"
            )

        if (strong.any(screen::contains)) {
            return true
        }

        val weak =
            listOf(
                "login",
                "senha",
                "e-mail",
                "email",
                "telefone",
                "entrar"
            )

        return weak.count(screen::contains) >= 2
    }

    fun isConsentScreen(screen: String): Boolean {
        val context =
            listOf(
                "termos",
                "termo de uso",
                "política de privacidade",
                "politica de privacidade",
                "consentimento",
                "concordo",
                "aceito"
            )

        val action =
            listOf(
                "aceitar",
                "aceito",
                "concordar",
                "concordo",
                "continuar"
            )

        return context.any(screen::contains) &&
            action.any(screen::contains)
    }

    fun isFaceCheckScreen(screen: String): Boolean {
        return listOf(
            "reconhecimento facial",
            "verificação facial",
            "verificacao facial",
            "validação facial",
            "validacao facial",
            "selfie",
            "olhe para a câmera",
            "olhe para a camera"
        ).any(screen::contains)
    }

    fun hasDeliveryTabs(screen: String): Boolean {
        return screen.contains("em rota") &&
            (
                screen.contains("ocorrência") ||
                    screen.contains("ocorrencia")
                ) &&
            (
                screen.contains("encerrado") ||
                    screen.contains("finalizado")
                )
    }

    fun findAt(texts: List<String>): String? {
        texts.forEach { raw ->
            val normalized =
                raw
                    .replace(" ", "")
                    .uppercase()

            atRegex.find(normalized)?.let {
                return it.value.uppercase()
            }
        }

        return null
    }

    fun findBrCodes(texts: List<String>): Set<String> {
        val result =
            linkedSetOf<String>()

        texts.forEach { raw ->
            val normalized =
                raw
                    .replace(" ", "")
                    .replace("\n", "")
                    .uppercase()

            brRegex.findAll(normalized).forEach {
                result.add(
                    it.value.uppercase()
                )
            }
        }

        return result
    }

    fun findInRouteTotal(
        texts: List<String>
    ): Int? {
        val regex =
            Regex(
                """em\s*rota\s*\(\s*(\d{1,4})\s*\)""",
                RegexOption.IGNORE_CASE
            )

        texts.forEach { text ->
            regex.find(text)?.let { match ->
                return match.groupValues[1]
                    .toIntOrNull()
            }
        }

        return null
    }

    fun findExpectedTotal(
        texts: List<String>
    ): Int? {
        findInRouteTotal(texts)?.let {
            return it
        }

        val fraction =
            Regex(
                """(?<!\d)(\d{1,4})\s*/\s*(\d{1,4})(?!\d)"""
            )

        var best: Int? =
            null

        texts.forEach { text ->
            fraction.findAll(text).forEach { match ->
                val current =
                    match.groupValues[1]
                        .toIntOrNull()

                val total =
                    match.groupValues[2]
                        .toIntOrNull()

                if (
                    current != null &&
                    total != null &&
                    current >= 0 &&
                    total in 1..1000 &&
                    current <= total
                ) {
                    if (
                        best == null ||
                        total > best!!
                    ) {
                        best = total
                    }
                }
            }
        }

        return best
    }

    fun dateFromAt(
        at: String
    ): String? {
        val result =
            Regex(
                """^AT(\d{4})(\d{2})(\d{2})"""
            ).find(
                at.uppercase()
            ) ?: return null

        val year =
            result.groupValues[1]
                .toIntOrNull()
                ?: return null

        val month =
            result.groupValues[2]
                .toIntOrNull()
                ?: return null

        val day =
            result.groupValues[3]
                .toIntOrNull()
                ?: return null

        if (year !in 2020..2100) {
            return null
        }

        try {
            GregorianCalendar().apply {
                isLenient = false
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
        } catch (_: Exception) {
            return null
        }

        return String.format(
            "%02d/%02d/%04d",
            day,
            month,
            year
        )
    }

    fun collectTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<String>
    ) {
        if (node == null) {
            return
        }

        if (!node.isPassword) {
            node.text
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(output::add)

            node.contentDescription
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(output::add)
        }

        for (
            i in 0 until node.childCount
        ) {
            collectTexts(
                node.getChild(i),
                output
            )
        }
    }

    fun findDownloadButton(
        root: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        if (root == null) {
            return null
        }

        if (!root.isPassword) {
            val text =
                root.text
                    ?.toString()
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()

            val desc =
                root.contentDescription
                    ?.toString()
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()

            val id =
                root.viewIdResourceName
                    ?.lowercase()
                    .orEmpty()

            val strongMatch =
                listOf(
                    "download",
                    "baixar",
                    "download_route",
                    "route_download",
                    "downloadroute",
                    "offline_route",
                    "sync_route"
                ).any {
                    text.contains(it) ||
                        desc.contains(it) ||
                        id.contains(it)
                }

            if (strongMatch) {
                clickableSelfOrParent(root)?.let {
                    return it
                }
            }
        }

        for (
            i in 0 until root.childCount
        ) {
            findDownloadButton(
                root.getChild(i)
            )?.let {
                return it
            }
        }

        return null
    }

    fun findTopClickableExactText(
        root: AccessibilityNodeInfo?,
        expected: String,
        screenHeight: Int
    ): AccessibilityNodeInfo? {
        if (root == null) {
            return null
        }

        if (!root.isPassword) {
            val text =
                root.text
                    ?.toString()
                    ?.trim()

            val desc =
                root.contentDescription
                    ?.toString()
                    ?.trim()

            if (
                text.equals(
                    expected,
                    ignoreCase = true
                ) ||
                desc.equals(
                    expected,
                    ignoreCase = true
                )
            ) {
                val rect =
                    Rect()

                root.getBoundsInScreen(rect)

                if (
                    rect.centerY() <=
                    (screenHeight * 0.40f)
                ) {
                    clickableSelfOrParent(root)?.let {
                        return it
                    }
                }
            }
        }

        for (
            i in 0 until root.childCount
        ) {
            findTopClickableExactText(
                root.getChild(i),
                expected,
                screenHeight
            )?.let {
                return it
            }
        }

        return null
    }

    fun findClickableStartsWith(
        root: AccessibilityNodeInfo?,
        expectedPrefix: String
    ): AccessibilityNodeInfo? {
        if (root == null) {
            return null
        }

        if (!root.isPassword) {
            val text =
                root.text
                    ?.toString()
                    ?.trim()

            val desc =
                root.contentDescription
                    ?.toString()
                    ?.trim()

            val matches =
                text?.startsWith(
                    expectedPrefix,
                    ignoreCase = true
                ) == true ||
                    desc?.startsWith(
                        expectedPrefix,
                        ignoreCase = true
                    ) == true

            if (matches) {
                clickableSelfOrParent(root)?.let {
                    return it
                }
            }
        }

        for (
            i in 0 until root.childCount
        ) {
            findClickableStartsWith(
                root.getChild(i),
                expectedPrefix
            )?.let {
                return it
            }
        }

        return null
    }

    fun logSafeClickableCandidates(
        root: AccessibilityNodeInfo?,
        emit: (String) -> Unit
    ) {
        if (root == null) {
            return
        }

        if (
            root.isClickable &&
            !root.isPassword
        ) {
            val text =
                root.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val desc =
                root.contentDescription
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            val id =
                root.viewIdResourceName
                    .orEmpty()

            val safeText =
                sanitizeForLog(text)

            val safeDesc =
                sanitizeForLog(desc)

            if (
                safeText.isNotBlank() ||
                safeDesc.isNotBlank() ||
                id.isNotBlank()
            ) {
                emit(
                    "CLICKABLE text='$safeText' desc='$safeDesc' id='$id'"
                )
            }
        }

        for (
            i in 0 until root.childCount
        ) {
            logSafeClickableCandidates(
                root.getChild(i),
                emit
            )
        }
    }

    private fun sanitizeForLog(
        value: String
    ): String {
        if (value.isBlank()) {
            return ""
        }

        val upper =
            value.uppercase()

        if (
            upper.contains("BR") ||
            upper.contains("AT") ||
            value.any { it.isDigit() } &&
            value.length > 8
        ) {
            return "[oculto]"
        }

        return value
            .take(60)
    }

    private fun clickableSelfOrParent(
        original: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var node:
            AccessibilityNodeInfo? =
            original

        var depth =
            0

        while (
            node != null &&
            depth < 7
        ) {
            if (node.isClickable) {
                return node
            }

            node =
                node.parent

            depth++
        }

        return null
    }
}
