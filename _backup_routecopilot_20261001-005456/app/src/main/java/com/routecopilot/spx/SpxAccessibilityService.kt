package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.routecopilot.MainActivity
import com.routecopilot.route.RouteRepository

class SpxAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG =
            "RouteCopilotACC"

        private const val SPX_PACKAGE =
            "com.shopee.spx.driver.brazil"

        private const val SCAN_DELAY_MS =
            700L
    }

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val scanRunnable =
        Runnable {
            scan()
        }

    private lateinit var navigator:
        SpxNavigator

    private val controller =
        SpxImportController()

    private var lastImportId =
        -1

    private var finished =
        false

    private var lastCountLogged =
        -1

    override fun onServiceConnected() {
        super.onServiceConnected()

        RouteRepository.initialize(
            applicationContext
        )

        SpxSessionState.initialize(
            applicationContext
        )

        navigator =
            SpxNavigator(
                this,
                handler
            )

        Log.d(
            TAG,
            "SERVICO=ATIVO"
        )
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (event == null) {
            return
        }

        if (
            event.packageName
                ?.toString() !=
            SPX_PACKAGE
        ) {
            return
        }

        val active =
            SpxSessionState
                .syncActive
                .value ||
                SpxSessionState
                    .scannerRequested
                    .value

        if (!active) {
            return
        }

        schedule(
            180L
        )
    }

    private fun resetForImportIfNeeded() {
        val currentId =
            SpxSessionState
                .importId
                .value

        if (
            currentId ==
            lastImportId
        ) {
            return
        }

        lastImportId =
            currentId

        finished =
            false

        lastCountLogged =
            -1

        controller.reset()

        navigator.reset()

        Log.d(
            TAG,
            "IMPORT=START | ID=$currentId"
        )
    }

    private fun scan() {
        resetForImportIfNeeded()

        val root =
            rootInActiveWindow
                ?: run {
                    SpxSessionState.update(
                        SpxState.CHECKING_SESSION,
                        "Aguardando o SPX carregar..."
                    )

                    schedule()

                    return
                }

        if (
            root.packageName
                ?.toString() !=
            SPX_PACKAGE
        ) {
            return
        }

        if (
            SpxSessionState
                .scannerRequested
                .value
        ) {
            if (
                navigator.openScanner(
                    root
                )
            ) {
                SpxSessionState
                    .clearScannerRequest()

                Log.d(
                    TAG,
                    "SCANNER=OPENED"
                )

                return
            }

            /*
             * Se ainda não encontramos o botão, continuamos observando
             * a tela do SPX mesmo que a importação anterior já tenha terminado.
             */
            schedule(700L)
            return
        }

        if (finished) {
            return
        }

        if (
            controller.timedOut()
        ) {
            handleTimeout()

            return
        }

        val texts =
            mutableListOf<String>()

        SpxParser.collectTexts(
            root,
            texts
        )

        if (
            texts.isEmpty()
        ) {
            SpxSessionState.update(
                SpxState.CHECKING_SESSION,
                "Aguardando conteúdo do SPX..."
            )

            schedule()

            return
        }

        val screen =
            SpxParser.normalizeScreen(
                texts
            )

        if (
            SpxParser.isLoginScreen(
                screen
            )
        ) {
            SpxSessionState.update(
                SpxState.LOGIN_REQUIRED,
                "Faça o login normalmente no SPX. O Copilot continua sozinho depois."
            )

            schedule(
                900L
            )

            return
        }

        if (
            SpxParser.isConsentScreen(
                screen
            )
        ) {
            SpxSessionState.update(
                SpxState.CONSENT_REQUIRED,
                "Confirme o aceite na tela oficial do SPX."
            )

            schedule(
                900L
            )

            return
        }

        if (
            SpxParser.isFaceCheckScreen(
                screen
            )
        ) {
            SpxSessionState.update(
                SpxState.FACE_CHECK_REQUIRED,
                "Conclua a autenticação facial normalmente no SPX."
            )

            schedule(
                900L
            )

            return
        }

        val at =
            SpxParser.findAt(
                texts
            )

        val expected =
            SpxParser.findExpectedTotal(
                texts
            )

        if (at != null) {
            val importAtChanged =
                controller.observeAt(
                    at
                )

            val repositorySwitched =
                SpxSessionState
                    .acceptDetectedRoute(
                        at = at,
                        loadingDate =
                            SpxParser
                                .dateFromAt(at),
                        expectedTotal =
                            expected
                    )

            if (
                expected != null
            ) {
                SpxSessionState
                    .updateExpectedTotal(
                        expected
                    )
            }

            if (
                importAtChanged ||
                repositorySwitched
            ) {
                Log.d(
                    TAG,
                    "NEW_ROUTE=DETECTED"
                )
            }

            SpxSessionState.update(
                SpxState.ROUTE_DETECTED,
                "Rota ${maskAt(at)} localizada."
            )

            Log.d(
                TAG,
                "ROTA_AT=DETECTADA"
            )
        }

        if (
            expected != null &&
            controller.observedAt != null
        ) {
            SpxSessionState
                .updateExpectedTotal(
                    expected
                )

            Log.d(
                TAG,
                "TOTAL_ESPERADO=$expected"
            )
        }

        val candidates =
            SpxParser
                .findPackageCandidates(
                    root
                )

        val visibleCodes =
            candidates
                .map {
                    it.br
                }
                .toSet()

        /*
         * Evita misturar pedidos de uma AT anterior:
         * só salvamos BRs depois que a AT desta importação
         * foi observada.
         */
        if (
            candidates.isNotEmpty() &&
            controller.observedAt != null
        ) {
            val added =
                SpxSessionState
                    .addCandidates(
                        candidates
                    )

            controller
                .observeVisibleCodes(
                    visibleCodes,
                    added
                )

            val count =
                SpxSessionState
                    .packageCount
                    .value

            val total =
                RouteRepository
                    .activeRoute
                    .value
                    ?.expectedTotal

            if (
                count !=
                lastCountLogged
            ) {
                lastCountLogged =
                    count

                Log.d(
                    TAG,
                    "PACOTES_TOTAL=$count | ESPERADO=${total ?: "?"}"
                )
            }

            SpxSessionState.update(
                SpxState.IMPORTING_PACKAGES,
                if (total != null) {
                    "Importando $count de $total pedidos..."
                } else {
                    "Importando $count pedidos..."
                }
            )

            if (
                total != null &&
                count >= total
            ) {
                finishImport(
                    partial = false
                )

                return
            }

            if (
                total == null &&
                controller
                    .shouldFinishWithoutExpectedTotal(
                        count
                    )
            ) {
                finishImport(
                    partial = false
                )

                return
            }

            val moved =
                navigator.scrollForward(
                    root
                )

            controller
                .noteNativeScrollResult(
                    moved
                )

            if (moved) {
                Log.d(
                    TAG,
                    "SCROLL=NODE"
                )

                schedule(
                    950L
                )

                return
            }

            if (
                navigator.swipeUp()
            ) {
                Log.d(
                    TAG,
                    "SCROLL=GESTURE"
                )

                schedule(
                    1_100L
                )

                return
            }

            SpxSessionState.update(
                SpxState.VERIFYING_END,
                "Confirmando o final da lista..."
            )

            schedule(
                900L
            )

            return
        }

        /*
         * Ainda não sabemos a AT desta importação.
         * Não importamos BRs usando a AT antiga persistida.
         */
        if (
            candidates.isNotEmpty() &&
            controller.observedAt == null
        ) {
            SpxSessionState.update(
                SpxState.FINDING_ROUTE,
                "Identificando a AT antes de importar os pedidos..."
            )
        }

        if (
            navigator.openDeliveries(
                root
            )
        ) {
            SpxSessionState.update(
                SpxState.OPENING_DELIVERIES,
                "Abrindo Entregas..."
            )

            Log.d(
                TAG,
                "NAV=ENTREGAS"
            )

            schedule(
                1_200L
            )

            return
        }

        if (
            navigator.openInRoute(
                root
            )
        ) {
            SpxSessionState.update(
                SpxState.OPENING_IN_ROUTE,
                "Abrindo pedidos Em Rota..."
            )

            Log.d(
                TAG,
                "NAV=EM_ROTA"
            )

            schedule(
                1_200L
            )

            return
        }

        SpxSessionState.update(
            SpxState.FINDING_ROUTE,
            if (
                controller.observedAt ==
                null
            ) {
                "Localizando a nova AT no SPX..."
            } else {
                "Localizando os pedidos da rota..."
            }
        )

        schedule(
            900L
        )
    }

    private fun handleTimeout() {
        val route =
            RouteRepository
                .activeRoute
                .value

        val count =
            route
                ?.packages
                ?.size
                ?: 0

        if (
            route != null &&
            count > 0
        ) {
            Log.d(
                TAG,
                "IMPORT=TIMEOUT_PARTIAL | TOTAL=$count | ESPERADO=${route.expectedTotal ?: "?"}"
            )

            finishImport(
                partial = true
            )
        } else {
            failAndReturn(
                "Não consegui confirmar os pedidos desta rota. " +
                    "Abra Entrega / Em Rota no SPX e tente sincronizar novamente."
            )
        }
    }

    private fun finishImport(
        partial: Boolean
    ) {
        val route =
            RouteRepository
                .activeRoute
                .value

        if (
            route == null ||
            route.packages.isEmpty()
        ) {
            failAndReturn(
                "Nenhum pedido da nova rota foi identificado."
            )

            return
        }

        finished =
            true

        handler.removeCallbacks(
            scanRunnable
        )

        RouteRepository.markReady(
            partial
        )

        SpxSessionState.update(
            if (partial) {
                SpxState.IMPORT_PARTIAL
            } else {
                SpxState.IMPORT_COMPLETE
            },
            if (partial) {
                "Importação parcial: ${route.packages.size} pedidos. Revise a rota."
            } else {
                "Importação concluída: ${route.packages.size} pedidos."
            }
        )

        Log.d(
            TAG,
            "IMPORT_COMPLETE | TOTAL=${route.packages.size} | PARTIAL=$partial"
        )

        returnToCopilot(
            partial
        )
    }

    private fun failAndReturn(
        message: String
    ) {
        finished =
            true

        handler.removeCallbacks(
            scanRunnable
        )

        SpxSessionState.fail(
            message
        )

        Log.d(
            TAG,
            "IMPORT=ERROR"
        )

        returnToCopilot(
            partial = true,
            success = false
        )
    }

    private fun returnToCopilot(
        partial: Boolean,
        success: Boolean = true
    ) {
        if (success) {
            SpxSessionState.update(
                SpxState.RETURNING_TO_COPILOT,
                "Voltando ao RouteCopilot..."
            )
        }

        val intent =
            Intent(
                applicationContext,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

                putExtra(
                    "OPEN_ROUTE_MANAGEMENT",
                    success
                )

                putExtra(
                    "IMPORT_PARTIAL",
                    partial
                )
            }

        fun launchCopilot() {
            runCatching {
                startActivity(
                    intent
                )
            }.onSuccess {
                Log.d(
                    TAG,
                    "RETURN=COPILOT"
                )

                if (success) {
                    SpxSessionState
                        .markReady(
                            partial
                        )
                }
            }.onFailure {
                Log.e(
                    TAG,
                    "RETURN=FAILED",
                    it
                )
            }
        }

        launchCopilot()

        /*
         * Fallback para Samsung/Android que mantém o SPX
         * no topo. Não confirma nada no SPX: apenas vai
         * à Home e reabre o RouteCopilot.
         */
        handler.postDelayed(
            {
                val stillInSpx =
                    rootInActiveWindow
                        ?.packageName
                        ?.toString() ==
                        SPX_PACKAGE

                if (stillInSpx) {
                    Log.d(
                        TAG,
                        "RETURN=FALLBACK_HOME"
                    )

                    performGlobalAction(
                        GLOBAL_ACTION_HOME
                    )

                    handler.postDelayed(
                        {
                            launchCopilot()
                        },
                        350L
                    )
                }
            },
            1_000L
        )
    }

    private fun maskAt(
        at: String
    ): String {
        if (
            at.length <= 6
        ) {
            return at
        }

        return "${at.take(4)}…${at.takeLast(4)}"
    }

    private fun schedule(
        delay: Long =
            SCAN_DELAY_MS
    ) {
        handler.removeCallbacks(
            scanRunnable
        )

        handler.postDelayed(
            scanRunnable,
            delay
        )
    }

    override fun onInterrupt() {
        Log.d(
            TAG,
            "SERVICO=INTERROMPIDO"
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(
            scanRunnable
        )

        super.onDestroy()
    }
}
