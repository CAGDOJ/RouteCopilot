package com.routecopilot.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WazeLauncher {
    fun navigate(context: Context, lat: Double, lon: Double) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")).apply {
            setPackage("com.waze")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) }
        catch (_: Exception) { Toast.makeText(context, "Waze não encontrado.", Toast.LENGTH_LONG).show() }
    }
}
