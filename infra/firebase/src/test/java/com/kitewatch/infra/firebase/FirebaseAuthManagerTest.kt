package com.kitewatch.infra.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirebaseAuthManagerTest {
    private lateinit var auth: FirebaseAuth
    private lateinit var manager: FirebaseAuthManager

    @Before
    fun setUp() {
        auth = mockk(relaxed = true)
        every { auth.currentUser } returns null
        // Capture and immediately invoke the listener so the StateFlow initialises
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        every { auth.addAuthStateListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onAuthStateChanged(auth)
        }
        manager = FirebaseAuthManager(auth)
    }

    @Test
    fun `authState emits null when currentUser is null`() {
        assertNull(manager.authState.value)
    }

    @Test
    fun `authState emits user when currentUser is non-null`() {
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        every { auth.addAuthStateListener(capture(listenerSlot)) } answers {
            listenerSlot.captured.onAuthStateChanged(auth)
        }
        val mgr = FirebaseAuthManager(auth)
        assertTrue(mgr.authState.value === user)
    }

    @Test
    fun `getIdToken returns TokenExpiredException when unauthenticated`() =
        runTest {
            every { auth.currentUser } returns null
            val result = manager.getIdToken()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is TokenExpiredException)
        }

    @Test
    fun `signOut calls FirebaseAuth signOut`() {
        manager.signOut()
        verify { auth.signOut() }
    }
}
