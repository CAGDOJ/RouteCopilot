package com.routecopilot.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RouteDataStore {

    private val _route =
        MutableStateFlow<ImportedRoute?>(
            null
        )

    val route: StateFlow<ImportedRoute?> =
        _route.asStateFlow()

    fun setRoute(
        route: ImportedRoute
    ) {

        _route.value =
            route
    }

    fun clear() {

        _route.value =
            null
    }
}