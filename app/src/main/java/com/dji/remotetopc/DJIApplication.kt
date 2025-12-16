package com.dji.remotetopc

import android.app.Application

class DJIApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DJIApplication
            private set
    }
}
