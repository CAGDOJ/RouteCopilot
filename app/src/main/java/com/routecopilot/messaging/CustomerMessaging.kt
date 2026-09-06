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
            append("Olá! Sua encomenda está em rota de entrega. ")
            append("Você poderá acompanhar a aproximação do entregador pelo link abaixo.")
            if (!link.isNullOrBlank()) {
                append("\n\n")
                append(link)
            }
            append("\n\nPrevisão de chegada: aproximadamente ")
            append(etaMinutes)
            append(" minutos.")
        }
    }

    fun nextStopMessage(
        stop: DeliveryStop,
        etaMinutes: Int
    ): String {
        val link = TrackingClient.trackingLink(stop)
        return buildString {
            append("Olá! Sua encomenda é a próxima entrega. ")
            append("O entregador está a aproximadamente ")
            append(etaMinutes)
            append(" minutos do seu endereço.")
            if (!link.isNullOrBlank()) {
                append("\n\nAcompanhe a aproximação pelo link abaixo:\n")
                append(link)
            }
        }
    }

    fun openShare(
        context: Context,
        stop: DeliveryStop,
        message: String
    ) {
        val phone = stop.phone?.filter(Char::isDigit)

        if (!phone.isNullOrBlank()) {
            val encoded = URLEncoder.encode(message, "UTF-8")
            val uri = Uri.parse("https://wa.me/$phone?text=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            runCatching { context.startActivity(intent) }
                .onFailure { shareGeneric(context, message) }
        } else {
            shareGeneric(context, message)
            Toast.makeText(
                context,
                "Telefone não identificado no SPX; abrindo compartilhamento.",
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
        context.startActivity(Intent.createChooser(intent, "Enviar mensagem"))
    }
}
