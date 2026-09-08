package com.routecopilot.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object WazeLauncher {
    fun navigate(context: Context, address: String) {
        if (address.isBlank()) {
            Toast.makeText(context, "Endereço não disponível.", Toast.LENGTH_SHORT).show()
            return
        }

        val encoded = URLEncoder.encode(address, Charsets.UTF_8.name())
        val wazeUri = Uri.parse("https://waze.com/ul?q=$encoded&navigate=yes")
        val intent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
            setPackage("com.waze")
        }

        runCatching {
            context.startActivity(intent)
        }.recoverCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, wazeUri))
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o Waze.", Toast.LENGTH_LONG).show()
        }
    }
}
