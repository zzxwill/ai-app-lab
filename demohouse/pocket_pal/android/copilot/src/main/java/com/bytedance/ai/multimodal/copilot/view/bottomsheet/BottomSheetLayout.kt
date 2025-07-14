package com.bytedance.ai.multimodal.copilot.view.bottomsheet

import android.animation.Animator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.bytedance.ai.multimodal.common.log.FLogger
import kotlin.apply
import kotlin.math.abs
import kotlin.ranges.coerceIn

class BottomSheetLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var viewHeight = 0
    private var parentHeight = 0
    private var initialY = 0f
    private var lastY = 0f

    private val halfExpandedRatio = 0.5f  // 半展开状态，显示50%
    private val expandedRatio = 0.9f      // 完全展开状态，显示90%

    private var currentState = State.EXPANDED
    private val velocityTracker = VelocityTracker.obtain()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    enum class State {
        COLLAPSED,  // 新增折叠状态
        HALF_EXPANDED,
        EXPANDED
    }

    val Int.dp: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        )
    init {
        // 设置布局背景和圆角
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadii = floatArrayOf(
                16.dp.toFloat(),
                16.dp.toFloat(),
                16.dp.toFloat(),
                16.dp.toFloat(),
                0f,
                0f,
                0f,
                0f
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewHeight = h
        parentHeight = (parent as View).height
        // 初始化为半展开状态
        translationY = parentHeight - (parentHeight * expandedRatio)
        FLogger.i("BottomSheetLayout", "onSizeChanged parentHeight=$parentHeight ")
        heightChangedListener?.onHeightChanged(parentHeight - translationY)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.rawY
                lastY = translationY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - initialY
                val newTranslationY = lastY + dy

                // 修改移动范围，允许完全折叠
                translationY = newTranslationY.coerceIn(
                    parentHeight * (1 - expandedRatio),
                    parentHeight.toFloat()
                )
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                FLogger.i("BottomSheetLayout", "touch ${event.action}")
                velocityTracker.computeCurrentVelocity(1000)
                val velocityY = velocityTracker.yVelocity

                // 根据速度和位置决定最终状态
                settleToState(velocityY)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun settleToState(velocityY: Float) {
        FLogger.i("BottomSheetLayout", "settleToState")
        val currentRatio = 1 - (translationY / parentHeight)

        val targetState = when {
            abs(velocityY) > 1000 -> {
                if (velocityY > 0) findLowerState(currentState)
                else findUpperState(currentState)
            }
            currentRatio < 0.25f -> State.COLLAPSED  // 展开比例小于25%时折叠
            currentRatio < halfExpandedRatio -> State.HALF_EXPANDED
            abs(currentRatio - halfExpandedRatio) < abs(currentRatio - expandedRatio) -> State.HALF_EXPANDED
            else -> State.EXPANDED
        }

        animateToState(targetState)
    }

    private fun animateToState(state: State) {
        val targetTranslationY = when (state) {
            State.COLLAPSED -> parentHeight.toFloat()
            State.HALF_EXPANDED -> parentHeight * (1 - halfExpandedRatio)
            State.EXPANDED -> parentHeight * (1 - expandedRatio)
        }

        ObjectAnimator.ofFloat(this, "translationY", translationY, targetTranslationY).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    heightChangedListener?.onHeightChanged(parentHeight - translationY)
                    stateChangedListener?.onStateChanged(state)
                    if (state == State.COLLAPSED) {
                        stateChangedListener?.onCollapsed()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }

        currentState = state
    }

    private fun findLowerState(state: State): State = when (state) {
        State.EXPANDED -> State.HALF_EXPANDED
        State.HALF_EXPANDED -> State.COLLAPSED
        State.COLLAPSED -> State.COLLAPSED
    }

    private fun findUpperState(state: State): State = when (state) {
        State.COLLAPSED -> State.HALF_EXPANDED
        State.HALF_EXPANDED -> State.EXPANDED
        State.EXPANDED -> State.EXPANDED
    }

    interface OnHeightChangedListener {
        fun onHeightChanged(newHeight: Float)
    }

    private var heightChangedListener: OnHeightChangedListener? = null

    fun setOnHeightChangedListener(listener: OnHeightChangedListener) {
        heightChangedListener = listener
    }

    interface OnStateChangedListener {
        fun onStateChanged(newState: State)
        fun onCollapsed()  // 新增折叠回调
    }

    private var stateChangedListener: OnStateChangedListener? = null

    fun setOnStateChangedListener(listener: OnStateChangedListener) {
        stateChangedListener = listener
    }

}