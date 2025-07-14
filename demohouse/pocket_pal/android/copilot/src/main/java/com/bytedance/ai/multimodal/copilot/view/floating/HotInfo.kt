package com.bytedance.ai.multimodal.copilot.view.floating

/**
 * 热区信息，全屏幕包括状态栏、导航栏为热区
 */
data class HotInfo(
    var horizontalPadding: Int = 0,
    var topMargin: Int = 0,
    var bottomMargin: Int = 0
)