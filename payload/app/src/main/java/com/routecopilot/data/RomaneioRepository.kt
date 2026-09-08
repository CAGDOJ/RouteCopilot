package com.routecopilot.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RomaneioRepository {

    private data class CandidateFile(
        val uri: Uri,
        val name: String,
        val lastModified: Long,
        val fileDateKey: String?
    )

    suspend fun syncLatestRoutes(
        context: Context,
        treeUri: Uri
    ): RouteSyncResult = withContext(Dispatchers.IO) {
        val files = listXlsxFiles(context, treeUri)
        if (files.isEmpty()) {
            error("Nenhum arquivo XLSX foi encontrado na pasta selecionada.")
        }

        val latestDateKey = files
            .mapNotNull { it.fileDateKey }
            .maxOrNull()

        val candidates = if (latestDateKey != null) {
            files.filter { it.fileDateKey == latestDateKey }
        } else {
            files.sortedByDescending { it.lastModified }.take(20)
        }

        val routesByKey = linkedMapOf<String, RomaneioRoute>()

        candidates
            .sortedByDescending { it.lastModified }
            .forEach { candidate ->
                val bytes = context.contentResolver
                    .openInputStream(candidate.uri)
                    ?.use { it.readBytes() }
                    ?: return@forEach

                val parsed = runCatching {
                    XlsxRomaneioParser.parseRoutes(
                        bytes = bytes,
                        sourceFileName = candidate.name,
                        sourceLastModified = candidate.lastModified
                    )
                }.getOrElse {
                    return@forEach
                }

                parsed.forEach { route ->
                    val key = route.routeKey
                    val current = routesByKey[key]
                    if (current == null || route.sourceLastModified > current.sourceLastModified) {
                        routesByKey[key] = route
                    }
                }
            }

        if (routesByKey.isEmpty()) {
            error("Os XLSX encontrados não possuem rotas válidas.")
        }

        val routes = routesByKey.values
            .sortedWith(
                compareByDescending<RomaneioRoute> { it.loadDate.orEmpty() }
                    .thenByDescending { it.sourceLastModified }
            )

        RouteSyncResult(
            routes = routes,
            sourceDate = latestDateKey?.let(::formatDateKey)
        )
    }

    private fun listXlsxFiles(
        context: Context,
        treeUri: Uri
    ): List<CandidateFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val result = mutableListOf<CandidateFile>()

        context.contentResolver.query(
            childrenUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex) ?: continue
                val name = cursor.getString(nameIndex).orEmpty()
                val modified = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""

                val isXlsx = name.endsWith(".xlsx", ignoreCase = true) ||
                    mime.contains("spreadsheetml", ignoreCase = true)

                if (!isXlsx) continue

                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                result += CandidateFile(
                    uri = uri,
                    name = name,
                    lastModified = modified,
                    fileDateKey = parseDateKeyFromName(name)
                )
            }
        }

        return result
    }

    private fun parseDateKeyFromName(name: String): String? {
        val patterns = listOf(
            Regex("(?:^|\\D)(\\d{2})[-_](\\d{2})[-_](\\d{4})(?:\\D|$)"),
            Regex("(?:^|\\D)(\\d{2})(\\d{2})(\\d{4})(?:\\D|$)")
        )

        for (regex in patterns) {
            val match = regex.find(name) ?: continue
            val day = match.groupValues[1]
            val month = match.groupValues[2]
            val year = match.groupValues[3]
            val key = "$year$month$day"
            if (isValidDateKey(key)) return key
        }

        return null
    }

    private fun isValidDateKey(key: String): Boolean {
        return runCatching {
            val parser = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { isLenient = false }
            parser.parse(key)
            true
        }.getOrDefault(false)
    }

    private fun formatDateKey(key: String): String? {
        return runCatching {
            val parser = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { isLenient = false }
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
            val date: Date = parser.parse(key) ?: return null
            formatter.format(date)
        }.getOrNull()
    }
}
