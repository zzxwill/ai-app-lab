package com.bytedance.ai.multimodal.copilot.manager

import android.content.Context
import android.view.View
import android.view.ViewGroup

class FloatWindowController(context: Context) {

    // 窗口内的某一个视图
    private var handler: SingleFloatWindowHandler

    // 窗口管理器，用于管理窗口内所有的View
    private val winManager: FloatWindowManager = FloatWindowManager(context)

    init {
        handler = SingleFloatWindowHandler(context, winManager)
    }

    fun updateView(view: View, width: Int, height: Int) {
        handler.updateView(view, width, height)
    }

    fun removeView(view: View) {
        handler.removeView(view)
    }

    fun showFloatView(view: View, params: ViewGroup.LayoutParams) {
        winManager.addView(view, params)
    }

    fun removeFloatView(view: View) {
        winManager.removeView(view)
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        handler.setOnClickListener(listener)
    }
}