package com.bytedance.ai.multimodal.copilot

import android.content.Context
import android.preference.PreferenceManager
import androidx.annotation.StringRes

/** Utility class to retrieve shared preferences. */
object PreferenceUtils {

    private fun getIntPref(context: Context, @StringRes prefKeyId: Int, defaultValue: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(prefKeyId)
        return sharedPreferences.getInt(prefKey, defaultValue)
    }

    fun getStringPref(context: Context, @StringRes prefKeyId: Int, defaultValue: String): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(prefKeyId)
        return sharedPreferences.getString(prefKey, defaultValue)
    }

    fun saveStringPreference(context: Context, @StringRes prefKeyId: Int, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(prefKeyId), value)
            .apply()
    }

    fun getMultimodalWebUrl(context: Context, data: String): String {
        val multimodalWebUrl = getStringPref(
            context,
            R.string.pref_key_url,
            ""
        ) ?: ""
        return multimodalWebUrl
    }

    fun getConfirmationTimeMs(context: Context): Int = 1000

    private fun getBooleanPref(
        context: Context,
        @StringRes prefKeyId: Int,
        defaultValue: Boolean
    ): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(prefKeyId), defaultValue)
}