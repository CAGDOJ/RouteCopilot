package com.routecopilot.spx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxStatus {
    CHECKING,
    CONNECTED,
    AUTH_REQUIRED,
    UNAVAILABLE
}

object SpxSessionState {
    private val _status = MutableStateFlow(SpxStatus.CHECKING)
    val status: StateFlow<SpxStatus> = _status.asStateFlow()

    fun update(status: SpxStatus) {
        _status.value = status
    }
}
