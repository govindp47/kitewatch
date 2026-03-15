package com.kitewatch.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.kitewatch.app.logging.SanitisedDebugTree
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.infra.auth.AppLockStateManager
import com.kitewatch.infra.worker.WorkSchedulerRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class KiteWatchApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var appLockStateManager: AppLockStateManager

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var workSchedulerRepository: WorkSchedulerRepository

    @Inject
    lateinit var accountBindingRepository: AccountBindingRepository

    @Inject
    lateinit var sessionManager: com.kitewatch.infra.auth.SessionManager

    /** Application-scoped coroutine scope for fire-and-forget suspend calls in onCreate. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        // --- START MODIFICATION ---
        if (BuildConfig.DEBUG) {
            // SanitisedDebugTree scrubs sensitive key=value patterns before logcat output.
            Timber.plant(SanitisedDebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        // --- END MODIFICATION ---
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockStateManager)
        applicationScope.launch { sessionManager.observe() }
        scheduleWorkersIfBound()
    }

    private fun scheduleWorkersIfBound() {
        applicationScope.launch {
            if (accountBindingRepository.isBound()) {
                workSchedulerRepository.scheduleOrderSync()
                workSchedulerRepository.scheduleChargeRateSync()
            }
        }
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
