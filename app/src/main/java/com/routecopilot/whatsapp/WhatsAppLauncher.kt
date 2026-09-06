package com.routecopilot.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object WhatsAppLauncher {
    fun open(
        context: Context,
        phone: String,
        message: String
    ) {
        val normalized =
            phone.filter(Char::isDigit)

        val encoded =
            URLEncoder.encode(
                message,
                "UTF-8"
            )

        val url =
            "https://wa.me/$normalized?text=$encoded"

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
                "Não foi possível abrir o WhatsApp.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
