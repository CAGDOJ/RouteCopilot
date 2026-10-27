package com.routecopilot.spx

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

class SpxNavigator(
    private val service: AccessibilityService,
    private val handler: Handler
) {

    private val lastAction =
        mutableMapOf<String, Long>()

    fun reset() {
        lastAction.clear()
    }

    fun openDeliveries(
        root: AccessibilityNodeInfo
    ): Boolean =
        clickFirst(
            key = "deliveries",
            root = root,
            exactTexts =
                listOf(
                    "Entrega",
                    "Entregas"
                )
        )

    fun openInRoute(
        root: AccessibilityNodeInfo
    ): Boolean =
        clickStartsWith(
            key = "in_route",
            root = root,
            prefix = "Em Rota"
        )

    fun openScanner(
        root: AccessibilityNodeInfo
    ): Boolean =
        clickFirst(
            key = "scanner",
            root = root,
            exactTexts =
                listOf(
                    "Escanear",
                    "Ler pedido",
                    "Scanner"
                )
        )

    fun scrollForward(
        root: AccessibilityNodeInfo
    ): Boolean {
        val scrollables =
            mutableListOf<AccessibilityNodeInfo>()

        collectScrollables(
            root,
            scrollables
        )

        val ordered =
            scrollables
                .sortedByDescending {
                    val rect =
                        Rect()

                    it.getBoundsInScreen(
                        rect
                    )

                    rect.height()
                }

        for (node in ordered) {
            val ok =
                runCatching {
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    )
                }.getOrDefault(
                    false
                )

            if (ok) {
                return true
            }
        }

        return false
    }

    fun swipeUp(): Boolean {
        val now =
            SystemClock.elapsedRealtime()

        val last =
            lastAction["swipe"]
                ?: 0L

        if (
            now - last <
            850L
        ) {
            return false
        }

        lastAction["swipe"] =
            now

        val metrics =
            service.resources.displayMetrics

        val x =
            metrics.widthPixels *
                0.50f

        val startY =
            metrics.heightPixels *
                0.78f

        val endY =
            metrics.heightPixels *
                0.28f

        val path =
            Path().apply {
                moveTo(
                    x,
                    startY
                )

                lineTo(
                    x,
                    endY
                )
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription
                        .StrokeDescription(
                            path,
                            0L,
                            450L
                        )
                )
                .build()

        return runCatching {
            service.dispatchGesture(
                gesture,
                null,
                handler
            )
        }.getOrDefault(
            false
        )
    }

    private fun clickFirst(
        key: String,
        root: AccessibilityNodeInfo,
        exactTexts: List<String>
    ): Boolean {
        if (!canAct(key)) {
            return false
        }

        exactTexts.forEach { wanted ->
            val node =
                findClickable(
                    root
                ) { value ->
                    value.equals(
                        wanted,
                        ignoreCase = true
                    )
                }

            if (node != null) {
                val clicked =
                    runCatching {
                        node.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    }.getOrDefault(
                        false
                    )

                if (clicked) {
                    markAction(key)
                    return true
                }
            }
        }

        return false
    }

    private fun clickStartsWith(
        key: String,
        root: AccessibilityNodeInfo,
        prefix: String
    ): Boolean {
        if (!canAct(key)) {
            return false
        }

        val node =
            findClickable(
                root
            ) { value ->
                value.startsWith(
                    prefix,
                    ignoreCase = true
                )
            }
                ?: return false

        val clicked =
            runCatching {
                node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }.getOrDefault(
                false
            )

        if (clicked) {
            markAction(key)
        }

        return clicked
    }

    private fun findClickable(
        node: AccessibilityNodeInfo?,
        predicate: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) {
            return null
        }

        if (!node.isPassword) {
            val values =
                listOfNotNull(
                    node.text
                        ?.toString()
                        ?.trim(),
                    node.contentDescription
                        ?.toString()
                        ?.trim()
                )

            if (
                values.any(
                    predicate
                )
            ) {
                clickableSelfOrParent(
                    node
                )?.let {
                    return it
                }
            }
        }

        for (
            i in 0 until node.childCount
        ) {
            findClickable(
                node.getChild(i),
                predicate
            )?.let {
                return it
            }
        }

        return null
    }

    private fun clickableSelfOrParent(
        original: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var node:
            AccessibilityNodeInfo? =
            original

        repeat(7) {
            if (
                node?.isClickable ==
                true
            ) {
                return node
            }

            node =
                node?.parent
        }

        return null
    }

    private fun collectScrollables(
        node: AccessibilityNodeInfo?,
        output: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) {
            return
        }

        if (node.isScrollable) {
            output += node
        }

        for (
            i in 0 until node.childCount
        ) {
            collectScrollables(
                node.getChild(i),
                output
            )
        }
    }

    private fun canAct(
        key: String
    ): Boolean {
        val now =
            SystemClock.elapsedRealtime()

        return now -
            (
                lastAction[key]
                    ?: 0L
                ) >=
            1_500L
    }

    private fun markAction(
        key: String
    ) {
        lastAction[key] =
            SystemClock.elapsedRealtime()
    }
}
