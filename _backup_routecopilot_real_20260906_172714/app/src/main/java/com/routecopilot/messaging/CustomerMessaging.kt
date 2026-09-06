package com.routecopilot.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.routecopilot.route.DeliveryStop
import com.routecopilot.tracking.TrackingClient
import java.net.URLEncoder

object CustomerMessaging {

    fun initialMessage(
        stop: DeliveryStop,
        etaMinutes: Int
    ): String {
        val link = TrackingClient.trackingLink(stop)

        return buildString {
            append("Olá! Sua encomenda está em rota de entrega.")
            append("\n\nPrevisão de chegada: aproximadamente ")
            append(etaMinutes)
            append(" minutos.")

            if (!link.isNullOrBlank()) {
                append("\n\nAcompanhe a aproximação do entregador:")
                append("\n")
                append(link)
            }
        }
    }

    fun nextStopMessage(
        stop: DeliveryStop,
        etaMinutes: Int
    ): String {
        val link = TrackingClient.trackingLink(stop)

        return buildString {
            append("Olá! Sua encomenda é a próxima entrega.")
            append("\n\nO entregador está a aproximadamente ")
            append(etaMinutes)
            append(" minutos do seu endereço.")

            if (!link.isNullOrBlank()) {
                append("\n\nAcompanhe a aproximação:")
                append("\n")
                append(link)
            }
        }
    }

    fun openWhatsAppOrShare(
        context: Context,
        stop: DeliveryStop,
        message: String
    ) {
        val phone = stop.phone?.filter(Char::isDigit)

        if (!phone.isNullOrBlank()) {
            val normalizedPhone =
                if (phone.startsWith("55")) phone else "55$phone"

            val encoded = URLEncoder.encode(message, "UTF-8")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$normalizedPhone?text=$encoded")
            )

            runCatching {
                context.startActivity(intent)
            }.onFailure {
                shareGeneric(context, message)
            }
        } else {
            shareGeneric(context, message)

            Toast.makeText(
                context,
                "Telefone não identificado no SPX. Abrindo compartilhamento.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareGeneric(
        context: Context,
        message: String
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }

        context.startActivity(
            Intent.createChooser(intent, "Enviar mensagem")
        )
    }
}
