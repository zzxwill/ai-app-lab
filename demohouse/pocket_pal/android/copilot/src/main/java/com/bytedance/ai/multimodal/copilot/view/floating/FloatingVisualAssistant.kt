package com.bytedance.ai.multimodal.copilot.view.floating

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.bytedance.ai.multimodal.copilot.screenshot.ScreenshotSession
import com.bytedance.ai.multimodal.copilot.AppCore
import com.bytedance.ai.multimodal.copilot.manager.MutexFloatManager
import com.bytedance.ai.multimodal.copilot.R
import com.bytedance.ai.multimodal.copilot.ScreenCaptureSource
import com.bytedance.ai.multimodal.copilot.base.ProcessorConfigProvider
import com.bytedance.ai.multimodal.copilot.core.permission.PermissionManager
import com.bytedance.ai.multimodal.copilot.core.utils.ActivityManager
import com.bytedance.ai.multimodal.copilot.core.utils.SettingsPreference
import com.bytedance.ai.multimodal.visual.realtime.videosource.FrameInterceptDelegateConfig
import com.bytedance.ai.multimodal.visual.realtime.IVideoSource
import com.bytedance.ai.multimodal.visual.realtime.RealtimeServiceProvider
import com.bytedance.ai.multimodal.visual.realtime.local.createDefaultAsrProcessor
import com.bytedance.ai.multimodal.visual.realtime.service.RealtimeForegroundService
import com.bytedance.ai.multimodal.visual.realtime.service.ServiceStateCallback
import kotlin.apply
import kotlin.let
import kotlin.text.toFloatOrNull
import kotlin.text.toIntOrNull


/**
 * 悬浮窗（同屏助手）
 */
object FloatingVisualAssistant: AudioShot.ActionHandler {
    var isFloatingBallShow = false
        private set
    const val KEY_SHOW_CAMERA = "showCamera"

    fun showFloatingBall(context: Activity) {
        if (!isFloatingBallShow) {
            if(PermissionManager.isPermissionGranted(Manifest.permission.SYSTEM_ALERT_WINDOW)){
                realShowFloatingBall(context)
            } else {
                PermissionManager.requestPermission(Manifest.permission.SYSTEM_ALERT_WINDOW, object:
                    PermissionManager.RequestPermissionCallback {
                    override fun onResult(grantedPermissions: List<String>, result: Boolean) {
                        if (result) {
                            realShowFloatingBall(context)
                        } else {
                            Toast.makeText(context, R.string.warning_permission_denied, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }

    fun removeFloatingBall(context: Context) {
        if (isFloatingBallShow) {
            context.apply {
                RealtimeForegroundService.stopVisualAssistant(context)
                isFloatingBallShow = false
            }
        }
    }

    private fun realShowFloatingBall(context: Activity) {
        if (PermissionManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            requestMediaProjectionPermission(context)
        } else {
            PermissionManager.requestPermission(Manifest.permission.RECORD_AUDIO, object :
                PermissionManager.RequestPermissionCallback {
                override fun onResult(grantedPermissions: List<String>, result: Boolean) {
                    if (result) {
                        //投屏
                        requestMediaProjectionPermission(context)
                    } else {
                        Toast.makeText(context, R.string.warning_permission_denied, Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun requestMediaProjectionPermission(context: Activity){
        with(ScreenshotSession.getInstance()) {
            this.requestPermission { grant ->
                if (grant) {
                    createFloatingBallWithScreenSharing(context)
                } else {
                    Toast.makeText(context, R.string.warning_permission_denied, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun initFrameInterceptDelegateConfig(): FrameInterceptDelegateConfig {
        val frameInterval =
            SettingsPreference.getGlobalPreference().getString("frame_interval", null)
                ?.toIntOrNull()
        val frameIntervalSecond =
            SettingsPreference.getGlobalPreference().getInt("frame_interval_second", 2)

        val enableSimilarityFilter = SettingsPreference.getGlobalPreference()
            .getBoolean("enable_similarity_frame_filter", false)
        val similarityThreshold =
            SettingsPreference.getGlobalPreference().getString("similarity_threshold", "0.8")
                ?.toFloatOrNull() ?: 0.8f
        return FrameInterceptDelegateConfig(
            frameInterval = frameInterval ?: (frameIntervalSecond * 1000),
            enableSimilarityFilter = enableSimilarityFilter,
            similarityThreshold = similarityThreshold
        )
    }

    private fun createFloatingBallWithScreenSharing(context: Activity) {
        RealtimeForegroundService.startService(
            context,
            "com.bytedance.ai.multimodal.copilot.page.MainActivity",
            R.mipmap.ic_copilot_launcher,
            object : ServiceStateCallback {
                override fun onStart(videoSource: IVideoSource?) {
                    val source = ScreenCaptureSource(context)
                    showFloatingAudioViewInRealtimeMode(source)
                    isFloatingBallShow = true
                }

                override fun onStop() {
                    MutexFloatManager.inst()?.hideFloatAudioView()
                }
            }
        )
    }


    override fun onCloseAction() {
        val context = ActivityManager.currentActivity ?: AppCore.inst
        removeFloatingBall(context)
    }

    override fun onLongClickAction() {
        ActivityManager.currentActivity?.let {
            it.startActivity(Intent(it, Class.forName("com.flow.aisdk.example.page.HomeActivity")).apply {
                putExtra(KEY_SHOW_CAMERA, true)
            })
        }?: AppCore.inst.startActivity(Intent(AppCore.inst, Class.forName("com.flow.aisdk.example.page.HomeActivity")).apply {
            putExtra(KEY_SHOW_CAMERA, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun showFloatingAudioViewInRealtimeMode(videoSource: IVideoSource) {
        val processor = RealtimeServiceProvider.createDefaultAsrProcessor(
            ProcessorConfigProvider.newDefaultProcessorConfig(),
            videoSource
        )
        val eosTimeout = SettingsPreference.getGlobalPreference().getString("eos_timeout", "1000")
            ?.toIntOrNull()
        MutexFloatManager.inst()?.showFloatAudioView(
            this@FloatingVisualAssistant,
            videoSource,
            processor,
            eosTimeout
        )
    }

}