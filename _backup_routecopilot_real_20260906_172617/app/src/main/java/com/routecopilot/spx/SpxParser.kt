package com.routecopilot.spx

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.routecopilot.route.ImportedPackageCandidate
import java.util.Calendar
import java.util.GregorianCalendar

object SpxParser {

    private val atRegex =
        Regex("""\bAT[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)

    private val brRegex =
        Regex("""\bBR[A-Z0-9]{8,}\b""", RegexOption.IGNORE_CASE)

    private val phoneRegex =
        Regex("""(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?(?:9\d{4}|\d{4})[-\s]?\d{4}""")

    fun collectTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<String>
    ) {
        if (node == null) return
        if (!node.isPassword) {
            node.text?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(output::add)
            node.contentDescription?.toString()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(output::add)
        }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), output)
        }
    }

    fun normalizeScreen(texts: List<String>): String =
        texts.joinToString(" ").lowercase()

    fun isLoginScreen(screen: String): Boolean {
        val strong = listOf(
            "esqueci minha senha",
            "fazer login",
            "iniciar sessão",
            "iniciar sessao",
            "código de verificação",
            "codigo de verificacao"
        )
        if (strong.any(screen::contains)) return true
        val weak = listOf("login", "senha", "e-mail", "email", "entrar")
        return weak.count(screen::contains) >= 2
    }

    fun isConsentScreen(screen: String): Boolean =
        listOf("termos", "consentimento", "política de privacidade", "politica de privacidade")
            .any(screen::contains) &&
        listOf("aceitar", "aceito", "concordar", "continuar").any(screen::contains)

    fun isFaceCheckScreen(screen: String): Boolean =
        listOf(
            "reconhecimento facial",
            "verificação facial",
            "verificacao facial",
            "validação facial",
            "validacao facial",
            "selfie",
            "olhe para a câmera",
            "olhe para a camera"
        ).any(screen::contains)

    fun findAt(texts: List<String>): String? {
        texts.forEach { raw ->
            val normalized = raw.replace(" ", "").uppercase()
            atRegex.find(normalized)?.let { return it.value.uppercase() }
        }
        return null
    }

    fun findBrCodes(texts: List<String>): Set<String> {
        val result = linkedSetOf<String>()
        texts.forEach { raw ->
            val normalized = raw.replace(" ", "").replace("\n", "").uppercase()
            brRegex.findAll(normalized).forEach {
                result += it.value.uppercase()
            }
        }
        return result
    }

    fun findExpectedTotal(texts: List<String>): Int? {
        // Só aceita total explicitamente ligado a "Em Rota".
        val corpus = texts.joinToString(" ")
        val patterns = listOf(
            Regex("""em\s*rota\s*\(\s*(\d{1,4})\s*\)""", RegexOption.IGNORE_CASE),
            Regex("""em\s*rota\s*[:\-]?\s*(\d{1,4})""", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { regex ->
            regex.find(corpus)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                if (it in 1..1000) return it
            }
        }
        return null
    }

    fun dateFromAt(at: String): String? {
        val m = Regex("""^AT(\d{4})(\d{2})(\d{2})""").find(at.uppercase()) ?: return null
        val year = m.groupValues[1].toIntOrNull() ?: return null
        val month = m.groupValues[2].toIntOrNull() ?: return null
        val day = m.groupValues[3].toIntOrNull() ?: return null
        if (year !in 2020..2100) return null
        try {
            GregorianCalendar().apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                time
            }
        } catch (_: Exception) {
            return null
        }
        return String.format("%02d/%02d/%04d", day, month, year)
    }

    fun findPackageCandidates(
        root: AccessibilityNodeInfo?
    ): List<ImportedPackageCandidate> {
        if (root == null) return emptyList()

        val brNodes = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
        findBrNodes(root, brNodes)

        val result = LinkedHashMap<String, ImportedPackageCandidate>()

        brNodes.forEachIndexed { index, (node, br) ->
            var container: AccessibilityNodeInfo? = node
            repeat(4) {
                container = container?.parent ?: container
            }

            val texts = mutableListOf<String>()
            collectTexts(container ?: node, texts)

            val phone = texts.asSequence()
                .mapNotNull { phoneRegex.find(it)?.value }
                .firstOrNull()

            val address = texts
                .filterNot { it.contains(br, ignoreCase = true) }
                .firstOrNull(::looksLikeAddress)

            val recipient = texts
                .map(String::trim)
                .firstOrNull {
                    it.isNotBlank() &&
                    !it.contains(br, ignoreCase = true) &&
                    !it.startsWith("AT", ignoreCase = true) &&
                    !looksLikeAddress(it) &&
                    phoneRegex.find(it) == null &&
                    it.length in 3..60 &&
                    !isUiLabel(it)
                }

            val candidate = ImportedPackageCandidate(
                br = br,
                recipient = recipient,
                phone = phone,
                address = address,
                originalOrder = index + 1
            )

            val old = result[br]
            if (old == null || richness(candidate) > richness(old)) {
                result[br] = candidate
            }
        }

        return result.values.toList()
    }

    private fun findBrNodes(
        node: AccessibilityNodeInfo?,
        output: MutableList<Pair<AccessibilityNodeInfo, String>>
    ) {
        if (node == null) return
        if (!node.isPassword) {
            val values = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString()
            )
            values.forEach { value ->
                brRegex.find(value.replace(" ", "").uppercase())?.let {
                    output += node to it.value.uppercase()
                }
            }
        }
        for (i in 0 until node.childCount) {
            findBrNodes(node.getChild(i), output)
        }
    }

    private fun looksLikeAddress(value: String): Boolean {
        val v = value.lowercase()
        val prefixes = listOf(
            "rua ", "r. ", "avenida ", "av. ", "travessa ", "tv. ",
            "passagem ", "rodovia ", "estrada ", "conjunto ", "alameda ",
            "residencial ", "vila ", "quadra "
        )
        return prefixes.any(v::contains) ||
            (value.any(Char::isDigit) && value.contains(",") && value.length >= 8)
    }

    private fun isUiLabel(value: String): Boolean {
        val v = value.lowercase()
        return listOf(
            "entrega", "entregas", "em rota", "ocorrência", "ocorrencia",
            "encerrado", "finalizado", "escanear", "entregue", "pedido",
            "pacote", "telefone", "destinatário", "destinatario"
        ).any { v == it || v.startsWith("$it ") }
    }

    private fun richness(c: ImportedPackageCandidate): Int =
        listOf(c.recipient, c.phone, c.address).count { !it.isNullOrBlank() }

    fun findClickableExact(
        root: AccessibilityNodeInfo?,
        text: String
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (!root.isPassword) {
            val values = listOfNotNull(
                root.text?.toString()?.trim(),
                root.contentDescription?.toString()?.trim()
            )
            if (values.any { it.equals(text, ignoreCase = true) }) {
                clickableSelfOrParent(root)?.let { return it }
            }
        }
        for (i in 0 until root.childCount) {
            findClickableExact(root.getChild(i), text)?.let { return it }
        }
        return null
    }

    fun findClickableStartsWith(
        root: AccessibilityNodeInfo?,
        prefix: String
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (!root.isPassword) {
            val values = listOfNotNull(
                root.text?.toString()?.trim(),
                root.contentDescription?.toString()?.trim()
            )
            if (values.any { it.startsWith(prefix, ignoreCase = true) }) {
                clickableSelfOrParent(root)?.let { return it }
            }
        }
        for (i in 0 until root.childCount) {
            findClickableStartsWith(root.getChild(i), prefix)?.let { return it }
        }
        return null
    }

    fun findLargestScrollable(
        root: AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val list = mutableListOf<AccessibilityNodeInfo>()
        collectScrollables(root, list)
        return list.maxByOrNull {
            val rect = Rect()
            it.getBoundsInScreen(rect)
            rect.height()
        }
    }

    private fun collectScrollables(
        node: AccessibilityNodeInfo?,
        output: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        if (node.isScrollable) output += node
        for (i in 0 until node.childCount) {
            collectScrollables(node.getChild(i), output)
        }
    }

    private fun clickableSelfOrParent(
        original: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = original
        repeat(7) {
            if (node?.isClickable == true) return node
            node = node?.parent
        }
        return null
    }
}
