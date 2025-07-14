package com.bytedance.ai.multimodal.copilot.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.bytedance.ai.multimodal.copilot.core.utils.dp2px

class BreathingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rectAPaint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        alpha = (0.6 * 255).toInt() // 设置透明度为 0.6
        isAntiAlias = true
    }

    private val rectBPaint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.white)
    }

    private val rectA = RectF()
    private val rectB = RectF()

    private var blurA = 80f // 初始 Blur 值
    private var alphaA = 0.6f
    private var scaleA = 1f
    private var scaleAHeight = 1f

    private var blurB = 24f // 固定 Blur 值
    private var scaleB = 1f
    private var scaleBHeight = 1f
    private var alphaB = 1f

    init {
        // 启动动画
        startBreathingAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 设置 Rect A 的绘制区域
        val rectAWidth = 170.dp2px() * scaleA
        val rectAHeight = 38.dp2px() * scaleAHeight
        rectA.set(
            (width / 2f) - rectAWidth / 2f,
            (height / 2f) - rectAHeight / 2f,
            (width / 2f) + rectAWidth / 2f,
            (height / 2f) + rectAHeight / 2f
        )

        // 设置 Rect B 的绘制区域
        val rectBWidth = 120.dp2px().toFloat() * scaleB
        val rectBHeight = 28.dp2px().toFloat() * scaleBHeight
        rectB.set(
            (width / 2f) - rectBWidth / 2f,
            (height / 2f) - rectBHeight / 2f,
            (width / 2f) + rectBWidth / 2f,
            (height / 2f) + rectBHeight / 2f
        )

        // 设置模糊效果
        rectAPaint.maskFilter = android.graphics.BlurMaskFilter(blurA, android.graphics.BlurMaskFilter.Blur.NORMAL)
        rectBPaint.maskFilter = android.graphics.BlurMaskFilter(blurB, android.graphics.BlurMaskFilter.Blur.NORMAL)

        // 绘制矩形 A
        rectAPaint.alpha = (alphaA * 255).toInt()
        canvas.drawRoundRect(rectA, rectAHeight / 2, rectAHeight / 2, rectAPaint)
//        canvas.drawOval(rectA, rectAPaint)

        // 绘制矩形 B
        rectBPaint.alpha = (alphaB * 255).toInt()
        canvas.drawRoundRect(rectB, rectBHeight / 2, rectBHeight / 2, rectBPaint)
//        canvas.drawOval(rectB, rectBPaint)
    }

    private fun startBreathingAnimation() {
        // 使用 ValueAnimator 动态调整 blurA 值
        val animator = ValueAnimator.ofFloat(0.1f, 1f).apply {
            duration = 800 // 动画时长
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                blurA = 50f + (it.animatedValue as Float) * 20f
                alphaA = 0.2f + (it.animatedValue as Float) * 0.8f
                scaleA = 1f + (it.animatedValue as Float) * 0.16f
                scaleAHeight = 1f + (it.animatedValue as Float) * 0.16f

                alphaB = 0.2f + (it.animatedValue as Float) * 0.8f
                scaleB = 1f + (it.animatedValue as Float) * 0.16f
                scaleBHeight = 1f + (it.animatedValue as Float) * 0.16f
                invalidate() // 触发重绘
            }
        }
        animator.start()
    }
}