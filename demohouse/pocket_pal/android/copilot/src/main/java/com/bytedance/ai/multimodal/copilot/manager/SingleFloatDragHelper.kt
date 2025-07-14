package com.bytedance.ai.multimodal.copilot.manager

import android.os.SystemClock
import android.view.*
import com.bytedance.ai.multimodal.common.utils.ScreenUtils
import com.bytedance.ai.multimodal.common.log.FLogger

class SingleFloatDragHelper(private val ball: SingleFloatBall) {

    var onClickListener: View.OnClickListener? = null

    private val touchSlopSquare: Int = try {
        val viewConfiguration = ViewConfiguration.get(ball.context)
        viewConfiguration.scaledTouchSlop * viewConfiguration.scaledTouchSlop
    } catch (e: Exception) {
        // ViewConfiguration.get(context) 出现了一例异常
        576
    }
    private val tapTimeout = ViewConfiguration.getTapTimeout()

    private var mDownX = 0f
    private var mDownY = 0f
    private var parentWidth = 0
    private var parentHeight = 0

    private var downTime = 0L

    private var canMove = false

    fun onInterceptTouchEvent(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onDown(view, event)
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

    private fun onDown(view: View, event: MotionEvent) {
        mDownX = event.rawX
        mDownY = event.rawY
        val parent = view.parent
        parentWidth = if (parent is ViewGroup) parent.width else ScreenUtils.getScreenWidth(view.context)
        parentHeight = if (parent is ViewGroup) parent.height else ScreenUtils.getScreenHeight(view.context)
        FLogger.d(SingleFloatBall.TAG, "parentWidth: $parentWidth, parentHeight: $parentHeight")
        if (onClickListener != null) {
            downTime = SystemClock.uptimeMillis()
        }
        canMove = false
    }

    fun onTouch(view: View, event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            onDown(view, event)
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
            gravityAwareUpdate(view, event)
        } else if (action == MotionEvent.ACTION_UP) {
            val clickListener = onClickListener
            if (clickListener != null && !canMove && SystemClock.uptimeMillis() - downTime <= tapTimeout) {
                clickListener.onClick(view)
            }
            return canMove
        }
        return true
    }

    private fun gravityAwareUpdate(view: View, event: MotionEvent) {
        val params = ball.getParams()
        // 更新view坐标位置
        val diffX = (event.rawX - mDownX).toInt()
        val diffY = (event.rawY - mDownY).toInt()
        mDownX = event.rawX
        mDownY = event.rawY
        val x = if (params.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
            params.x + diffX
        } else {
            params.x - diffX
        }
        params.x = x
        val y = params.y + diffY
        params.y = y
        val width = if (params.width == WindowManager.LayoutParams.WRAP_CONTENT) {
            ball.measuredWidth
        } else {
            params.width
        }
        //FLogger.d(SingleFloatBall.TAG, "x: $x, y: $y, width: ${width}, height: ${params.height}")
        // 将坐标位置与Gravity融合做转换
        val whichSide = if (params.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT) {
            if (params.x + width / 2 < ScreenUtils.getScreenWidth(view.context) / 2) {
                Gravity.LEFT
            } else {
                params.x = ScreenUtils.getScreenWidth(view.context)- params.x - width
                Gravity.RIGHT
            }
        } else {
           // FLogger.d(SingleFloatBall.TAG, "params.x: ${params.x}, width: $width")
            if (params.x + width / 2 > ScreenUtils.getScreenWidth(view.context) / 2) {
                params.x = ScreenUtils.getScreenWidth(view.context) - params.x - width
                Gravity.LEFT
            } else {
                Gravity.RIGHT
            }
        }
        params.gravity = Gravity.TOP or whichSide
        ball.updatePosition(params, MoveSource.FINGER)
    }
}