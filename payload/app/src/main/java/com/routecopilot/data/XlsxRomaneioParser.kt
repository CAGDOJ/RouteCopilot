package com.routecopilot.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XlsxRomaneioParser {

    private val requiredHeaders = setOf(
        "at id",
        "sequence",
        "stop",
        "spx tn",
        "destination address",
        "bairro",
        "city",
        "zipcode/postal code"
    )

    private val recipientAliases = listOf(
        "recipient name",
        "recipient",
        "consignee",
        "customer name",
        "buyer name",
        "nome do destinatario",
        "nome destinatario",
        "destinatario",
        "nome"
    )

    private val phoneAliases = listOf(
        "recipient phone",
        "phone",
        "mobile",
        "contact number",
        "telefone do destinatario",
        "telefone destinatario",
        "telefone",
        "contato"
    )

    fun parseRoutes(
        bytes: ByteArray,
        sourceFileName: String,
        sourceLastModified: Long
    ): List<RomaneioRoute> {
        val entries = unzipRelevantEntries(bytes)
        val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheetPath = findFirstWorksheetPath(entries)
        val sheetBytes = entries[sheetPath]
            ?: error("A primeira planilha do XLSX não foi encontrada.")

        val rows = parseSheetRows(sheetBytes, sharedStrings)
        if (rows.isEmpty()) error("O romaneio está vazio.")

        val headerRowIndex = rows.indexOfFirst { row ->
            row.values
                .map(::normalizeHeader)
                .count { it in requiredHeaders } >= 4
        }
        if (headerRowIndex < 0) {
            error("Não encontrei o cabeçalho esperado do romaneio.")
        }

        val headerMap = rows[headerRowIndex].entries.associate { (column, value) ->
            normalizeHeader(value) to column
        }

        fun col(name: String): Int? = headerMap[normalizeHeader(name)]
        fun aliasCol(aliases: List<String>): Int? = aliases.firstNotNullOfOrNull { alias ->
            headerMap[normalizeHeader(alias)]
        }

        val atCol = col("AT ID") ?: error("Coluna 'AT ID' não encontrada.")
        val sequenceCol = col("Sequence")
        val stopCol = col("Stop")
        val spxCol = col("SPX TN") ?: error("Coluna 'SPX TN' não encontrada.")
        val addressCol = col("Destination Address")
            ?: error("Coluna 'Destination Address' não encontrada.")
        val bairroCol = col("Bairro")
        val cityCol = col("City")
        val zipcodeCol = col("Zipcode/Postal code")
        val recipientCol = aliasCol(recipientAliases)
        val phoneCol = aliasCol(phoneAliases)

        val byAt = linkedMapOf<String, LinkedHashMap<String, RomaneioPackage>>()

        rows
            .drop(headerRowIndex + 1)
            .forEachIndexed { offset, row ->
                val sourceRow = headerRowIndex + 2 + offset

                val spxTn = row[spxCol]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    .orEmpty()

                if (spxTn.isBlank() || !spxTn.startsWith("BR")) {
                    return@forEachIndexed
                }

                val atId = row[atCol]
                    ?.trim()
                    ?.uppercase(Locale.ROOT)
                    .orEmpty()

                if (atId.isBlank() || !atId.startsWith("AT")) {
                    return@forEachIndexed
                }

                // Importante: nome e telefone só vêm de colunas próprias.
                // Nunca tentamos extrair telefone da linha inteira, evitando
                // misturar data/AT/endereço no número enviado ao WhatsApp.
                val recipientName = recipientCol
                    ?.let { row[it] }
                    ?.trim()
                    .orEmpty()

                val phone = phoneCol
                    ?.let { row[it] }
                    ?.let(::normalizePhoneCell)
                    .orEmpty()

                val item = RomaneioPackage(
                    atId = atId,
                    sequence = parseInt(sequenceCol?.let(row::get)),
                    stop = parseInt(stopCol?.let(row::get)),
                    spxTn = spxTn,
                    recipientName = recipientName,
                    phone = phone,
                    destinationAddress = row[addressCol]?.trim().orEmpty(),
                    bairro = bairroCol?.let(row::get)?.trim().orEmpty(),
                    city = cityCol?.let(row::get)?.trim().orEmpty(),
                    zipcode = zipcodeCol?.let(row::get)?.trim().orEmpty(),
                    sourceRow = sourceRow
                )

                val dedup = byAt.getOrPut(atId) { linkedMapOf() }
                dedup[spxTn] = item
            }

        if (byAt.isEmpty()) {
            error("Nenhum pedido BR foi encontrado no romaneio.")
        }

        return byAt.map { (atId, packageMap) ->
            RomaneioRoute(
                atId = atId,
                loadDate = parseDateFromAt(atId),
                sourceFileName = sourceFileName,
                sourceLastModified = sourceLastModified,
                packages = packageMap.values.toList()
            )
        }
    }

    private fun normalizePhoneCell(value: String): String {
        val digits = value.filter(Char::isDigit)

        // Evita aceitar datas, IDs ou outros números curtos como telefone.
        if (digits.length !in 10..13) return ""

        return when {
            digits.startsWith("55") && digits.length in 12..13 -> "+$digits"
            digits.length in 10..11 -> "+55$digits"
            else -> ""
        }
    }

    private fun unzipRelevantEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val relevant =
                    name == "xl/sharedStrings.xml" ||
                        name == "xl/workbook.xml" ||
                        name == "xl/_rels/workbook.xml.rels" ||
                        (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))

                if (relevant) entries[name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun findFirstWorksheetPath(entries: Map<String, ByteArray>): String {
        val workbookBytes = entries["xl/workbook.xml"]
        val relsBytes = entries["xl/_rels/workbook.xml.rels"]

        if (workbookBytes != null && relsBytes != null) {
            runCatching {
                val workbook = parseXml(workbookBytes)
                val sheets = workbook.getElementsByTagName("sheet")
                if (sheets.length > 0) {
                    val firstSheet = sheets.item(0) as Element
                    val relationId = firstSheet.getAttributeNS(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id"
                    ).ifBlank { firstSheet.getAttribute("r:id") }

                    if (relationId.isNotBlank()) {
                        val rels = parseXml(relsBytes)
                        val relations = rels.getElementsByTagName("Relationship")
                        for (i in 0 until relations.length) {
                            val relation = relations.item(i) as Element
                            if (relation.getAttribute("Id") == relationId) {
                                val target = relation.getAttribute("Target")
                                if (target.isNotBlank()) {
                                    val normalized = if (target.startsWith("/")) {
                                        target.removePrefix("/")
                                    } else {
                                        "xl/$target"
                                    }
                                    if (entries.containsKey(normalized)) return normalized
                                }
                            }
                        }
                    }
                }
            }
        }

        return entries.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .sorted()
            .firstOrNull()
            ?: error("Nenhuma planilha foi encontrada no XLSX.")
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val document = parseXml(bytes)
        val nodes = document.getElementsByTagName("si")
        val result = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            val builder = StringBuilder()
            collectTextNodes(nodes.item(i), builder)
            result += builder.toString()
        }
        return result
    }

    private fun collectTextNodes(node: Node, builder: StringBuilder) {
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.endsWith("t")) {
            builder.append(node.textContent ?: "")
            return
        }
        val children = node.childNodes
        for (i in 0 until children.length) collectTextNodes(children.item(i), builder)
    }

    private fun parseSheetRows(
        bytes: ByteArray,
        sharedStrings: List<String>
    ): List<Map<Int, String>> {
        val document = parseXml(bytes)
        val rowNodes = document.getElementsByTagName("row")
        val rows = mutableListOf<Map<Int, String>>()

        for (i in 0 until rowNodes.length) {
            val rowNode = rowNodes.item(i) as Element
            val cellNodes = rowNode.getElementsByTagName("c")
            val row = linkedMapOf<Int, String>()
            for (j in 0 until cellNodes.length) {
                val cell = cellNodes.item(j) as Element
                val column = columnIndexFromReference(cell.getAttribute("r"))
                if (column >= 0) row[column] = readCellValue(cell, sharedStrings)
            }
            rows += row
        }
        return rows
    }

    private fun readCellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val textNodes = cell.getElementsByTagName("t")
            val builder = StringBuilder()
            for (i in 0 until textNodes.length) {
                builder.append(textNodes.item(i).textContent ?: "")
            }
            return builder.toString()
        }

        val valueNodes = cell.getElementsByTagName("v")
        val raw = if (valueNodes.length > 0) valueNodes.item(0).textContent ?: "" else ""

        return when (type) {
            "s" -> raw.toIntOrNull()
                ?.takeIf { it in sharedStrings.indices }
                ?.let(sharedStrings::get)
                .orEmpty()
            "b" -> if (raw == "1") "TRUE" else "FALSE"
            else -> raw
        }
    }

    private fun columnIndexFromReference(reference: String): Int {
        if (reference.isBlank()) return -1
        var result = 0
        var hasLetter = false
        for (char in reference) {
            if (!char.isLetter()) break
            hasLetter = true
            result = result * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return if (hasLetter) result - 1 else -1
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    private fun normalizeHeader(value: String): String {
        val noAccent = Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace("\\p{Mn}+".toRegex(), "")

        return noAccent
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun parseInt(value: String?): Int? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed == "-") return null
        return trimmed.toIntOrNull()
            ?: trimmed.toDoubleOrNull()
                ?.takeIf { it % 1.0 == 0.0 }
                ?.toInt()
    }

    fun parseDateFromAt(atId: String): String? {
        val match = Regex("^AT(\\d{4})(\\d{2})(\\d{2})")
            .find(atId.uppercase(Locale.ROOT))
            ?: return null

        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null

        return runCatching {
            GregorianCalendar().apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                time
            }
            String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year)
        }.getOrNull()
    }
}
