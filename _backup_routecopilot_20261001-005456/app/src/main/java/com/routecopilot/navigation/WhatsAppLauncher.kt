package com.routecopilot.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppLauncher {

    fun open(
        context: Context,
        rawPhone: String?,
        message: String
    ) {
        val phone = normalizeBrazilPhone(rawPhone)

        if (phone == null) {
            Toast.makeText(
                context,
                "O SPX não forneceu um telefone utilizável para este pedido.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val uri = Uri.parse(
            "https://wa.me/$phone?text=${Uri.encode(message)}"
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
                "Não foi possível abrir o WhatsApp.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun normalizeBrazilPhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var digits = raw.filter(Char::isDigit)

        if (digits.startsWith("0")) {
            digits = digits.dropWhile { it == '0' }
        }

        if (!digits.startsWith("55") && digits.length in 10..11) {
            digits = "55$digits"
        }

        return digits.takeIf {
            it.length in 12..13
        }
    }
}
