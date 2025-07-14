package com.bytedance.ai.multimodal.copilot.manager

object MoveSource {
    const val FINGER = 0x00000000 // 手指移动
    const val ANIMATE_MASK = 0x00000001 // 动画移动
    const val MOVE_SHOW_ANIMATE = 0x00000011 // 移动显示动画
    const val TOUCH_FINISH_ANIMATE = 0x00000101 // 松手吸附动画
    const val MAXIMIZE_ANIMATE = 0x00001001 // 悬浮窗放大化动画
    const val RESET_ANIMATE = 0x00001001 // 重置动画
}