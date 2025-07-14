package com.bytedance.ai.multimodal.copilot.view.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.utils.ScreenUtils
import com.bytedance.ai.multimodal.copilot.R
import com.bytedance.ai.multimodal.copilot.ScreenUtil
import com.bytedance.ai.multimodal.copilot.core.utils.dp2px
import com.bytedance.ai.multimodal.copilot.databinding.FloatingWidgetBinding
import com.bytedance.ai.multimodal.copilot.manager.MutexFloatManager
import com.bytedance.ai.multimodal.copilot.manager.SingleFloatBall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.apply

typealias CanDismissListener = ()->Unit
typealias ShowListener = ()->Unit
class FloatWidget  @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: FloatingWidgetBinding = FloatingWidgetBinding.inflate(LayoutInflater.from(context))

    private var canMove = false
    private var mDownX = 0f
    private var mDownY = 0f
    private var parentWidth = 0
    private var parentHeight = 0
    private var widgetWidth: Int = 0
    private var isShowWidget = false

    private val touchSlopSquare: Int = try {
        val viewConfiguration = ViewConfiguration.get(context)
        viewConfiguration.scaledTouchSlop * viewConfiguration.scaledTouchSlop
    } catch (e: Exception) {
        // ViewConfiguration.get(context) 出现了一例异常
        576
    }

    init {
        val screenWidth = context.resources.displayMetrics.widthPixels
        setBackgroundResource(android.R.color.transparent)
        widgetWidth = (screenWidth * 0.9f).toInt()
        addView(binding.root, LayoutParams(widgetWidth, LayoutParams.WRAP_CONTENT))
    }

    fun show(packageId: String, appId: String, data: String, showListener: ShowListener? = null){
        if (isShowWidget) {
            dismissInternal{
                CoroutineScope(Dispatchers.Main).launch {
                    //加个延时，防止上一个页面还未彻底销毁，新页面上屏直接崩溃
                    delay(500)
                    realShow(packageId, appId, data, showListener)
                }
            }
        }else {
            realShow(packageId, appId, data, showListener)
        }
    }

    fun dismiss(dismissListener: CanDismissListener? = null){
        if (isShowWidget){
            dismissInternal(dismissListener)
        } else {
            dismissListener?.invoke()
        }
    }


    private fun dismissInternal(dismissListener: CanDismissListener? = null) {
        FLogger.d("FloatWidget", "====dismiss====")
        val animation = AnimationUtils.loadAnimation(context, R.anim.animation_float_widget_out) as AnimationSet
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                FLogger.d("FloatWidget", "====dismiss finish====")
                binding.webViewParent.removeAllViews()
                MutexFloatManager.inst()?.removeFloatView(this@FloatWidget)
                dismissListener?.invoke()
                isShowWidget = false
            }

            override fun onAnimationRepeat(animation: Animation?) {
            }
        })
        binding.root.startAnimation(animation)
    }

    private fun realShow(packageId: String, appId: String, data: String, showListener: ShowListener? = null){
        isShowWidget = true
        FLogger.d("FloatWidget", "======showFloatWidget=========")
        //TODO showWidget
        //createWidget(packageId, appId, binding.webViewParent, data)

        val layoutParams = WindowManager.LayoutParams().apply {
            this.format = PixelFormat.TRANSPARENT
            this.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                this.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            } else {
                this.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            gravity = Gravity.TOP or Gravity.CENTER
            x = 6.dp2px()
            y = 10.dp2px() + ScreenUtil.getStatusBarHeight()
            width = widgetWidth
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        MutexFloatManager.inst()?.showFloatView(this, layoutParams)
        val animation = AnimationUtils.loadAnimation(context, R.anim.animation_float_widget_in) as AnimationSet
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
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onDown(this, event)
                false
            }

            MotionEvent.ACTION_MOVE -> {
                if (canMove) {
                    return true
                }
                val diffX: Float = event.rawX - mDownX
                val diffY: Float = event.rawY - mDownY
                val square = diffX * diffX + diffY * diffY
                canMove = square > touchSlopSquare
                canMove
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            onDown(this, event)
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!canMove) {
                val diffX: Float = event.rawX - mDownX
                val diffY: Float = event.rawY - mDownY
                val square = diffX * diffX + diffY * diffY
                canMove = square > touchSlopSquare
                if (!canMove) {
                    return true
                }
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (canMove) {
                // 判断上滑超过指定距离（比如200像素）则隐藏悬浮窗
                if (mDownY - event.rawY > 200) {
                    this.dismiss(null)
                    return true
                }
            }
        }
        return true
    }


    private fun onDown(view: View, event: MotionEvent) {
        mDownX = event.rawX
        mDownY = event.rawY
        val parent = view.parent
        parentWidth = if (parent is ViewGroup) parent.width else ScreenUtils.getScreenWidth(view.context)
        parentHeight = if (parent is ViewGroup) parent.height else ScreenUtils.getScreenHeight(view.context)
        FLogger.d(SingleFloatBall.TAG, "parentWidth: $parentWidth, parentHeight: $parentHeight")
        canMove = false
    }

}