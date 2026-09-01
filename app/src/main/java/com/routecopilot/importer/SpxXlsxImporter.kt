package com.routecopilot.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

import com.routecopilot.model.DeliveryPackage
import com.routecopilot.model.ImportedRoute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.w3c.dom.Element

import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream

import javax.xml.parsers.DocumentBuilderFactory

object SpxXlsxImporter {

    /*
     * Exemplos reais:
     *
     * BR260544449868H
     */
    private val brRegex =
        Regex(
            "\\bBR[A-Z0-9]{8,}\\b",
            RegexOption.IGNORE_CASE
        )

    /*
     * Exemplo real:
     *
     * AT2026083195IG3
     */
    private val atRegex =
        Regex(
            "\\bAT[A-Z0-9]{8,}\\b",
            RegexOption.IGNORE_CASE
        )

    suspend fun importRoute(
        context: Context,
        uri: Uri
    ): ImportedRoute =
        withContext(
            Dispatchers.IO
        ) {

            val fileName =
                queryFileName(
                    context,
                    uri
                )

            val bytes =
                context
                    .contentResolver
                    .openInputStream(
                        uri
                    )
                    ?.use {
                        it.readBytes()
                    }
                    ?: error(
                        "Não foi possível abrir o Excel."
                    )

            /*
             * XLSX internamente é um ZIP contendo XML.
             *
             * Por isso não precisamos de Apache POI.
             */
            val entries =
                unzip(
                    bytes
                )

            val sharedStrings =
                parseSharedStrings(
                    entries[
                        "xl/sharedStrings.xml"
                    ]
                )

            val sheetEntry =
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
                    .sorted()
                    .firstOrNull()
                    ?: error(
                        "Nenhuma planilha encontrada no Excel."
                    )

            val rows =
                parseRows(
                    entries.getValue(
                        sheetEntry
                    ),
                    sharedStrings
                )

            if (
                rows.isEmpty()
            ) {

                error(
                    "O Excel está vazio."
                )
            }

            val headerRowIndex =
                findHeaderRow(
                    rows
                )

            val headerRow =
                rows[
                    headerRowIndex
                ]

            val headers =
                headerRow.mapValues {

                    normalizeHeader(
                        it.value
                    )
                }

            /*
             * O Excel real enviado contém:
             *
             * AT ID
             * Sequence
             * Stop
             * SPX TN
             * Destination Address
             * Bairro
             * City
             * Zipcode/Postal code
             * Latitude
             * Longitude
             */
            val columns =
                resolveColumns(
                    headers
                )

            val packages =
                linkedMapOf<
                    String,
                    DeliveryPackage
                    >()

            var routeAt:
                String? =
                null

            rows
                .drop(
                    headerRowIndex + 1
                )
                .forEachIndexed {
                        offset,
                        row ->

                    val sourceRow =
                        headerRowIndex +
                            2 +
                            offset

                    val allValues =
                        row
                            .values
                            .filter {

                                it.isNotBlank()
                            }

                    if (
                        allValues.isEmpty()
                    ) {

                        return@forEachIndexed
                    }

                    /*
                     * SPX TN é a coluna que contém
                     * o código BR.
                     */
                    val br =
                        extractBr(
                            valueAt(
                                row,
                                columns.br
                            ),
                            allValues
                        )
                            ?: return@forEachIndexed

                    val at =
                        extractAt(
                            valueAt(
                                row,
                                columns.at
                            ),
                            allValues
                        )

                    if (
                        routeAt == null &&
                        at != null
                    ) {

                        routeAt =
                            at
                    }

                    val address =
                        valueAt(
                            row,
                            columns.address
                        )
                            .orEmpty()
                            .trim()

                    val neighborhood =
                        valueAt(
                            row,
                            columns.neighborhood
                        )

                    val city =
                        valueAt(
                            row,
                            columns.city
                        )

                    val state =
                        valueAt(
                            row,
                            columns.state
                        )

                    val zip =
                        valueAt(
                            row,
                            columns.zipCode
                        )

                    val recipient =
                        valueAt(
                            row,
                            columns.recipient
                        )

                    val phone =
                        valueAt(
                            row,
                            columns.phone
                        )

                    val status =
                        valueAt(
                            row,
                            columns.status
                        )

                    val sequence =
                        parseInt(
                            valueAt(
                                row,
                                columns.sequence
                            )
                        )

                    val stop =
                        parseInt(
                            valueAt(
                                row,
                                columns.stop
                            )
                        )

                    val latitude =
                        parseDouble(
                            valueAt(
                                row,
                                columns.latitude
                            )
                        )

                    val longitude =
                        parseDouble(
                            valueAt(
                                row,
                                columns.longitude
                            )
                        )

                    val item =
                        DeliveryPackage(

                            br = br,

                            at = at,

                            sequence =
                                sequence,

                            stop =
                                stop,

                            recipient =
                                recipient,

                            phone =
                                phone,

                            address =
                                address,

                            neighborhood =
                                neighborhood,

                            city =
                                city,

                            state =
                                state,

                            zipCode =
                                zip,

                            status =
                                status,

                            latitude =
                                latitude,

                            longitude =
                                longitude,

                            sourceRow =
                                sourceRow
                        )

                    /*
                     * Código BR é a identidade única
                     * do pacote.
                     *
                     * Assim um mesmo BR nunca é contado
                     * duas vezes.
                     */
                    packages.putIfAbsent(
                        br,
                        item
                    )
                }

            if (
                packages.isEmpty()
            ) {

                error(
                    "Nenhum pacote BR foi encontrado. Selecione o Excel exportado pelo SPX."
                )
            }

            ImportedRoute(

                at =
                    routeAt,

                sourceFileName =
                    fileName,

                packages =
                    packages
                        .values
                        .toList()
            )
        }

