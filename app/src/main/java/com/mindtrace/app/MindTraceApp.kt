package com.mindtrace.app

import android.app.Application
import com.mindtrace.app.data.AppContainer
import com.mindtrace.app.work.NotificationHelper

class MindTraceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
    }
}
