package com.routecopilot.spx

import android.content.Context
import android.net.Uri
import com.routecopilot.model.DeliveryItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream

data class ParsedSpxExcel(
    val deliveries: List<DeliveryItem>,
    val sheetsRead: Int
)

object SpxExcelParser {

    private val trackingRegex =
        Regex(
            """\bBR[A-Z0-9]{8,}\b""",
            RegexOption.IGNORE_CASE
        )

    fun parse(
        context: Context,
        uri: Uri
    ): ParsedSpxExcel {

        val input =
            context
                .contentResolver
                .openInputStream(
                    uri
                )
                ?: error(
                    "Não foi possível abrir a planilha do SPX."
                )

        val entries =
            mutableMapOf<String, ByteArray>()

        input.use { source ->

            ZipInputStream(
                source
            ).use { zip ->

                var entry =
                    zip.nextEntry

                while (
                    entry != null
                ) {

                    if (
                        !entry.isDirectory
                    ) {

                        val name =
                            entry.name

                        if (
                            name ==
                            "xl/sharedStrings.xml" ||
                            (
                                name.startsWith(
                                    "xl/worksheets/sheet"
                                ) &&
                                    name.endsWith(
                                        ".xml"
                                    )
                                )
                        ) {

                            entries[name] =
                                zip.readBytes()
                        }
                    }

                    zip.closeEntry()

                    entry =
                        zip.nextEntry
                }
            }
        }

        val sharedStrings =
            entries[
                "xl/sharedStrings.xml"
            ]?.let {

                parseSharedStrings(
                    it
                )

            } ?: emptyList()

        val worksheets =
            entries
                .keys
                .filter {

                    it.startsWith(
                        "xl/worksheets/sheet"
                    ) &&
                        it.endsWith(
                            ".xml"
                        )
                }
                .sortedWith(
                    compareBy {
                        extractSheetNumber(it)
                    }
                )

        if (
            worksheets.isEmpty()
        ) {

            error(
                "Nenhuma planilha interna foi encontrada no XLSX."
            )
        }

        val deliveries =
            mutableListOf<DeliveryItem>()

        val seenTracking =
            linkedSetOf<String>()

        var sheetsRead =
            0

        worksheets.forEach { sheetName ->

            val xml =
                entries[
                    sheetName
                ]
                    ?: return@forEach

            val rows =
                parseWorksheet(
                    xml,
                    sharedStrings
                )

            if (
                rows.isEmpty()
            ) {

                return@forEach
            }

            sheetsRead++

            val headerIndex =
                findBestHeaderRow(
                    rows
                )

            val headers =
                rows[
                    headerIndex
                ].map {

                    it.trim()
                }

            for (
                rowIndex in
                headerIndex + 1
                    until rows.size
            ) {

                val row =
                    rows[
                        rowIndex
                    ]

                if (
                    row.all {
                        it.isBlank()
                    }
                ) {

                    continue
                }

                val tracking =
                    findTracking(
                        row
                    )
                        ?: continue

                if (
                    !seenTracking.add(
                        tracking
                    )
                ) {

                    continue
                }

                val raw =
                    linkedMapOf<String, String>()

                headers.forEachIndexed {
                        index,
                        header ->

                    if (
                        header.isBlank() ||
                        index >= row.size
                    ) {

                        return@forEachIndexed
                    }

                    val value =
                        row[
                            index
                        ].trim()

                    if (
                        value.isNotBlank()
                    ) {

                        raw[
                            header
                        ] =
                            value
                    }
                }

                deliveries.add(
                    DeliveryItem(
                        tracking =
                            tracking,

                        stop =
                            findByHeader(
                                headers,
                                row,
                                listOf(
                                    "parada",
                                    "stop",
                                    "sequencia",
                                    "sequência",
                                    "ordem",
                                    "numero da parada",
                                    "número da parada"
                                )
                            ),

                        address =
                            findByHeader(
                                headers,
                                row,
                                listOf(
                                    "endereco",
                                    "endereço",
                                    "address",
                                    "logradouro",
                                    "rua",
                                    "endereco de entrega",
                                    "endereço de entrega"
                                )
                            ),

                        neighborhood =
                            findByHeader(
                                headers,
                                row,
                                listOf(
                                    "bairro",
                                    "neighborhood",
                                    "bairro de entrega",
                                    "bairro destino",
                                    "distrito"
                                )
                            ),

                        recipient =
                            findByHeader(
                                headers,
                                row,
                                listOf(
                                    "destinatario",
                                    "destinatário",
                                    "cliente",
                                    "recipient",
                                    "recebedor",
                                    "nome do cliente"
                                )
                            ),

                        phone =
                            findByHeader(
                                headers,
                                row,
                                listOf(
                                    "telefone",
                                    "celular",
                                    "phone",
                                    "contato",
                                    "telefone do cliente",
                                    "celular do cliente"
                                )
                            ),

                        rawColumns =
                            raw
                    )
                )
            }
        }

        if (
            deliveries.isEmpty()
        ) {

            error(
                "Nenhum código BR foi encontrado nas planilhas do SPX."
            )
        }

        return ParsedSpxExcel(
            deliveries =
                deliveries,

            sheetsRead =
                sheetsRead
        )
    }