    private data class Columns(

        val br: Int? = null,

        val at: Int? = null,

        val sequence: Int? = null,

        val stop: Int? = null,

        val recipient: Int? = null,

        val phone: Int? = null,

        val address: Int? = null,

        val neighborhood: Int? = null,

        val city: Int? = null,

        val state: Int? = null,

        val zipCode: Int? = null,

        val status: Int? = null,

        val latitude: Int? = null,

        val longitude: Int? = null
    )

    private fun resolveColumns(
        headers:
            Map<Int, String>
    ): Columns {

        fun find(
            vararg aliases: String
        ): Int? {

            val normalizedAliases =
                aliases.map(
                    ::normalizeHeader
                )

            return headers
                .entries
                .firstOrNull {
                        (_, header) ->

                    normalizedAliases.any {
                            alias ->

                        header ==
                            alias ||
                            header.contains(
                                alias
                            )
                    }
                }
                ?.key
        }

        return Columns(

            /*
             * Nome REAL no arquivo:
             * SPX TN
             */
            br =
                find(
                    "spx tn",
                    "tracking number",
                    "tracking",
                    "codigo br",
                    "br"
                ),

            /*
             * Nome REAL:
             * AT ID
             */
            at =
                find(
                    "at id",
                    "at",
                    "route id",
                    "rota"
                ),

            sequence =
                find(
                    "sequence",
                    "sequencia",
                    "ordem"
                ),

            stop =
                find(
                    "stop",
                    "parada"
                ),

            recipient =
                find(
                    "recipient",
                    "destinatario",
                    "cliente"
                ),

            phone =
                find(
                    "phone",
                    "telefone",
                    "celular",
                    "contato"
                ),

            /*
             * Nome REAL:
             * Destination Address
             */
            address =
                find(
                    "destination address",
                    "address",
                    "endereco"
                ),

            neighborhood =
                find(
                    "bairro",
                    "neighborhood",
                    "district"
                ),

            city =
                find(
                    "city",
                    "cidade",
                    "municipio"
                ),

            state =
                find(
                    "state",
                    "estado",
                    "uf"
                ),

            /*
             * Nome REAL:
             * Zipcode/Postal code
             */
            zipCode =
                find(
                    "zipcode postal code",
                    "postal code",
                    "zipcode",
                    "zip code",
                    "cep"
                ),

            status =
                find(
                    "status",
                    "situacao"
                ),

            latitude =
                find(
                    "latitude",
                    "lat"
                ),

            longitude =
                find(
                    "longitude",
                    "lng",
                    "lon"
                )
        )
    }

