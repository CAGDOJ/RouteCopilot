package com.routecopilot.spx

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast

object SpxBridge {
    const val SPX_PACKAGE = "com.shopee.spx.driver.brazil"

    private const val PREFS = "routecopilot_spx_bridge_v4"
    private const val KEY_RETURN = "return_after_auth"
    private const val KEY_OPENED_AT = "opened_at"
    private const val KEY_STATUS = "last_status"

    fun refreshPresence(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SPX_PACKAGE)
        if (launchIntent == null) {
            markStatus(context, SpxStatus.UNAVAILABLE)
        } else {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATUS, null)
                ?.let { runCatching { SpxStatus.valueOf(it) }.getOrNull() }
            SpxSessionState.update(saved ?: SpxStatus.CHECKING)
        }
    }

    fun connect(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(SPX_PACKAGE)
        if (intent == null) {
            markStatus(context, SpxStatus.UNAVAILABLE)
            Toast.makeText(context, "SPX não encontrado neste aparelho.", Toast.LENGTH_LONG).show()
            return
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RETURN, true)
            .putLong(KEY_OPENED_AT, SystemClock.elapsedRealtime())
            .apply()

        markStatus(context, SpxStatus.CHECKING)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openForOfficialAction(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(SPX_PACKAGE)
        if (intent == null) {
            markStatus(context, SpxStatus.UNAVAILABLE)
            Toast.makeText(context, "SPX não encontrado neste aparelho.", Toast.LENGTH_LONG).show()
            return
        }

        // Ação oficial é explicitamente iniciada pelo motorista.
        // Não ativamos retorno automático aqui porque o SPX exibe os botões
        // "Entregue/Ocorrência" antes da baixa; retornar só pelo texto faria
        // o Copilot interromper a operação. O retorno automático fica restrito
        // ao fluxo de autenticação, que é seguro de detectar.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RETURN, false)
            .putLong(KEY_OPENED_AT, SystemClock.elapsedRealtime())
            .apply()

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun markStatus(context: Context, status: SpxStatus) {
        SpxSessionState.update(status)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATUS, status.name)
            .apply()
    }

    fun shouldReturn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RETURN, false)

    fun clearReturn(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RETURN, false)
            .apply()
    }
}
