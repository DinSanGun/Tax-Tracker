package com.dinyairsadot.clearledger.core.data

import android.content.Context
import android.content.SharedPreferences
import com.dinyairsadot.clearledger.core.domain.AppTextSize

/**
 * Persists the user's app-wide text size preference (Normal/Large) in its own
 * SharedPreferences file, mirroring [LanguagePreferenceManager]'s pattern.
 *
 * This preference is UI-only: it is never stored in Room, never part of
 * invoice/category data, and is local to the device.
 */
class TextSizePreferenceManager(context: Context) {
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "text_size_prefs"
        private const val KEY_TEXT_SIZE = "selected_text_size"
    }

    /**
     * Get the current saved text size preference. Default is [AppTextSize.NORMAL]
     * when no preference is set, or if the stored value is unrecognized.
     */
    fun getTextSize(): AppTextSize {
        val raw = sharedPrefs.getString(KEY_TEXT_SIZE, AppTextSize.NORMAL.name)
        return runCatching { AppTextSize.valueOf(raw ?: AppTextSize.NORMAL.name) }
            .getOrDefault(AppTextSize.NORMAL)
    }

    /** Save the selected text size preference. */
    fun setTextSize(textSize: AppTextSize) {
        sharedPrefs.edit().putString(KEY_TEXT_SIZE, textSize.name).apply()
    }
}
