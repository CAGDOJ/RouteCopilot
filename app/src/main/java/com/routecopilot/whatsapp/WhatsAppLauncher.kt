package com.routecopilot.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object WhatsAppLauncher {
    fun open(context: Context, phone: String, message: String) {
        val number = phone.filter { it.isDigit() }
        val encoded = URLEncoder.encode(message, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) }
        catch (_: Exception) { Toast.makeText(context, "Não foi possível abrir o WhatsApp.", Toast.LENGTH_LONG).show() }
    }
}
