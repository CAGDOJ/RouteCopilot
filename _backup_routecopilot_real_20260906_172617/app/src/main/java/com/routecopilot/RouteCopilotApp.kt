package com.routecopilot

import android.app.Application
import com.routecopilot.spx.SpxSessionState

class RouteCopilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SpxSessionState.initialize(this)
    }
}