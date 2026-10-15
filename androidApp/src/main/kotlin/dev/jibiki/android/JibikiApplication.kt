package dev.jibiki.android

import android.app.Application
import dev.jibiki.app.AppGraph
import kotlinx.coroutines.MainScope

class JibikiApplication : Application() {
    lateinit var platform: AndroidPlatform
        private set
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatform(this)
        graph = AppGraph(platform, MainScope())
    }
}
