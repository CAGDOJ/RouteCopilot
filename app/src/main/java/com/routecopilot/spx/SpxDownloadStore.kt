package com.routecopilot.spx

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

data class DownloadFileMeta(
    val uri: Uri,
    val name: String,
    val lastModified: Long,
    val size: Long,
    val folder: String = ""
)

object SpxDownloadStore {

    private const val PREFS =
        "routecopilot_downloads"

    private const val KEY_TREE_URI =
        "downloads_tree_uri"

    private const val MAX_DEPTH =
        6

    fun saveTreeUri(
        context: Context,
        uri: Uri
    ): Boolean {

        return try {

            try {

                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

            } catch (_: Exception) {

                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
            }

            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    KEY_TREE_URI,
                    uri.toString()
                )
                .apply()

            true

        } catch (_: Exception) {

            false
        }
    }

    fun getTreeUri(
        context: Context
    ): Uri? {

        val value =
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_TREE_URI,
                    null
                )
                ?: return null

        return try {

            Uri.parse(value)

        } catch (_: Exception) {

            null
        }
    }

    fun hasPermission(
        context: Context
    ): Boolean {

        val treeUri =
            getTreeUri(context)
                ?: return false

        return context
            .contentResolver
            .persistedUriPermissions
            .any {

                it.uri == treeUri &&
                    it.isReadPermission
            }
    }

    fun findLatestXlsx(
        context: Context
    ): DownloadFileMeta? {

        val treeUri =
            getTreeUri(context)
                ?: return null

        val rootDocumentId =
            try {

                DocumentsContract
                    .getTreeDocumentId(
                        treeUri
                    )

            } catch (_: Exception) {

                return null
            }

        val result =
            mutableListOf<DownloadFileMeta>()

        try {

            searchDirectory(
                resolver =
                    context.contentResolver,

                treeUri =
                    treeUri,

                directoryDocumentId =
                    rootDocumentId,

                currentPath =
                    "",

                depth =
                    0,

                result =
                    result
            )

        } catch (_: Exception) {

            return null
        }

        return result
            .filterNot {
                it.name.startsWith("~$")
            }
            .maxWithOrNull(
                compareBy<DownloadFileMeta> {
                    it.lastModified
                }.thenBy {
                    it.size
                }
            )
    }

    private fun searchDirectory(
        resolver: ContentResolver,
        treeUri: Uri,
        directoryDocumentId: String,
        currentPath: String,
        depth: Int,
        result: MutableList<DownloadFileMeta>
    ) {

        if (depth > MAX_DEPTH) {
            return
        }

        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    treeUri,
                    directoryDocumentId
                )

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )

        resolver
            .query(
                childrenUri,
                projection,
                null,
                null,
                null
            )
            ?.use { cursor ->

                val idIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    )

                val nameIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    )

                val mimeIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    )

                val modifiedIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    )

                val sizeIndex =
                    cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_SIZE
                    )

                while (
                    cursor.moveToNext()
                ) {

                    if (
                        idIndex < 0 ||
                        nameIndex < 0
                    ) {
                        continue
                    }

                    val documentId =
                        cursor.getString(
                            idIndex
                        )
                            ?: continue

                    val name =
                        cursor.getString(
                            nameIndex
                        )
                            ?: continue

                    val mime =
                        if (
                            mimeIndex >= 0 &&
                            !cursor.isNull(mimeIndex)
                        ) {

                            cursor.getString(
                                mimeIndex
                            )

                        } else {

                            ""
                        }

                    val path =
                        if (currentPath.isBlank()) {
                            name
                        } else {
                            "$currentPath/$name"
                        }

                    if (
                        mime ==
                        DocumentsContract.Document.MIME_TYPE_DIR
                    ) {

                        searchDirectory(
                            resolver =
                                resolver,

                            treeUri =
                                treeUri,

                            directoryDocumentId =
                                documentId,

                            currentPath =
                                path,

                            depth =
                                depth + 1,

                            result =
                                result
                        )

                        continue
                    }

                    if (
                        !name
                            .lowercase()
                            .endsWith(".xlsx")
                    ) {
                        continue
                    }

                    val modified =
                        if (
                            modifiedIndex >= 0 &&
                            !cursor.isNull(modifiedIndex)
                        ) {

                            cursor.getLong(
                                modifiedIndex
                            )

                        } else {

                            0L
                        }

                    val size =
                        if (
                            sizeIndex >= 0 &&
                            !cursor.isNull(sizeIndex)
                        ) {

                            cursor.getLong(
                                sizeIndex
                            )

                        } else {

                            0L
                        }

                    val documentUri =
                        DocumentsContract
                            .buildDocumentUriUsingTree(
                                treeUri,
                                documentId
                            )

                    result.add(
                        DownloadFileMeta(
                            uri =
                                documentUri,

                            name =
                                name,

                            lastModified =
                                modified,

                            size =
                                size,

                            folder =
                                currentPath
                        )
                    )
                }
            }
    }
}