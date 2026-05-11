package com.chatflow.app

import android.app.Application
import com.chatflow.app.data.AppContainer

class ChatFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
