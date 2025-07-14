package com.bytedance.ai.multimodal.copilot.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.copilot.R
import com.bytedance.ai.multimodal.copilot.core.utils.dp2px
import com.bytedance.ai.multimodal.copilot.databinding.BottomSheetContainerBinding
import com.bytedance.ai.multimodal.copilot.view.bottomsheet.BottomSheetLayout
import com.bytedance.ai.multimodal.copilot.view.floating.BaseFloatingView
import com.bytedance.ai.multimodal.copilot.view.floating.FloatViewManager
import com.bytedance.ai.multimodal.copilot.view.floating.ShowListener
import com.bytedance.ai.multimodal.copilot.PreferenceUtils
import com.bytedance.ai.multimodal.tts.api.TTSService

class BottomSheetWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseFloatingView(context, attrs, defStyleAttr) {
    private val binding: BottomSheetContainerBinding =
        BottomSheetContainerBinding.inflate(LayoutInflater.from(context), this, true)

    // 添加触摸事件相关变量
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var widgetWidth: Int = 0
    private var widgetView: View? = null

    private val transparentBackground = View(context).apply {
        setBackgroundColor(Color.argb(76, 0, 0, 0))
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        setOnClickListener {
            dismiss()
        }
        // 确保背景 View 在最底层
        elevation = -1f
    }

    init {
        // 添加透明背景 View 作为第一个子 View
        addView(transparentBackground, 0)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        widgetWidth = screenWidth

        FLogger.i("BottomSheet", "init parent=$parent height=$height")

        // Set up the height change listener
        binding.bottomSheetLayout.run {
            setOnHeightChangedListener(object :
                BottomSheetLayout.OnHeightChangedListener {
                override fun onHeightChanged(newHeight: Float) {
                    FLogger.i("BottomSheet", "onHeightChanged newHeight=$newHeight")
                    binding.webPage.layoutParams =
                        binding.webPage.layoutParams.apply {
                            height = newHeight.toInt() - 28.dp2px() // minus bar height
                        }
                }
            })

            setOnStateChangedListener(object : BottomSheetLayout.OnStateChangedListener {
                override fun onStateChanged(newState: BottomSheetLayout.State) {
                    // 处理状态变化
                }

                override fun onCollapsed() {
                    // 处理折叠事件，例如移除视图
                    dismiss()
                }
            })
        }

        setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        // 监听返回键，移除 View
                        dismiss()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        isFocusable = true // 确保 View 可以接收按键事件
        isFocusableInTouchMode = true
    }

    override fun getWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    fun show(data: String, showListener: ShowListener? = null) {
        FLogger.d("FloatWidget", "======showFloatWidget=========")

        binding.webPage.loadUrl(PreferenceUtils.getMultimodalWebUrl(context, data))

        show()
        val animation =
            AnimationUtils.loadAnimation(context, R.anim.animation_float_widget_in_up) as AnimationSet
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                FLogger.d("FloatWidget", "======showFloatWidget finish=========")
                showListener?.invoke()
            }

            override fun onAnimationRepeat(animation: Animation?) {
            }
        })

        binding.root.startAnimation(animation)
        FloatViewManager.hideSpeakerStatus()
        FloatViewManager.dismissSpeakerStatus()
    }

    override fun dismiss() {
        if (!isShowing) return

        val animation = AnimationUtils.loadAnimation(context, R.anim.animation_float_widget_out_down) as AnimationSet
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                // 动画结束后执行原来的 dismiss 逻辑
                isShowing = false
                super@BottomSheetWidgetView.dismiss()
                // 关闭页面的时候停止tts
                TTSService.getInstance().stop()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        binding.root.startAnimation(animation)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun show() {
        isShowing = true
        val layoutParams = createDefaultLayoutParams()

        // 获取屏幕高度
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        // 定义到达底部的阈值，比如距离底部还有100像素就触发关闭
        val BOTTOM_THRESHOLD = 300

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    layoutParams.x = initialX + deltaX
                    layoutParams.y = initialY + deltaY

                    // 如果移动到接近屏幕底部，就关闭窗口
                    if (layoutParams.y + height > screenHeight - BOTTOM_THRESHOLD) {
                        dismiss()
                        return@setOnTouchListener true
                    }

                    winManager.updateViewLayout(this, layoutParams)
                    true
                }

                MotionEvent.ACTION_OUTSIDE -> {
                    dismiss()
                    true
                }

                else -> false
            }
        }
        (context.getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.let { vibrator ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
        winManager.addView(this, layoutParams)
        this.requestFocus()
    }

}