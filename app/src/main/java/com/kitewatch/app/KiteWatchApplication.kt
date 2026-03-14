package com.kitewatch.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.kitewatch.infra.auth.AppLockStateManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class KiteWatchApplication : Application() {
    @Inject
    lateinit var appLockStateManager: AppLockStateManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockStateManager)
    }
}

/** No-op Timber tree for release builds — swallows all log calls. */
private class ReleaseTree : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) = Unit
}
