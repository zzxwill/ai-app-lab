package com.bytedance.ai.multimodal.copilot.manager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.contains
import androidx.core.view.drawToBitmap
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.utils.UnitUtils.dp2px
import com.bytedance.ai.multimodal.common.utils.gone
import com.bytedance.ai.multimodal.common.utils.setClipViewCornerRadius
import com.bytedance.ai.multimodal.common.utils.visible
import com.bytedance.ai.multimodal.copilot.R
import com.bytedance.ai.multimodal.copilot.ScreenUtil
import com.bytedance.ai.multimodal.copilot.view.floating.HotInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler

class SingleFloatBall (
    context: Context,
    private val handler: SingleFloatWindowHandler,
    private val winManager: FloatWindowManager
): FrameLayout(context) {

    companion object {
        val maskIconPadding = 17.dp2px()
        const val TAG = "SingleFloatBall"

        fun createBaseParams(): WindowManager.LayoutParams {
            val params = WindowManager.LayoutParams()
            params.format = PixelFormat.TRANSPARENT
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            } else {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }
            params.gravity = Gravity.TOP or Gravity.RIGHT
            params.x = 6.dp2px()
            params.y = ScreenUtil.getStatusBarHeight() + 80.dp2px()
            params.width = 0
            params.height = 0
            return params
        }
    }

    private val dragHelper: SingleFloatDragHelper
    private val hotInfo: HotInfo
    private var params: WindowManager.LayoutParams
    private var contentContainer: ViewGroup
    private var leftMaskContainer: ViewGroup
    private var rightMaskContainer: ViewGroup
    private var leftMaskIcon: ImageView
    private var rightMaskIcon: ImageView
    var onBallClickListener: OnClickListener? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.float_ball, this, false)
        addView(view)
        val bottomMargin =  ScreenUtil.getNavBarHeight() + 12.dp2px()
        hotInfo = HotInfo(6.dp2px(), ScreenUtil.getStatusBarHeight() + 12.dp2px(), bottomMargin)
        contentContainer = view.findViewById(R.id.container)
        leftMaskContainer = view.findViewById(R.id.leftMaskContainer)
        rightMaskContainer = view.findViewById(R.id.rightMaskContainer)
        leftMaskIcon = view.findViewById(R.id.leftMaskIcon)
        rightMaskIcon = view.findViewById(R.id.rightMaskIcon)
        params = createBaseParams()
        dragHelper = SingleFloatDragHelper(this)
        dragHelper.onClickListener = OnClickListener { onClicked() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragHelper.onTouch(this, event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.onViewTouched()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                handler.onViewTouchFinished()
            }
        }
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            autoUpdateBlurMask()
        }
        return dragHelper.onInterceptTouchEvent(this, ev)
    }

    private fun autoUpdateBlurMask() {
        try {
            val bitmap = resizeBitmapToMaxDimension(contentContainer.drawToBitmap())
            CoroutineScope(Dispatchers.IO).launch(CoroutineExceptionHandler { _, throwable ->
                FLogger.e(TAG, "autoUpdateBlurMask CoroutineScope fail: $throwable")
            }) {
                val drawable = BitmapDrawable(resources, bitmap)
                FLogger.d(TAG, "autoUpdateBlurMask: $drawable")
                MainScope().launch(CoroutineExceptionHandler { _, throwable ->
                    FLogger.e(TAG, "autoUpdateBlurMask MainScope fail: $throwable")
                }) {
                    leftMaskContainer.background = drawable
                    rightMaskContainer.background = drawable
                }
            }
        } catch (e: Exception) {
            FLogger.e("SingleFloatBall", "autoUpdateBlurMask fail: $e")
        }
    }


    /**
     * 缩放 Bitmap，确保最大宽度/高度不超过 2048，同时保持原始宽高比。
     *
     * @param originalBitmap 原始的 Bitmap 对象。
     * @return 缩放后的 Bitmap 对象。
     */
    private fun resizeBitmapToMaxDimension(originalBitmap: Bitmap): Bitmap {
        // 目标最大尺寸
        val maxWidth = 2048
        val maxHeight = 2048

        val width = originalBitmap.width
        val height = originalBitmap.height

        // 计算缩放比例
        val ratio = Math.min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        // 如果比例大于等于1，说明原图宽高都小于2048，直接返回原图
        if (ratio >= 1.0) {
            return originalBitmap
        }

        // 计算缩放后的尺寸
        val scaledWidth = (width * ratio).toInt()
        val scaledHeight = (height * ratio).toInt()

        // 创建新的 Bitmap 对象，并返回
        return Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true)
    }

    fun getParams(): WindowManager.LayoutParams = params

    fun getHotInfo(): HotInfo = hotInfo


    fun updatePosition(params: WindowManager.LayoutParams, source: Int){
        winManager.updateViewLayout(this, params)
        handler.onViewMove(source)
    }

    fun showBall() {
        winManager.addView(this, params)
    }

    fun hideBall() {
        winManager.removeView(this)
    }

    fun updateContentView(view: View, width: Int, height: Int) {
        params.width = width
        params.height = height
        if (contentContainer.contains(view)) {
            view.layoutParams.width = width
            view.layoutParams.height = height
            updatePosition(params, MoveSource.RESET_ANIMATE)
            //winManager.updateViewLayout(this, params)
        } else {
            contentContainer.removeAllViews()
            contentContainer.addView(view)
            handler.onViewMove(MoveSource.RESET_ANIMATE)
            winManager.addView(this, params.x, params.y, params.gravity)
        }
        // 参照Lynx的FMP pct90分位
        postDelayed({ autoUpdateBlurMask() }, 1100)
    }

    fun removeContentView(view: View){
        contentContainer.removeView(view)
        params.width = 0
        params.height = 0
        winManager.removeView(this)
    }

    private fun onClicked() {
        onBallClickListener?.onClick(this)
    }

    fun alphaShowMask(alpha: Float) {
        val maskView = if (isInLeft()) {
            leftMaskContainer
        } else {
            rightMaskContainer
        }
        FLogger.d(TAG, "alphaShowMask: $alpha")
        maskView.alpha = alpha
        maskView.visible()
    }

    fun dismissMask() {
        val maskView = if (isInLeft()) {
            leftMaskContainer
        } else {
            rightMaskContainer
        }
        maskView.gone()
    }

    fun alphaShowMaskIcon(alpha: Float) {
        val icon = if (isInLeft()) {
            leftMaskIcon
        } else {
            rightMaskIcon
        }
        icon.alpha = alpha
        icon.visible()
    }

    fun dismissMaskIcon() {
        val icon = if (isInLeft()) {
            leftMaskIcon
        } else {
            rightMaskIcon
        }
        icon.gone()
    }

    fun setMaskClickListener(listener: OnClickListener) {
        if (isInLeft()) {
            leftMaskContainer.setOnClickListener(listener)
        } else {
            rightMaskContainer.setOnClickListener(listener)
        }
    }

    private fun isInLeft(): Boolean = params.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        this.setClipViewCornerRadius(8.dp2px())
    }

    override fun setAlpha(alpha: Float) {
        winManager.setAlpha(this, alpha)
        super.setAlpha(alpha)
    }
}