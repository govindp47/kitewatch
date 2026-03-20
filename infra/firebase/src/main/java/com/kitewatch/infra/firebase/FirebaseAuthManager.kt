package com.kitewatch.infra.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager
    @Inject
    constructor(
        private val auth: FirebaseAuth,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val authState: StateFlow<FirebaseUser?> =
            callbackFlow {
                val listener =
                    FirebaseAuth.AuthStateListener { firebaseAuth ->
                        trySend(firebaseAuth.currentUser)
                    }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = auth.currentUser,
            )

        suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> =
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                checkNotNull(result.user) { "FirebaseAuth returned null user after sign-in" }
            }

        suspend fun getIdToken(): Result<String> =
            runCatching {
                val user =
                    auth.currentUser
                        ?: throw TokenExpiredException("No authenticated Firebase user")
                val tokenResult = user.getIdToken(false).await()
                checkNotNull(tokenResult?.token?.takeIf { it.isNotEmpty() }) {
                    "Firebase returned null or empty ID token"
                }
            }

        fun signOut() {
            auth.signOut()
        }
    }

class TokenExpiredException(
    message: String,
) : Exception(message)