    private fun findHeaderRow(
        rows:
            List<Map<Int, String>>
    ): Int {

        val max =
            minOf(
                rows.size,
                20
            )

        var bestIndex =
            0

        var bestScore =
            -1

        for (
            i in 0 until max
        ) {

            val normalized =
                rows[i]
                    .values
                    .map(
                        ::normalizeHeader
                    )

            var score =
                0

            if (
                normalized.any {

                    it ==
                        "spx tn" ||
                        it.contains(
                            "tracking"
                        )
                }
            ) {

                score +=
                    5
            }

            if (
                normalized.any {

                    it.contains(
                        "destination address"
                    ) ||
                        it.contains(
                            "address"
                        )
                }
            ) {

                score +=
                    4
            }

            if (
                normalized.any {

                    it ==
                        "at id" ||
                        it.contains(
                            "route"
                        )
                }
            ) {

                score +=
                    3
            }

            if (
                normalized.any {

                    it.contains(
                        "latitude"
                    )
                }
            ) {

                score +=
                    2
            }

            if (
                normalized.any {

                    it.contains(
                        "longitude"
                    )
                }
            ) {

                score +=
                    2
            }

            if (
                score >
                bestScore
            ) {

                bestScore =
                    score

                bestIndex =
                    i
            }
        }

        return bestIndex
    }

    private fun extractBr(
        preferred: String?,
        allValues:
            List<String>
    ): String? {

        preferred?.let {

            brRegex
                .find(
                    it.uppercase(
                        Locale.ROOT
                    )
                )
                ?.value
                ?.let {
                        value ->

                    return value.uppercase(
                        Locale.ROOT
                    )
                }
        }

        for (
            value in allValues
        ) {

            val match =
                brRegex.find(
                    value.uppercase(
                        Locale.ROOT
                    )
                )

            if (
                match != null
            ) {

                return match
                    .value
                    .uppercase(
                        Locale.ROOT
                    )
            }
        }

        return null
    }

    private fun extractAt(
        preferred: String?,
        allValues:
            List<String>
    ): String? {

        preferred?.let {

            atRegex
                .find(
                    it.uppercase(
                        Locale.ROOT
                    )
                )
                ?.value
                ?.let {
                        value ->

                    return value.uppercase(
                        Locale.ROOT
                    )
                }
        }

        for (
            value in allValues
        ) {

            val match =
                atRegex.find(
                    value.uppercase(
                        Locale.ROOT
                    )
                )

            if (
                match != null
            ) {

                return match
                    .value
                    .uppercase(
                        Locale.ROOT
                    )
            }
        }

        return null
    }

    /*
     * "-" vira null.
     *
     * Isso é importante porque a primeira linha
     * do seu Excel possui "-" em Sequence/Stop.
     */
    private fun parseInt(
        value: String?
    ): Int? {

        if (
            value.isNullOrBlank() ||
            value.trim() == "-"
        ) {

            return null
        }

        return value
            .trim()
            .replace(
                Regex(
                    "[^0-9-]"
                ),
                ""
            )
            .toIntOrNull()
    }

    private fun parseDouble(
        value: String?
    ): Double? {

        return value
            ?.trim()
            ?.replace(
                " ",
                ""
            )
            ?.replace(
                ",",
                "."
            )
            ?.toDoubleOrNull()
    }

    private fun valueAt(
        row:
            Map<Int, String>,
        column: Int?
    ): String? {

        return column
            ?.let {

                row[it]
            }
            ?.takeIf {

                it.isNotBlank()
            }
    }

    private fun normalizeHeader(
        value: String
    ): String {

        return Normalizer
            .normalize(
                value
                    .trim()
                    .lowercase(
                        Locale.ROOT
                    ),
                Normalizer.Form.NFD
            )
            .replace(
                Regex(
                    "\\p{Mn}+"
                ),
                ""
            )
            .replace(
                Regex(
                    "[^a-z0-9]+"
                ),
                " "
            )
            .trim()
    }

