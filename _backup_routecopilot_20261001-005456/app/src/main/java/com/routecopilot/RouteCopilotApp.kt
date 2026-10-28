package com.routecopilot

import android.app.Application
import com.routecopilot.route.RouteRepository
import com.routecopilot.spx.SpxSessionState

class RouteCopilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RouteRepository.initialize(this)
        SpxSessionState.initialize(this)
    }
}
