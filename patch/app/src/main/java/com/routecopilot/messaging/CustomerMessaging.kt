package com.routecopilot.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.routecopilot.route.DeliveryStop
import com.routecopilot.route.RouteRepository
import com.routecopilot.tracking.TrackingClient
import java.net.URLEncoder

object CustomerMessaging {

    private data class BatchItem(
        val stop: DeliveryStop,
        val message: String
    )

    private var batchQueue: List<BatchItem> = emptyList()
    private var batchIndex: Int = 0
    private var batchActive: Boolean = false
    private var waitingWhatsAppReturn: Boolean = false

    /*
     * O horário NÃO vai mais na mensagem.
     * Ele muda durante a rota e é atualizado na página web do cliente.
     */
    fun initialMessage(
        stop: DeliveryStop,
        etaMinutes: Int = 0
    ): String {
        val link = TrackingClient.trackingLink(stop)

        return buildString {
            append("Olá! Sua encomenda saiu para entrega e está em rota.")

            if (!link.isNullOrBlank()) {
                append("\n\nAcompanhe a aproximação do entregador e a previsão atualizada pelo link:")
                append("\n")
                append(link)
            } else {
                append("\n\nO acompanhamento em tempo real será disponibilizado pelo RouteCopilot.")
            }
        }
    }

    fun nextStopMessage(
        stop: DeliveryStop,
        etaMinutes: Int = 0
    ): String {
        val link = TrackingClient.trackingLink(stop)

        return buildString {
            append("Olá! Sua entrega é a próxima parada.")

            if (!link.isNullOrBlank()) {
                append("\n\nAcompanhe a aproximação do entregador e a previsão atualizada aqui:")
                append("\n")
                append(link)
            }
        }
    }

    /*
     * Um toque no RouteCopilot inicia a fila de TODOS os clientes.
     * O WhatsApp comum não oferece API pública para confirmar o envio
     * silenciosamente. Por isso o app abre cada conversa já com a mensagem
     * individual pronta; ao voltar do WhatsApp, a próxima abre sozinha.
     */
    fun startBatch(
        context: Context,
        stops: List<DeliveryStop>
    ) {
        val valid = stops
            .filter { !it.phone.isNullOrBlank() }
            .map { stop ->
                BatchItem(
                    stop = stop,
                    message = initialMessage(stop)
                )
            }

        if (valid.isEmpty()) {
            Toast.makeText(
                context,
                "Nenhum telefone foi identificado nos pedidos.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        batchQueue = valid
        batchIndex = 0
        batchActive = true
        waitingWhatsAppReturn = false

        Toast.makeText(
            context,
            "Fila iniciada: ${valid.size} clientes.",
            Toast.LENGTH_SHORT
        ).show()

        openCurrentBatchItem(context)
    }

    /*
     * MainActivity chama isto no onResume().
     * Assim, depois que o entregador envia/volta do WhatsApp,
     * a próxima conversa da fila abre automaticamente.
     */
    fun onHostResume(context: Context) {
        if (!batchActive || !waitingWhatsAppReturn) return

        waitingWhatsAppReturn = false
        batchIndex++

        if (batchIndex >= batchQueue.size) {
            batchActive = false
            batchQueue = emptyList()

            Toast.makeText(
                context,
                "Fila de mensagens concluída.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed(
            {
                if (batchActive) {
                    openCurrentBatchItem(context)
                }
            },
            450L
        )
    }

    fun cancelBatch() {
        batchActive = false
        waitingWhatsAppReturn = false
        batchQueue = emptyList()
        batchIndex = 0
    }

    private fun openCurrentBatchItem(context: Context) {
        if (!batchActive || batchIndex !in batchQueue.indices) return

        val item = batchQueue[batchIndex]

        RouteRepository.markMessageSent(item.stop.br)

        waitingWhatsAppReturn = true

        openWhatsAppOrShare(
            context = context,
            stop = item.stop,
            message = item.message
        )
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
