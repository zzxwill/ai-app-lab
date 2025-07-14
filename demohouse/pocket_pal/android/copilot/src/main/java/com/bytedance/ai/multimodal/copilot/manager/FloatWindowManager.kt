package com.bytedance.ai.multimodal.copilot.manager

import android.content.Context
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bytedance.ai.multimodal.common.log.FLogger
import kotlin.apply
import kotlin.collections.find
import kotlin.jvm.java
import kotlin.onFailure

class FloatWindowManager(context: Context):ViewManager {
    companion object {
        const val TAG = "FloatWindowManager"
    }

    private val views = mutableSetOf<Pair<View, ImageView>>()


    // handler: 处理悬浮窗展示
    private val windowManager: WindowManager?

    init {
        windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)
    }


    fun addView(view: View, x:Int, y:Int, gravity: Int) {
        val windowParam = SingleFloatBall.createBaseParams().apply {
            this.width = WindowManager.LayoutParams.WRAP_CONTENT
            this.height = WindowManager.LayoutParams.WRAP_CONTENT
            this.x = x
            this.y = y
            this.gravity = gravity
        }
        windowManager?.addView(view, windowParam)
    }

    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        runCatching {
            if (params !is WindowManager.LayoutParams) throw IllegalArgumentException("Params must be WindowManager.LayoutParams")
            windowManager?.addView(view, params)
        }.onFailure {
            FLogger.e(TAG, "addView throw exception:${it}")
        }
    }


    override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
        runCatching {
            if (params !is WindowManager.LayoutParams) throw IllegalArgumentException("Params must be WindowManager.LayoutParams")
            view.layoutParams = params.toFrameLayoutParams()
            //FLogger.d(SingleFloatBall.TAG, "====updateViewLayout======x: ${params.x}, y: ${params.y}")
            windowManager?.updateViewLayout(view, params)
        }.onFailure {
            FLogger.e(TAG, "updateViewLayout throw exception:${it}")
        }
    }

    override fun removeView(view: View) {
        runCatching {
            windowManager?.removeView(view)
        }.onFailure {
            FLogger.e(TAG, "removeView throw exception:${it}")
        }
    }

    fun setAlpha(view: View, alpha: Float) {
        val pair = views.find { it.first == view } ?: return
        pair.second.alpha = alpha
    }

    private fun WindowManager.LayoutParams.toFrameLayoutParams(offset: Int = 0): FrameLayout.LayoutParams {
        val dup = FrameLayout.LayoutParams(width + 2 * offset, height + 2 * offset)
        dup.gravity = gravity
        if (gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT) {
            dup.rightMargin = x - offset
        } else {
            dup.leftMargin = x - offset
        }
        dup.topMargin = y - offset
        return dup
    }

}