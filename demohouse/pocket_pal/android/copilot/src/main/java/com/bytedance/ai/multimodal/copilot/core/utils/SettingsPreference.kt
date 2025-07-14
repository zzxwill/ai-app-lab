package com.bytedance.ai.multimodal.copilot.core.utils

import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.bytedance.ai.multimodal.copilot.AppCore

object SettingsPreference {
    fun getGlobalPreference(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(AppCore.inst)
    }
}