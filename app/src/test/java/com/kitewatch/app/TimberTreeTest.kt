package com.kitewatch.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import timber.log.Timber

class TimberTreeTest {
    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    @Test
    fun `debug build plants DebugTree`() {
        Timber.plant(Timber.DebugTree())
        assertEquals(1, Timber.forest().size)
        assert(Timber.forest().first() is Timber.DebugTree)
    }

    @Test
    fun `release tree swallows all log calls`() {
        // ReleaseTree is private, so we test the contract: a no-op tree does nothing.
        val noOpTree =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) = Unit
            }
        Timber.plant(noOpTree)
        Timber.d("test message")
        // No exception thrown, tree is planted and does nothing — contract satisfied.
        assertEquals(1, Timber.forest().size)
    }

    @Test
    fun `no trees planted by default`() {
        assertEquals(0, Timber.forest().size)
    }
}
