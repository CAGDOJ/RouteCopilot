package com.routecopilot.data

import android.content.Context
import android.net.Uri

object RomaneioFolderStore {
    private const val PREFS = "routecopilot_romaneio"
    private const val KEY_URI = "folder_uri"

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    fun get(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
}
