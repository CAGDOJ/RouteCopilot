package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.routecopilot.MainActivity

class SpxAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RouteCopilotACC"
        private const val CHECK_DELAY_MS = 400L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { checkCurrentScreen() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "SERVICO=ATIVO")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != SpxBridge.SPX_PACKAGE) return

        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, CHECK_DELAY_MS)
    }

    private fun checkCurrentScreen() {
        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        if (texts.isEmpty()) return

        val joined = texts.joinToString(" ").lowercase()

        if (looksLikeLogin(joined)) {
            SpxBridge.markStatus(this, SpxStatus.AUTH_REQUIRED)
            Log.d(TAG, "SPX=AUTH_REQUIRED")
            return
        }

        if (looksAuthenticated(texts, joined)) {
            SpxBridge.markStatus(this, SpxStatus.CONNECTED)
            Log.d(TAG, "SPX=CONNECTED")

            if (SpxBridge.shouldReturn(this)) {
                // Limpa ANTES de abrir o Copilot. Isso elimina o loop
                // Copilot -> SPX -> Encerrado -> Copilot -> SPX.
                SpxBridge.clearReturn(this)
                returnToCopilot()
            }
        }
    }

    private fun looksLikeLogin(text: String): Boolean {
        val strong = listOf(
            "esqueci minha senha",
            "fazer login",
            "iniciar sessão",
            "codigo de verificacao",
            "código de verificação"
        )
        if (strong.any(text::contains)) return true

        val signals = listOf("login", "senha", "email", "e-mail", "entrar")
        return signals.count(text::contains) >= 2
    }

    private fun looksAuthenticated(texts: List<String>, joined: String): Boolean {
        val atRegex = Regex("\\bAT[A-Z0-9]{8,}\\b", RegexOption.IGNORE_CASE)
        if (texts.any { atRegex.containsMatchIn(it.replace(" ", "")) }) return true

        // 'Encerrado' é uma tela pós-login do SPX. Não deve prender o usuário.
        val strong = listOf(
            "encerrado",
            "entregas",
            "em rota",
            "escanear",
            "ocorrência",
            "entregue"
        )
        if (strong.any(joined::contains)) return true

        val weak = listOf("rota", "pacotes", "entrega", "perfil")
        return weak.count(joined::contains) >= 2
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null) return
        if (!node.isPassword) {
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
        }
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), output)
    }

    private fun returnToCopilot() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("RETURNED_FROM_SPX", true)
        }

        runCatching {
            startActivity(intent)
        }.onFailure {
            Log.e(TAG, "RETURN_FAILED", it)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICO=INTERROMPIDO")
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        super.onDestroy()
    }
}
