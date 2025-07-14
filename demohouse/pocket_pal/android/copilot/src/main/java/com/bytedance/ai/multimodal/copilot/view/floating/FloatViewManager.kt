package com.bytedance.ai.multimodal.copilot.view.floating

import android.content.Context
import android.util.Log
import com.bytedance.ai.multimodal.asr.api.ASRManager
import com.bytedance.ai.multimodal.asr.api.ASRResultCallback
import com.bytedance.ai.multimodal.common.MultimodalKit
import com.bytedance.ai.multimodal.copilot.view.BottomSheetWidgetView
import com.bytedance.ai.multimodal.copilot.view.FloatSpeakerStatusView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.run

object FloatViewManager {
    private const val TAG = "FloatViewManager"

    private var bottomSheetView: WeakReference<BottomSheetWidgetView>? = null
    private var speakerStatusView: WeakReference<FloatSpeakerStatusView>? = null

    // 保存 Application Context
    private val appContext: Context = MultimodalKit.applicationContext


    fun init() {
        "FloatViewManager register".log()
        ASRManager.registerAsrResultCallback(object : ASRResultCallback {
            override fun onFinalResult(result: String) {
                speakerStatusView?.get()?.run {
                    updateText(result)
                }
                "asr $result".log()
            }

            override fun onReceiving(totalText: String) {
                "onReceiving $totalText".log()
                CoroutineScope(Dispatchers.Main).launch {
                    speakerStatusView?.get()?.updateText(totalText)
                }
            }

            override fun onError(errorCode: Int, errorMsg: String, taskId: String) {
                "ASR onError $errorMsg".log()
            }

            override fun onASRStart(taskId: String) {
                "onASRStart $taskId".log()
                showSpeakerStatus()
//                speakerStatusView?.updateText("")
            }

            override fun onASRStop(taskId: String, sosTimeout: Boolean) {
                "onASRStop $taskId --> $sosTimeout".log()
            }
        })
    }

    fun showSpeakerStatus() {
        "showSpeakerStatus".log()
        if (bottomSheetView?.get()?.isShowing == true) {
            return
        }

        if (speakerStatusView == null) {
            speakerStatusView = WeakReference(FloatSpeakerStatusView(appContext))
        }
        speakerStatusView?.get()?.show()
        speakerStatusView?.get()?.updateText("我在听，请说话")
    }

    fun dismissSpeakerStatus() {
        speakerStatusView?.get()?.dismiss()
    }

    fun showBottomSheet(
        data: String,
        context: Context? = null,
        showListener: ShowListener? = null
    ) {
        if (bottomSheetView == null) {
            bottomSheetView = WeakReference(BottomSheetWidgetView(context ?: appContext))
        }
        bottomSheetView?.get()?.show(data, showListener)
    }

    fun isBottomSheetShowing() : Boolean {
        return bottomSheetView?.get()?.isShowing ?: false
    }

    // 隐藏语音状态浮窗
    fun hideSpeakerStatus() {
        speakerStatusView?.get()?.dismiss()
        speakerStatusView = null
    }

    // 隐藏底部卡片浮窗
    fun hideBottomSheet() {
        bottomSheetView?.get()?.dismiss()
        bottomSheetView = null
    }


    fun String.log() = Log.e(TAG, this)
}