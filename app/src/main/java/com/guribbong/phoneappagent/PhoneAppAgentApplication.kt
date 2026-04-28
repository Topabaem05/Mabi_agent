package com.guribbong.phoneappagent

import android.app.Application
import com.guribbong.phoneappagent.di.appModule
import com.guribbong.phoneappagent.data.history.dataHistoryModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PhoneAppAgentApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@PhoneAppAgentApplication)
            modules(dataHistoryModule, appModule)
        }
    }
}

