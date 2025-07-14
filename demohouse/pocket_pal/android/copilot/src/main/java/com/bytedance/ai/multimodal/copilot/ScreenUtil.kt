package com.bytedance.ai.multimodal.copilot

import android.annotation.SuppressLint
import com.bytedance.ai.multimodal.common.MultimodalKit

@SuppressLint("InternalInsetResource")
object ScreenUtil {
    private var navBarHeightResId: Int = -1
    private var statusBarHeightResId: Int = -1
    private var statusBarHeight: Int = -1
    private var navBarHeight: Int = -1


    fun getStatusBarHeight(): Int {
        if (statusBarHeight > -1) return statusBarHeight
        statusBarHeight = if (getStatusBarResId() > 0) {
            MultimodalKit.applicationContext.resources.getDimensionPixelSize(getStatusBarResId())
        } else -1
        return statusBarHeight
    }

    fun getNavBarHeight(): Int {
        if (navBarHeight > -1) return navBarHeight
        navBarHeight = if (getNavBarResId() > 0) {
            MultimodalKit.applicationContext.resources.getDimensionPixelSize(getNavBarResId())
        } else -1
        return navBarHeight
    }

    @SuppressLint("DiscouragedApi")
    private fun getNavBarResId(): Int {
        if (navBarHeightResId > 0) return navBarHeightResId
        navBarHeightResId =
            MultimodalKit.applicationContext.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return navBarHeightResId
    }

    @SuppressLint("DiscouragedApi")
    private fun getStatusBarResId(): Int {
        if (statusBarHeightResId > 0) return statusBarHeightResId
        statusBarHeightResId =
            MultimodalKit.applicationContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        return statusBarHeightResId
    }
}