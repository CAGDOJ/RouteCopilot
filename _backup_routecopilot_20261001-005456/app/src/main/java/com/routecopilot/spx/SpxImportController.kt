package com.routecopilot.spx

import android.os.SystemClock

class SpxImportController {

    companion object {
        const val MAX_SYNC_MS =
            180_000L

        private const val MIN_NO_NEW_MS =
            14_000L

        private const val SAME_PAGE_LIMIT =
            10

        private const val NATIVE_END_LIMIT =
            3
    }

    var observedAt: String? =
        null
        private set

    private var startedAt =
        0L

    private var lastNewAt =
        0L

    private var lastFingerprint =
        ""

    private var samePagePasses =
        0

    private var nativeEndSignals =
        0

    fun reset() {
        val now =
            SystemClock.elapsedRealtime()

        startedAt =
            now

        lastNewAt =
            now

        lastFingerprint =
            ""

        samePagePasses =
            0

        nativeEndSignals =
            0

        observedAt =
            null
    }

    fun observeAt(
        at: String
    ): Boolean {
        val normalized =
            at.uppercase()

        val changed =
            observedAt != null &&
                observedAt != normalized

        observedAt =
            normalized

        if (changed) {
            val now =
                SystemClock.elapsedRealtime()

            lastNewAt =
                now

            lastFingerprint =
                ""

            samePagePasses =
                0

            nativeEndSignals =
                0
        }

        return changed
    }

    fun observeVisibleCodes(
        codes: Set<String>,
        newCodes: Int
    ) {
        if (codes.isEmpty()) return

        val fingerprint =
            codes
                .sorted()
                .joinToString("|")

        if (newCodes > 0) {
            lastNewAt =
                SystemClock.elapsedRealtime()

            samePagePasses =
                0

            nativeEndSignals =
                0
        } else {
            if (
                fingerprint ==
                lastFingerprint
            ) {
                samePagePasses++
            } else {
                samePagePasses =
                    0
            }
        }

        lastFingerprint =
            fingerprint
    }

    fun noteNativeScrollResult(
        moved: Boolean
    ) {
        if (moved) {
            nativeEndSignals =
                0
        } else {
            nativeEndSignals++
        }
    }

    fun shouldFinishWithoutExpectedTotal(
        packageCount: Int
    ): Boolean {
        if (packageCount <= 0) return false

        val noNewFor =
            SystemClock.elapsedRealtime() -
                lastNewAt

        return noNewFor >=
            MIN_NO_NEW_MS &&
            samePagePasses >=
            SAME_PAGE_LIMIT &&
            nativeEndSignals >=
            NATIVE_END_LIMIT
    }

    fun timedOut(): Boolean =
        startedAt != 0L &&
            SystemClock.elapsedRealtime() -
            startedAt >=
            MAX_SYNC_MS
}
