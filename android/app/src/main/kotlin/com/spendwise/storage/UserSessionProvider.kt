package com.spendwise.storage

/** Narrow read seam over `DeviceSessionStore` so callers (and their tests) don't need a real `Context`. */
interface UserSessionProvider {
    fun getUserId(): String?
}
