package com.dinyairsadot.clearledger.core.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny, dedicated flag used only to bridge the ~1 second "applying language" protection
 * window across the Activity recreation triggered by a language change.
 *
 * This is intentionally separate from [LanguagePreferenceManager]: it does not store any
 * language data, it only marks "a language change was just triggered, so the next screen
 * should show the blocking overlay for a moment". The flag is consumed (read once, then
 * cleared) as soon as the recreated Language Settings screen checks it, so it never leaks
 * into unrelated future recreations (e.g. rotation) of the Activity.
 */
class LanguageChangeProtectionManager(context: Context) {
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "language_change_protection_prefs"
        private const val KEY_PENDING = "pending_language_change_protection"
    }

    /**
     * Mark that a language change was just triggered. Uses [SharedPreferences.Editor.commit]
     * (synchronous) so the flag is guaranteed to be persisted before `Activity.recreate()` tears
     * down the current process state.
     */
    fun markPending() {
        sharedPrefs.edit().putBoolean(KEY_PENDING, true).commit()
    }

    /**
     * Read-and-clear the pending flag. Returns true at most once per [markPending] call.
     */
    fun consumePending(): Boolean {
        val wasPending = sharedPrefs.getBoolean(KEY_PENDING, false)
        if (wasPending) {
            sharedPrefs.edit().putBoolean(KEY_PENDING, false).commit()
        }
        return wasPending
    }
}
