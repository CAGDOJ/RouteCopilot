package com.routecopilot.spx

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.routecopilot.route.ImportedPackageCandidate
import java.util.Calendar
import java.util.GregorianCalendar

object SpxParser {

    private val atRegex =
        Regex(
            """\bAT[A-Z0-9]{8,}\b""",
            RegexOption.IGNORE_CASE
        )

    private val brRegex =
        Regex(
            """\bBR[A-Z0-9]{8,}\b""",
            RegexOption.IGNORE_CASE
        )

    private val phoneRegex =
        Regex(
            """(?:\+?55\s*)?(?:\(?\d{2}\)?\s*)?(?:9\d{4}|\d{4})[-\s]?\d{4}"""
        )

    fun collectTexts(
        node: AccessibilityNodeInfo?,
        output: MutableList<String>
    ) {
        if (node == null) return

        if (!node.isPassword) {
            node.text
                ?.toString()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let(
                    output::add
                )

            node.contentDescription
                ?.toString()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let(
                    output::add
                )
        }

        for (
            i in
            0 until node.childCount
        ) {
            collectTexts(
                node.getChild(i),
                output
            )
        }
    }

    fun normalizeScreen(
        texts: List<String>
    ): String =
        texts
            .joinToString(" ")
            .lowercase()

    fun isLoginScreen(
        screen: String
    ): Boolean {
        val strong =
            listOf(
                "esqueci minha senha",
                "fazer login",
                "iniciar sessão",
                "iniciar sessao",
                "código de verificação",
                "codigo de verificacao"
            )

        if (
            strong.any(
                screen::contains
            )
        ) {
            return true
        }

        val weak =
            listOf(
                "login",
                "senha",
                "e-mail",
                "email",
                "entrar"
            )

        return weak.count(
            screen::contains
        ) >= 2
    }

    fun isConsentScreen(
        screen: String
    ): Boolean {
        val context =
            listOf(
                "termos",
                "consentimento",
                "política de privacidade",
                "politica de privacidade"
            )
                .any(
                    screen::contains
                )

        val action =
            listOf(
                "aceitar",
                "aceito",
                "concordar",
                "continuar"
            )
                .any(
                    screen::contains
                )

        return context && action
    }

    fun isFaceCheckScreen(
        screen: String
    ): Boolean =
        listOf(
            "reconhecimento facial",
            "verificação facial",
            "verificacao facial",
            "validação facial",
            "validacao facial",
            "selfie",
            "olhe para a câmera",
            "olhe para a camera"
        )
            .any(
                screen::contains
            )

    fun findAt(
        texts: List<String>
    ): String? {
        texts.forEach { raw ->
            val normalized =
                raw
                    .replace(
                        " ",
                        ""
                    )
                    .uppercase()

            atRegex
                .find(
                    normalized
                )
                ?.let {
                    return it.value
                        .uppercase()
                }
        }

        return null
    }

    fun findBrCodes(
        texts: List<String>
    ): Set<String> {
        val result =
            linkedSetOf<String>()

        texts.forEach { raw ->
            val normalized =
                raw
                    .replace(
                        " ",
                        ""
                    )
                    .replace(
                        "\n",
                        ""
                    )
                    .uppercase()

            brRegex
                .findAll(
                    normalized
                )
                .forEach {
                    result +=
                        it.value
                            .uppercase()
                }
        }

        return result
    }

    fun findExpectedTotal(
        texts: List<String>
    ): Int? =
        findStatusCount(
            texts,
            listOf(
                "em rota"
            )
        )

    /*
     * Conta apenas números associados ao rótulo procurado.
     *
     * Exemplos que aceita:
     * Ocorrência (3)
     * Ocorrências: 3
     * 3 Ocorrências
     * Encerrados 18
     * Finalizados (18)
     * Em Rota 64/66 -> retorna 66
     */
    fun findStatusCount(
        texts: List<String>,
        labels: List<String>
    ): Int? {
        val corpus =
            texts
                .joinToString(
                    " "
                )

        for (
            label in
            labels
        ) {
            val escaped =
                Regex.escape(
                    label
                )

            val patterns =
                listOf(
                    Regex(
                        """$escaped\s*\(\s*(\d{1,4})\s*\)""",
                        RegexOption.IGNORE_CASE
                    ),
                    Regex(
                        """$escaped\s*[:\-]?\s*(\d{1,4})\s*/\s*(\d{1,4})""",
                        RegexOption.IGNORE_CASE
                    ),
                    Regex(
                        """$escaped\s*[:\-]?\s*(\d{1,4})""",
                        RegexOption.IGNORE_CASE
                    ),
                    Regex(
                        """(\d{1,4})\s+$escaped\b""",
                        RegexOption.IGNORE_CASE
                    )
                )

            for (
                regex in
                patterns
            ) {
                val match =
                    regex.find(
                        corpus
                    )
                        ?: continue

                val numbers =
                    match
                        .groupValues
                        .drop(
                            1
                        )
                        .mapNotNull {
                            it.toIntOrNull()
                        }

                val candidate =
                    if (
                        numbers.size >= 2
                    ) {
                        numbers.last()
                    } else {
                        numbers.firstOrNull()
                    }

                if (
                    candidate != null &&
                    candidate in 0..1000
                ) {
                    return candidate
                }
            }
        }

        return null
    }

