package com.bytedance.ai.multimodal.copilot.screenshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import com.bytedance.ai.multimodal.common.MultimodalKitInternal
import com.bytedance.ai.multimodal.common.log.FLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer

class ScreenshotSession {
    companion object {
        private const val TAG = "ScreenShotHelper"
        internal var permissionResultCallback: ((Int, Intent?) -> Unit)? = null

        @Volatile
        private var instance: ScreenshotSession? = null

        fun getInstance(): ScreenshotSession {
            return instance ?: synchronized(this) {
                instance ?: ScreenshotSession().also { instance = it }
            }
        }
    }

    private var isStart: Boolean = false
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val context = MultimodalKitInternal.applicationContext
    private var width = 0
    private var height = 0
    private var dpi = 0
    private var imageReader: ImageReader? = null
    private val screenFlow = MutableSharedFlow<Bitmap>(
        extraBufferCapacity = 1
    )

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)
        dpi = metrics.densityDpi
    }

    /**
     * 启动权限申请流程
     */
     fun requestPermission(callback: (Boolean) -> Unit) {
        if (mediaProjection != null) {
            callback(true)
            return
        }

        permissionResultCallback = { resultCode, data ->
            if (resultCode == Activity.RESULT_OK && data != null) {
                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = manager.getMediaProjection(resultCode, data)
                mediaProjection?.registerCallback(object : MediaProjection.Callback (){
                    override fun onStop() {
                        FLogger.e(TAG, "mediaProjection stop")
                        release()
                    }
                }, null)
                callback(true)
            } else {
                callback(false)
            }
        }

        context.startService(Intent(context, ScreenshotForegroundService::class.java))

        val intent = Intent(context, CapturePermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

     fun fetchFrame(): Flow<Bitmap> = flow {
        screenFlow.collect {
            emit(it)
        }
    }

    private fun prepareCapture(width: Int, height: Int) {
        if (this.width != width || this.height != height || imageReader == null) {
            imageReader?.close()
            this.width = width
            this.height = height
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay?.release()
            virtualDisplay = null
        }
        if (virtualDisplay == null){
            FLogger.d(TAG, "===== width: $width, height: $height")
            imageReader?.apply {
                virtualDisplay = mediaProjection?.createVirtualDisplay("ScreenCapture",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    this.surface, null, null
                )
            }
        }
    }

     fun start(width: Int, height: Int) {
        if (!isStart) {
            isStart = true
            handlerThread = HandlerThread("ScreenCaptureHelper", Process.THREAD_PRIORITY_DEFAULT).also {
                it.start()
                handler = Handler(it.looper)
            }

            prepareCapture(width, height)
            val listener = ImageReader.OnImageAvailableListener {
                try {
                    imageReader?.acquireLatestImage().use { image ->
                        FLogger.d(TAG, "----- image: $image. current thread: ${Thread.currentThread().name}")
                        if (image != null) {
                            if (screenFlow.subscriptionCount.value > 0) {
                                val bitmap = convertToMap(image)
                                screenFlow.tryEmit(bitmap)
                            }
                        } else {
                            FLogger.e(TAG, "image is NULL")
                        }
                    }
                } catch (e: Exception) {
                    FLogger.e(TAG, "acquireNextImage exception: $e")
                    release()
                }
            }
            imageReader?.setOnImageAvailableListener(listener, handler)
        }
    }

     fun release() {
        isStart = false
        FLogger.e(TAG, "stopScreenCapture")
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null

        // 释放后置空实例
        synchronized(Companion) {
            instance = null
        }
        handlerThread?.quitSafely()
    }

    private fun convertToMap(image: Image): Bitmap {
        val planes: Array<Image.Plane> = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride: Int = planes[0].pixelStride
        val rowStride: Int = planes[0].rowStride
        val rowPadding =
            rowStride - pixelStride * width
        val bitmap = createBitmap(image.width + rowPadding / pixelStride, image.height)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

}