package com.bytedance.ai.multimodal.copilot.manager

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.core.animation.addListener
import com.bytedance.ai.multimodal.common.utils.ScreenUtils
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.utils.UnitUtils.dp2px
import kotlin.math.abs
import kotlin.math.max

class SingleFloatWindowHandler(
    private val context: Context,
    private val winManager: FloatWindowManager
) {

    // 目前只有一个悬浮球，后续可以在一个屏幕中添加多个View
    private val ball = createFloatBall()
    // 悬浮窗的状态
    private var state = SingleFloatWindowState.MAXIMIZE
    /**
     * 创建一个小悬浮窗，初始位置为屏幕的右部分中间位置
     */
    @SuppressLint("RtlHardcoded")
    private fun createFloatBall(): SingleFloatBall {
        return SingleFloatBall(context, this, winManager)
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        ball.onBallClickListener = listener
    }

    // 以下 方法 用于更新、删除悬浮窗中视图
    fun updateView(view: View, width: Int, height: Int) {
        ball.updateContentView(view, width, height)
    }

    fun removeView(view: View) {
        ball.removeContentView(view)
    }

    // 以下 属性&方法 用于悬浮窗出现、隐藏动画
    private val alphaShowAnimator =
        ObjectAnimator.ofFloat(ball, View.ALPHA, 0f, 1f).apply {
            duration = 150
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
        }

    private val alphaDismissAnimator =
        ObjectAnimator.ofFloat(ball, View.ALPHA, 1f, 0f).apply {
            duration = 150
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
        }

    private fun moveShowAnimator(): ValueAnimator {
        val params = ball.getParams()
        return ValueAnimator.ofFloat(-params.width.toFloat(), 0f).apply {
            duration = 150
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
            this.addUpdateListener {
                params.x = (it.animatedValue as Float).toInt()
                ball.updatePosition(params, MoveSource.MOVE_SHOW_ANIMATE)
            }
        }
    }


    fun alphaShowFloatWindow() {
        alphaDismissAnimator.end()
        ball.alpha = 0f
        ball.showBall()
        ball.post {
            alphaShowAnimator.start()
        }

    }

    fun alphaHideFloatWindow(onEnd: (() -> Unit)? = null) {
        alphaShowAnimator.end()
        ball.alpha = 1f
        alphaDismissAnimator.addListener(onEnd = {
            ball.hideBall()
            onEnd?.invoke()
        })
        alphaDismissAnimator.start()
    }


    fun moveShowFloatWindow() {
        val params = ball.getParams()
        alphaDismissAnimator.end()
        ball.alpha = 1f
        params.x = -params.width
        ball.showBall()
        ball.post {
            moveShowAnimator().start()
        }
    }

    // 以下 方法 用于处理悬浮窗触摸、移动时的展示效果
    internal fun onViewTouched() {

    }

    // 悬浮窗最小化展开
    private fun maximizeAnimator(): ValueAnimator {
        val params = ball.getParams()
        val hotInfo = ball.getHotInfo()
        return ValueAnimator.ofInt(params.x, hotInfo.horizontalPadding).apply {
            duration = 150
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
            this.addUpdateListener {
                params.x = (it.animatedValue as Int)
                ball.updatePosition(params, MoveSource.MAXIMIZE_ANIMATE)
            }
            this.addListener(onEnd = {
                state = SingleFloatWindowState.MAXIMIZE
            })
        }
    }

    private fun alphaShowMaskIconAnimator(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
            this.addUpdateListener {
                val alpha = (it.animatedValue as? Float) ?: 1f
                ball.alphaShowMaskIcon(alpha)
            }
        }
    }

    internal fun onViewMove(source: Int) {
        val params = ball.getParams()
        if (params.x < 0) {
            ball.dismissMaskIcon()
            val width = if (params.width == WindowManager.LayoutParams.WRAP_CONTENT) {
                ball.width
            } else {
                params.width
            }
            // 展示蒙层
            val ratio =
                (abs(params.x.toFloat()) / (width / 2)).coerceAtMost(1f)
            ball.alphaShowMask(ratio)
        } else {
            ball.dismissMask()
        }
    }

    internal fun onViewTouchFinished() {
        moveToEdgeOfScreen()
    }

    /**
     * 移动悬浮球至屏幕边缘
     */
    private fun moveToEdgeOfScreen() {
        val params = ball.getParams()
        val animatorSet = AnimatorSet()
        val hotInfo = ball.getHotInfo()
        val finalY = params.y.coerceAtLeast(hotInfo.topMargin).coerceAtMost(ScreenUtils.getScreenHeight(ball.context) - hotInfo.bottomMargin - params.height)
        val yAnimator = ValueAnimator.ofInt(params.y, finalY).apply {
            duration = 200
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
        }
        val width = if (params.width == WindowManager.LayoutParams.WRAP_CONTENT) {
            ball.width
        } else {
            params.width
        }
        FLogger.d(SingleFloatBall.TAG, "moveToEdgeOfScreen: ${params.x} ${params.y} ${width} ${params.height} ${hotInfo.horizontalPadding} ${hotInfo.topMargin} ${hotInfo.bottomMargin}")
        val xAnimator =
            if ((params.x < - width / 2) || (params.x < 0 && state == SingleFloatWindowState.MAXIMIZE)) {
                // 点击蒙层执行悬浮窗展开动效
                ball.setMaskClickListener {
                    maximizeAnimator().start()
                }
                // 悬浮窗最小化动画
                val targetX = -(width -  17.dp2px())
                FLogger.d(SingleFloatBall.TAG, "MINIMIZE EdgeX: ${targetX}")
                ValueAnimator.ofInt(params.x, targetX).apply {
                    duration = 150
                    interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
                    addListener(onEnd = {
                        state = SingleFloatWindowState.MINIMIZE
                    })
                }
            } else {
                FLogger.d(SingleFloatBall.TAG, "MAXIMIZE EdgeX: ${hotInfo.horizontalPadding}")
                // 悬浮窗吸附动画，吸附有个最小边距6dp
                ValueAnimator.ofInt(params.x, hotInfo.horizontalPadding).apply {
                    duration = 200
                    interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
                    addListener(onEnd = {
                        state = SingleFloatWindowState.MAXIMIZE
                    })
                }
            }

        val moveAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = max(xAnimator.duration, yAnimator.duration)
        }
        moveAnimator.addUpdateListener {
            params.x = (xAnimator.animatedValue as? Int) ?: params.x
            params.y = (yAnimator.animatedValue as? Int) ?: params.y
            ball.updatePosition(params, MoveSource.TOUCH_FINISH_ANIMATE)
        }
        moveAnimator.addListener(onEnd = {
            if(params.x < 0){
                alphaShowMaskIconAnimator().start()
            }
        })
        animatorSet.playTogether(xAnimator, yAnimator,moveAnimator)
        animatorSet.start()
    }
}