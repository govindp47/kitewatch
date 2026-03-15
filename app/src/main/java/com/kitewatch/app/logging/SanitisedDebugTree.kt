package com.kitewatch.app.logging

import timber.log.Timber

/**
 * A [Timber.DebugTree] variant used in **debug builds only** that passes every log
 * message through [SensitiveDataFilter] before forwarding to logcat.
 *
 * This guards against accidental `Timber.d("token=$token")` calls introduced during
 * development. No performance-sensitive path should rely on Timber logging, so the
 * regex pass per log call is acceptable in debug builds.
 *
 * Release builds use [com.kitewatch.app.ReleaseTree] which no-ops all calls — this
 * tree must never be planted in release builds.
 */
internal class SanitisedDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        super.log(priority, tag, SensitiveDataFilter.sanitize(message), t)
    }
}
