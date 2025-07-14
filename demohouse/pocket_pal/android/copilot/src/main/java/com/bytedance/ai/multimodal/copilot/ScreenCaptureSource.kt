package com.bytedance.ai.multimodal.copilot

import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.view.WindowManager
import com.bytedance.ai.multimodal.copilot.screenshot.ScreenshotSession
import com.bytedance.ai.multimodal.common.base.VisualImageObject
import com.bytedance.ai.multimodal.common.base.VisualImageObjectWithBitmap
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.utils.ScreenUtils
import com.bytedance.ai.multimodal.visual.realtime.IVideoSource
import com.bytedance.ai.multimodal.visual.realtime.videosource.FrameInterceptDelegate
import com.bytedance.ai.multimodal.visual.realtime.videosource.FrameInterceptDelegateConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.take
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class ScreenCaptureSource(private val context: Context): IVideoSource {
    companion object {
        private const val TAG = "ScreenCaptureSource"
    }

    private var screenshotSession: ScreenshotSession? = null
    private var dpi = 0
    private val imageProcessScope =
        CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val frameInterceptDelegate: FrameInterceptDelegate by lazy {
        FrameInterceptDelegate(true)
    }
    private var isStart = false

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        dpi = metrics.densityDpi
    }

    override fun start() {
        FLogger.e(TAG, "start")
        isStart = true
        if (screenshotSession == null) {
            screenshotSession = ScreenshotSession.getInstance()
        }
        screenshotSession?.requestPermission {

        }
    }

    private suspend fun realScreenshot(width: Int, height: Int): Flow<Bitmap>? {
        if (screenshotSession == null) {
            screenshotSession = ScreenshotSession.getInstance()
        }
        return suspendCancellableCoroutine { c ->
            screenshotSession?.requestPermission { granted ->
                if (granted) {
                    // 执行截屏操作
                    screenshotSession?.start(width, height)
                    c.resume(screenshotSession?.fetchFrame())
                } else {
                    c.resume(null)
                }
            }
        }
    }

    private suspend fun fetchOneFrame(): VisualImageObject<*>? {
        return  realScreenshot(
            ScreenUtils.getScreenWidth(context),
            ScreenUtils.getScreenHeight(context)
        )?.take(1)?.singleOrNull()?.let {
            VisualImageObjectWithBitmap(it, false)
        }
    }

    override fun pause() {
        isStart = false
    }

    override fun stop() {
        pause()
        frameInterceptDelegate.stop()
        release()
    }

    override fun getFrameFlow(triggeredByCaller: Boolean): Flow<VisualImageObject<*>?> {
        return if (triggeredByCaller) {
            flow {
                emit(fetchOneFrame())
            }
        } else {
            return frameInterceptDelegate.getFrameFlow(false)
        }
    }

    private fun initFrameInterceptDelegate(config: FrameInterceptDelegateConfig) {
        frameInterceptDelegate.setInterceptors(config)
    }

    private fun release() {
        FLogger.e(TAG, "stopScreenCapture")
        imageProcessScope.cancel()
        screenshotSession?.release()
    }

}