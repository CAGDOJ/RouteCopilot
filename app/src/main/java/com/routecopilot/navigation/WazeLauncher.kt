package com.routecopilot.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object WazeLauncher {
    fun navigateToCoordinates(
        context: Context,
        latitude: Double,
        longitude: Double
    ) {
        open(
            context,
            "https://waze.com/ul?ll=$latitude,$longitude&navigate=yes"
        )
    }

    fun navigateToAddress(
        context: Context,
        address: String
    ) {
        val encoded = URLEncoder.encode(
            address,
            "UTF-8"
        )

        open(
            context,
            "https://waze.com/ul?q=$encoded&navigate=yes"
        )
    }

    private fun open(
        context: Context,
        url: String
    ) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
            )
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Não foi possível abrir o Waze.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
