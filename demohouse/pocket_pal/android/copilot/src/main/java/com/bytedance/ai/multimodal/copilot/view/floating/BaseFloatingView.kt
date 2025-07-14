package com.bytedance.ai.multimodal.copilot.view.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import com.bytedance.ai.multimodal.copilot.manager.FloatWindowManager
import kotlin.apply

abstract class BaseFloatingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 窗口管理器，用于管理窗口内所有的View
    protected val winManager: FloatWindowManager = FloatWindowManager(context)
    var isShowing = false

    // 是否允许点击外部区域关闭，默认为 true
    protected open var dismissOnTouchOutside: Boolean = true

    open fun dismiss() {
        isShowing = false
        winManager.removeView(this)
    }

    open fun show() {
        isShowing = true
        val layoutParams = createDefaultLayoutParams()
        winManager.addView(this, layoutParams)
    }

    protected fun createDefaultLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSPARENT
            flags = getWindowFlags()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            } else {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }

            // 设置位置为左上角，确保从(0,0)开始
            gravity = Gravity.START or Gravity.TOP

            // 获取屏幕实际尺寸
            val displayMetrics = DisplayMetrics()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            // 设置具体的像素值而不是MATCH_PARENT
            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels - getStatusBarHeight(context)

            // 设置x、y为0确保从屏幕边缘开始
            x = 0
            y = 0
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    protected open fun getWindowFlags(): Int {
        // 基础 flags
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        // 根据 dismissOnTouchOutside 设置来决定是否添加 FLAG_NOT_TOUCH_MODAL
        if (!dismissOnTouchOutside) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        return flags
    }

}