    fun findOccurrenceCount(
        texts: List<String>
    ): Int? =
        findStatusCount(
            texts,
            listOf(
                "ocorrência",
                "ocorrências",
                "ocorrencia",
                "ocorrencias"
            )
        )

    fun findClosedCount(
        texts: List<String>
    ): Int? =
        findStatusCount(
            texts,
            listOf(
                "encerrado",
                "encerrados",
                "finalizado",
                "finalizados",
                "concluído",
                "concluídos",
                "concluido",
                "concluidos"
            )
        )

    fun findOccurrenceDescriptions(
        texts: List<String>
    ): List<String> {
        val result =
            linkedSetOf<String>()

        val explicit =
            Regex(
                """(?:motivo|descri[cç][aã]o|ocorr[eê]ncia)\s*[:\-]\s*(.{3,120})""",
                RegexOption.IGNORE_CASE
            )

        val reasonWords =
            listOf(
                "destinatário ausente",
                "destinatario ausente",
                "cliente ausente",
                "endereço não localizado",
                "endereco nao localizado",
                "endereço incorreto",
                "endereco incorreto",
                "recusado",
                "estabelecimento fechado",
                "sem acesso",
                "área de risco",
                "area de risco",
                "mudou-se",
                "não localizado",
                "nao localizado"
            )

        texts.forEach { text ->
            explicit
                .find(
                    text
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let(
                    result::add
                )

            if (
                reasonWords.any {
                    text.contains(
                        it,
                        ignoreCase = true
                    )
                } &&
                text.length in
                3..140
            ) {
                result +=
                    text.trim()
            }
        }

        return result.toList()
    }

    fun dateFromAt(
        at: String
    ): String? {
        val m =
            Regex(
                """^AT(\d{4})(\d{2})(\d{2})"""
            )
                .find(
                    at.uppercase()
                )
                ?: return null

        val year =
            m.groupValues[1]
                .toIntOrNull()
                ?: return null

        val month =
            m.groupValues[2]
                .toIntOrNull()
                ?: return null

        val day =
            m.groupValues[3]
                .toIntOrNull()
                ?: return null

        if (
            year !in
            2020..2100
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

    fun findPackageCandidates(
        root: AccessibilityNodeInfo?
    ): List<ImportedPackageCandidate> {
        if (
            root == null
        ) {
            return emptyList()
        }

        val nodes =
            mutableListOf<
                Pair<
                    AccessibilityNodeInfo,
                    String
                >
            >()

        findBrNodes(
            root,
            nodes
        )

        val result =
            LinkedHashMap<
                String,
                ImportedPackageCandidate
            >()

        nodes
            .forEachIndexed {
                index,
                pair ->

                val node =
                    pair.first

                val br =
                    pair.second

                val container =
                    findBestPackageContainer(
                        node,
                        br
                    )

                val texts =
                    mutableListOf<String>()

                collectTexts(
                    container
                        ?: node,

                    texts
                )

                val phone =
                    texts
                        .asSequence()
                        .mapNotNull {
                            phoneRegex
                                .find(
                                    it
                                )
                                ?.value
                        }
                        .firstOrNull()

                val address =
                    texts
                        .filterNot {
                            it.contains(
                                br,
                                ignoreCase = true
                            )
                        }
                        .firstOrNull(
                            ::looksLikeAddress
                        )

                val neighborhood =
                    findNeighborhood(
                        texts,
                        address
                    )

                val recipient =
                    texts
                        .asSequence()
                        .map(
                            String::trim
                        )
                        .firstOrNull {
                            it.isNotBlank() &&
                                !it.contains(
                                    br,
                                    ignoreCase = true
                                ) &&
                                !it.startsWith(
                                    "AT",
                                    ignoreCase = true
                                ) &&
                                !looksLikeAddress(
                                    it
                                ) &&
                                !looksLikeNeighborhoodLabel(
                                    it
                                ) &&
                                phoneRegex.find(
                                    it
                                ) == null &&
                                it.length in
                                3..60 &&
                                !isUiLabel(
                                    it
                                )
                        }

                val candidate =
                    ImportedPackageCandidate(
                        br =
                            br,

                        recipient =
                            recipient,

                        phone =
                            phone,

                        address =
                            address,

                        neighborhood =
                            neighborhood,

                        originalOrder =
                            index + 1
                    )

                val old =
                    result[
                        br
                    ]

                if (
                    old == null ||
                    richness(
                        candidate
                    ) >
                    richness(
                        old
                    )
                ) {
                    result[
                        br
                    ] =
                        candidate
                }
            }

        return result
            .values
            .toList()
    }

    private fun findNeighborhood(
        texts: List<String>,
        address: String?
    ): String? {
        val explicit =
            Regex(
                """bairro\s*[:\-]\s*(.+)""",
                RegexOption.IGNORE_CASE
            )

        texts.forEach { text ->
            explicit
                .find(
                    text
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                ?.trim()
                ?.takeIf {
                    it.length in
                    2..60
                }
                ?.let {
                    return it
                }
        }

        if (
            !address.isNullOrBlank()
        ) {
            val patterns =
                listOf(
                    Regex(
                        """\s-\s*([^,]+),\s*[^,]+(?:\s-\s*[A-Z]{2})?""",
                        RegexOption.IGNORE_CASE
                    ),
                    Regex(
                        """,\s*([^,]+),\s*[^,]+\s*-\s*[A-Z]{2}""",
                        RegexOption.IGNORE_CASE
                    )
                )

            patterns.forEach { regex ->
                regex
                    .find(
                        address
                    )
                    ?.groupValues
                    ?.getOrNull(
                        1
                    )
                    ?.trim()
                    ?.takeIf {
                        it.length in
                        2..60
                    }
                    ?.let {
                        return it
                    }
            }
        }

        return null
    }

    private fun looksLikeNeighborhoodLabel(
        value: String
    ): Boolean =
        value
            .trim()
            .startsWith(
                "bairro",
                ignoreCase = true
            )

    private fun findBestPackageContainer(
        original:
            AccessibilityNodeInfo,

        br:
            String
    ): AccessibilityNodeInfo? {
        var current:
            AccessibilityNodeInfo? =
            original

        var best:
            AccessibilityNodeInfo? =
            original

        repeat(
            7
        ) {
            val node =
                current
                    ?: return@repeat

            val texts =
                mutableListOf<String>()

            collectTexts(
                node,
                texts
            )

            val brs =
                findBrCodes(
                    texts
                )

            if (
                brs.size == 1 &&
                brs.contains(
                    br
                )
            ) {
                best =
                    node
            } else if (
                brs.size >
                1
            ) {
                return best
            }

            current =
                node.parent
        }

        return best
    }

    private fun findBrNodes(
        node:
            AccessibilityNodeInfo?,

        output:
            MutableList<
                Pair<
                    AccessibilityNodeInfo,
                    String
                >
            >
    ) {
        if (
            node == null
        ) {
            return
        }

        if (
            !node.isPassword
        ) {
            val values =
                listOfNotNull(
                    node.text
                        ?.toString(),

                    node.contentDescription
                        ?.toString()
                )

            values.forEach { value ->
                brRegex
                    .find(
                        value
                            .replace(
                                " ",
                                ""
                            )
                            .uppercase()
                    )
                    ?.let {
                        output +=
                            node to
                            it.value
                                .uppercase()
                    }
            }
        }

        for (
            i in
            0 until node.childCount
        ) {
            findBrNodes(
                node.getChild(
                    i
                ),
                output
            )
        }
    }

    private fun looksLikeAddress(
        value: String
    ): Boolean {
        val v =
            value.lowercase()

        val prefixes =
            listOf(
                "rua ",
                "r. ",
                "avenida ",
                "av. ",
                "travessa ",
                "tv. ",
                "passagem ",
                "rodovia ",
                "estrada ",
                "conjunto ",
                "alameda ",
                "residencial ",
                "vila ",
                "quadra ",
                "condomínio ",
                "condominio "
            )

        return prefixes.any(
            v::contains
        ) ||
            (
                value.any(
                    Char::isDigit
                ) &&
                    value.contains(
                        ","
                    ) &&
                    value.length >=
                    8
                )
    }

    private fun isUiLabel(
        value: String
    ): Boolean {
        val v =
            value.lowercase()

        return listOf(
            "entrega",
            "entregas",
            "em rota",
            "ocorrência",
            "ocorrencias",
            "ocorrencia",
            "encerrado",
            "encerrados",
            "finalizado",
            "finalizados",
            "escanear",
            "entregue",
            "pedido",
            "pacote",
            "telefone",
            "destinatário",
            "destinatario"
        )
            .any {
                v == it ||
                    v.startsWith(
                        "$it "
                    )
            }
    }

    private fun richness(
        c:
            ImportedPackageCandidate
    ): Int =
        listOf(
            c.recipient,
            c.phone,
            c.address,
            c.neighborhood
        )
            .count {
                !it.isNullOrBlank()
            }

    fun findClickableExact(
        root:
            AccessibilityNodeInfo?,

        text:
            String
    ): AccessibilityNodeInfo? {
        if (
            root == null
        ) {
            return null
        }

        if (
            !root.isPassword
        ) {
            val values =
                listOfNotNull(
                    root.text
                        ?.toString()
                        ?.trim(),

                    root.contentDescription
                        ?.toString()
                        ?.trim()
                )

            if (
                values.any {
                    it.equals(
                        text,
                        ignoreCase = true
                    )
                }
            ) {
                clickableSelfOrParent(
                    root
                )
                    ?.let {
                        return it
                    }
            }
        }

        for (
            i in
            0 until root.childCount
        ) {
            findClickableExact(
                root.getChild(
                    i
                ),
                text
            )
                ?.let {
                    return it
                }
        }

        return null
    }

    fun findClickableStartsWith(
        root:
            AccessibilityNodeInfo?,

        prefix:
            String
    ): AccessibilityNodeInfo? {
        if (
            root == null
        ) {
            return null
        }

        if (
            !root.isPassword
        ) {
            val values =
                listOfNotNull(
                    root.text
                        ?.toString()
                        ?.trim(),

                    root.contentDescription
                        ?.toString()
                        ?.trim()
                )

            if (
                values.any {
                    it.startsWith(
                        prefix,
                        ignoreCase = true
                    )
                }
            ) {
                clickableSelfOrParent(
                    root
                )
                    ?.let {
                        return it
                    }
            }
        }

        for (
            i in
            0 until root.childCount
        ) {
            findClickableStartsWith(
                root.getChild(
                    i
                ),
                prefix
            )
                ?.let {
                    return it
                }
        }

        return null
    }

    fun findClickableContaining(
        root:
            AccessibilityNodeInfo?,

        terms:
            List<String>
    ): AccessibilityNodeInfo? {
        if (
            root == null
        ) {
            return null
        }

        if (
            !root.isPassword
        ) {
            val values =
                listOfNotNull(
                    root.text
                        ?.toString()
                        ?.trim(),

                    root.contentDescription
                        ?.toString()
                        ?.trim()
                )

            if (
                values.any { value ->
                    terms.any {
                        value.contains(
                            it,
                            ignoreCase = true
                        )
                    }
                }
            ) {
                clickableSelfOrParent(
                    root
                )
                    ?.let {
                        return it
                    }
            }
        }

        for (
            i in
            0 until root.childCount
        ) {
            findClickableContaining(
                root.getChild(
                    i
                ),
                terms
            )
                ?.let {
                    return it
                }
        }

        return null
    }

    fun findLargestScrollable(
        root:
            AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        if (
            root == null
        ) {
            return null
        }

        val list =
            mutableListOf<
                AccessibilityNodeInfo
            >()

        collectScrollables(
            root,
            list
        )

        return list
            .maxByOrNull {
                val rect =
                    Rect()

                it.getBoundsInScreen(
                    rect
                )

                rect.height()
            }
    }

    private fun collectScrollables(
        node:
            AccessibilityNodeInfo?,

        output:
            MutableList<
                AccessibilityNodeInfo
            >
    ) {
        if (
            node == null
        ) {
            return
        }

        if (
            node.isScrollable
        ) {
            output +=
                node
        }

        for (
            i in
            0 until node.childCount
        ) {
            collectScrollables(
                node.getChild(
                    i
                ),
                output
            )
        }
    }

    private fun clickableSelfOrParent(
        original:
            AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var node:
            AccessibilityNodeInfo? =
            original

        repeat(
            7
        ) {
            if (
                node?.isClickable ==
                true
            ) {
                return node
            }

            node =
                node?.parent
        }

        return null
    }
}
