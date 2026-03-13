package com.kitewatch.infra.auth

import com.kitewatch.domain.event.ReAuthRequiredEvent
import com.kitewatch.domain.event.SessionExpiredEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes [SessionExpiredEvent] from the network layer and coordinates the
 * local session tear-down:
 *   1. Clears the stored access token via [CredentialStore].
 *   2. Emits [ReAuthRequiredEvent] so the UI layer can navigate to re-auth.
 *
 * **Lifecycle**: call [observe] from an application-scoped coroutine (e.g. the
 * app's [kotlinx.coroutines.CoroutineScope]) at startup so that 401/403 responses
 * are handled regardless of which screen is active.
 *
 * ```kotlin
 * applicationScope.launch { sessionManager.observe() }
 * ```
 */
@Singleton
class SessionManager
    @Inject
    constructor(
        private val sessionExpiredFlow: MutableSharedFlow<SessionExpiredEvent>,
        private val credentialStore: CredentialStore,
    ) {
        private val _reAuthRequiredFlow = MutableSharedFlow<ReAuthRequiredEvent>(extraBufferCapacity = 1)

        /** Emits [ReAuthRequiredEvent] whenever a session-expired event is handled. */
        val reAuthRequiredFlow: SharedFlow<ReAuthRequiredEvent> = _reAuthRequiredFlow.asSharedFlow()

        /**
         * Suspends indefinitely, collecting [SessionExpiredEvent]s and handling each one.
         * Must be launched in a coroutine that outlives the session (typically app scope).
         */
        suspend fun observe() {
            sessionExpiredFlow.collect {
                credentialStore.clearAccessToken()
                _reAuthRequiredFlow.tryEmit(ReAuthRequiredEvent)
            }
        }
    }