    private fun queryFileName(
        context: Context,
        uri: Uri
    ): String? {

        return context
            .contentResolver
            .query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )
            ?.use {
                    cursor ->

                if (
                    cursor.moveToFirst()
                ) {

                    cursor.getString(
                        0
                    )

                } else {

                    null
                }
            }
    }

    private fun unzip(
        bytes: ByteArray
    ): Map<String, ByteArray> {

        val result =
            mutableMapOf<
                String,
                ByteArray
                >()

        ZipInputStream(
            ByteArrayInputStream(
                bytes
            )
        ).use {
                zip ->

            while (
                true
            ) {

                val entry =
                    zip.nextEntry
                        ?: break

                if (
                    !entry.isDirectory
                ) {

                    result[
                        entry.name
                    ] =
                        zip.readBytes()
                }

                zip.closeEntry()
            }
        }

        return result
    }

    private fun parseSharedStrings(
        bytes: ByteArray?
    ): List<String> {

        if (
            bytes == null
        ) {

            return emptyList()
        }

        val document =
            newDocumentBuilder()
                .parse(
                    ByteArrayInputStream(
                        bytes
                    )
                )

        val nodes =
            document.getElementsByTagName(
                "si"
            )

        val result =
            ArrayList<String>(
                nodes.length
            )

        for (
            i in 0 until
            nodes.length
        ) {

            val element =
                nodes.item(
                    i
                ) as Element

            val textNodes =
                element
                    .getElementsByTagName(
                        "t"
                    )

            val text =
                buildString {

                    for (
                        j in 0 until
                        textNodes.length
                    ) {

                        append(
                            textNodes
                                .item(
                                    j
                                )
                                .textContent
                        )
                    }
                }

            result +=
                text
        }

        return result
    }

    private fun parseRows(
        bytes: ByteArray,
        sharedStrings:
            List<String>
    ): List<Map<Int, String>> {

        val document =
            newDocumentBuilder()
                .parse(
                    ByteArrayInputStream(
                        bytes
                    )
                )

        val rowNodes =
            document
                .getElementsByTagName(
                    "row"
                )

        val result =
            ArrayList<
                Map<Int, String>
                >(
                rowNodes.length
            )

        for (
            i in 0 until
            rowNodes.length
        ) {

            val rowElement =
                rowNodes.item(
                    i
                ) as Element

            val cellNodes =
                rowElement
                    .getElementsByTagName(
                        "c"
                    )

            val row =
                linkedMapOf<
                    Int,
                    String
                    >()

            for (
                j in 0 until
                cellNodes.length
            ) {

                val cell =
                    cellNodes.item(
                        j
                    ) as Element

                val ref =
                    cell.getAttribute(
                        "r"
                    )

                val column =
                    columnIndex(
                        ref
                    )

                val type =
                    cell.getAttribute(
                        "t"
                    )

                val value =
                    when (
                        type
                    ) {

                        "s" -> {

                            val index =
                                cell
                                    .getElementsByTagName(
                                        "v"
                                    )
                                    .item(
                                        0
                                    )
                                    ?.textContent
                                    ?.toIntOrNull()

                            index
                                ?.let {

                                    sharedStrings.getOrNull(
                                        it
                                    )
                                }
                                .orEmpty()
                        }

                        "inlineStr" -> {

                            cell
                                .getElementsByTagName(
                                    "t"
                                )
                                .item(
                                    0
                                )
                                ?.textContent
                                .orEmpty()
                        }

                        else -> {

                            cell
                                .getElementsByTagName(
                                    "v"
                                )
                                .item(
                                    0
                                )
                                ?.textContent
                                .orEmpty()
                        }
                    }

                if (
                    value.isNotBlank()
                ) {

                    row[
                        column
                    ] =
                        value.trim()
                }
            }

            result +=
                row
        }

        return result
    }

    private fun columnIndex(
        cellRef: String
    ): Int {

        val letters =
            cellRef
                .takeWhile {
                    it.isLetter()
                }
                .uppercase(
                    Locale.ROOT
                )

        var result =
            0

        for (
            char in letters
        ) {

            result =
                result *
                    26 +
                    (
                        char -
                            'A' +
                            1
                        )
        }

        return result -
            1
    }

    private fun newDocumentBuilder() =
        DocumentBuilderFactory
            .newInstance()
            .apply {

                isNamespaceAware =
                    false

                setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
                )
            }
            .newDocumentBuilder()
}