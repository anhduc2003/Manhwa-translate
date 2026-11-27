package com.manhwatranslate

import android.app.Application
import com.manhwatranslate.di.AppContainer

class ManhwaTranslateApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
