package com.detox.app

import android.app.Application
import com.detox.app.data.RulesRepository

/**
 * Created by Android BEFORE anything else in our app (screens AND the bouncer service),
 * so this is the right place to open the database once.
 */
class DetoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RulesRepository.init(this)
    }
}