    private fun extractSheetNumber(
        path: String
    ): Int {

        return Regex(
            """sheet(\d+)\.xml"""
        )
            .find(
                path
            )
            ?.groupValues
            ?.getOrNull(
                1
            )
            ?.toIntOrNull()
            ?: Int.MAX_VALUE
    }

    private fun parseSharedStrings(
        xml: ByteArray
    ): List<String> {

        val factory =
            XmlPullParserFactory
                .newInstance()

        factory.isNamespaceAware =
            false

        val parser =
            factory
                .newPullParser()

        parser.setInput(
            ByteArrayInputStream(
                xml
            ),
            "UTF-8"
        )

        val result =
            mutableListOf<String>()

        var insideSi =
            false

        var buffer =
            StringBuilder()

        var event =
            parser.eventType

        while (
            event !=
            XmlPullParser.END_DOCUMENT
        ) {

            when (
                event
            ) {

                XmlPullParser.START_TAG -> {

                    when (
                        parser.name
                    ) {

                        "si" -> {

                            insideSi =
                                true

                            buffer =
                                StringBuilder()
                        }

                        "t" -> {

                            if (
                                insideSi
                            ) {

                                buffer.append(
                                    parser.nextText()
                                )
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {

                    if (
                        parser.name ==
                        "si"
                    ) {

                        result.add(
                            buffer
                                .toString()
                        )

                        insideSi =
                            false
                    }
                }
            }

            event =
                parser.next()
        }

        return result
    }

    private fun parseWorksheet(
        xml: ByteArray,
        sharedStrings: List<String>
    ): List<List<String>> {

        val factory =
            XmlPullParserFactory
                .newInstance()

        factory.isNamespaceAware =
            false

        val parser =
            factory
                .newPullParser()

        parser.setInput(
            ByteArrayInputStream(
                xml
            ),
            "UTF-8"
        )

        val rows =
            mutableListOf<List<String>>()

        var currentRow:
            MutableMap<Int, String>? =
            null

        var currentColumn =
            -1

        var currentType =
            ""

        var currentValue =
            ""

        var event =
            parser.eventType

        while (
            event !=
            XmlPullParser.END_DOCUMENT
        ) {

            when (
                event
            ) {

                XmlPullParser.START_TAG -> {

                    when (
                        parser.name
                    ) {

                        "row" -> {

                            currentRow =
                                linkedMapOf()
                        }

                        "c" -> {

                            val reference =
                                parser
                                    .getAttributeValue(
                                        null,
                                        "r"
                                    )
                                    ?: ""

                            currentColumn =
                                columnFromCellReference(
                                    reference
                                )

                            currentType =
                                parser
                                    .getAttributeValue(
                                        null,
                                        "t"
                                    )
                                    ?: ""

                            currentValue =
                                ""
                        }

                        "v" -> {

                            val raw =
                                parser
                                    .nextText()

                            currentValue =
                                decodeCellValue(
                                    raw,
                                    currentType,
                                    sharedStrings
                                )
                        }

                        "t" -> {

                            if (
                                currentType ==
                                "inlineStr"
                            ) {

                                currentValue =
                                    parser
                                        .nextText()
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {

                    when (
                        parser.name
                    ) {

                        "c" -> {

                            if (
                                currentRow != null &&
                                currentColumn >= 0
                            ) {

                                currentRow[
                                    currentColumn
                                ] =
                                    currentValue
                            }

                            currentColumn =
                                -1

                            currentValue =
                                ""
                        }

                        "row" -> {

                            val sourceRow =
                                currentRow

                            if (
                                sourceRow != null
                            ) {

                                val maxColumn =
                                    sourceRow
                                        .keys
                                        .maxOrNull()
                                        ?: -1

                                val values =
                                    if (
                                        maxColumn >= 0
                                    ) {

                                        MutableList(
                                            maxColumn + 1
                                        ) {
                                            ""
                                        }

                                    } else {

                                        mutableListOf()
                                    }

                                sourceRow.forEach {
                                        (column,
                                         value) ->

                                    if (
                                        column in
                                        values.indices
                                    ) {

                                        values[
                                            column
                                        ] =
                                            value
                                    }
                                }

                                rows.add(
                                    values
                                )
                            }

                            currentRow =
                                null
                        }
                    }
                }
            }

            event =
                parser.next()
        }

        return rows
    }

    private fun decodeCellValue(
        raw: String,
        type: String,
        sharedStrings: List<String>
    ): String {

        return when (
            type
        ) {

            "s" -> {

                val index =
                    raw
                        .toIntOrNull()

                if (
                    index != null &&
                    index in sharedStrings.indices
                ) {

                    sharedStrings[
                        index
                    ]

                } else {

                    raw
                }
            }

            else ->
                raw
        }
    }

    private fun columnFromCellReference(
        reference: String
    ): Int {

        val letters =
            reference
                .takeWhile {
                    it.isLetter()
                }
                .uppercase()

        if (
            letters.isBlank()
        ) {

            return -1
        }

        var result =
            0

        letters.forEach {

            result =
                result * 26 +
                    (
                        it.code -
                            'A'.code +
                            1
                        )
        }

        return result - 1
    }

    private fun findBestHeaderRow(
        rows: List<List<String>>
    ): Int {

        val limit =
            minOf(
                rows.size,
                25
            )

        val keywords =
            listOf(
                "bairro",
                "endereco",
                "endereço",
                "destinatario",
                "destinatário",
                "telefone",
                "celular",
                "pedido",
                "tracking",
                "rastreio",
                "parada",
                "logradouro"
            )

        var bestIndex =
            0

        var bestScore =
            Int.MIN_VALUE

        for (
            index in
            0 until limit
        ) {

            val text =
                rows[
                    index
                ]
                    .joinToString(
                        " "
                    ) {
                        normalize(it)
                    }

            var score =
                0

            keywords.forEach {

                if (
                    text.contains(
                        normalize(it)
                    )
                ) {

                    score++
                }
            }

            if (
                score >
                bestScore
            ) {

                bestScore =
                    score

                bestIndex =
                    index
            }
        }

        return bestIndex
    }

    private fun findTracking(
        row: List<String>
    ): String? {

        row.forEach { value ->

            trackingRegex
                .find(
                    value.uppercase()
                )
                ?.let {

                    return it
                        .value
                        .uppercase()
                }
        }

        return null
    }

    private fun findByHeader(
        headers: List<String>,
        row: List<String>,
        candidates: List<String>
    ): String? {

        headers.forEachIndexed {
                index,
                header ->

            if (
                index >=
                row.size
            ) {

                return@forEachIndexed
            }

            val normalizedHeader =
                normalize(
                    header
                )

            val matched =
                candidates.any {

                    normalizedHeader
                        .contains(
                            normalize(it)
                        )
                }

            if (
                matched
            ) {

                val value =
                    row[
                        index
                    ].trim()

                if (
                    value.isNotBlank()
                ) {

                    return value
                }
            }
        }

        return null
    }

    private fun normalize(
        value: String
    ): String {

        return Normalizer
            .normalize(
                value,
                Normalizer.Form.NFD
            )
            .replace(
                Regex(
                    "\\p{Mn}+"
                ),
                ""
            )
            .lowercase()
            .trim()
    }
}