package com.routecopilot.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WazeLauncher {

    fun open(
        context: Context,
        latitude: Double?,
        longitude: Double?
    ) {
        if (latitude == null || longitude == null) {
            Toast.makeText(
                context,
                "Coordenadas da próxima entrega ainda não estão disponíveis.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uri = Uri.parse(
            "https://waze.com/ul?ll=$latitude,$longitude&navigate=yes"
        )

        val intent = Intent(
            Intent.ACTION_VIEW,
            uri
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(
                context,
                "Não foi possível abrir o Waze.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
