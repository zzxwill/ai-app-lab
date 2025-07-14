package com.bytedance.ai.multimodal.copilot.manager

import android.view.View
import android.view.ViewGroup.LayoutParams
import com.bytedance.ai.multimodal.common.MultimodalKit
import com.bytedance.ai.multimodal.common.utils.UnitUtils.dp2px
import com.bytedance.ai.multimodal.copilot.view.floating.AudioShot
import com.bytedance.ai.multimodal.visual.realtime.processor.AsrResultProcessor
import com.bytedance.ai.multimodal.visual.realtime.IVideoSource

class MutexFloatManager {
    companion object {
        private var sInst: MutexFloatManager? = null
        fun inst(): MutexFloatManager? {
            if (sInst == null) {
                synchronized(MutexFloatManager::class.java) {
                    if (sInst == null) {
                        sInst = MutexFloatManager()
                    }
                }
            }
            return sInst
        }
    }

    private var controller: FloatWindowController =
        FloatWindowController(MultimodalKit.applicationContext)

    fun showFloatAudioView(
        actionHandler: AudioShot.ActionHandler?,
        videoSource: IVideoSource,
        processor: AsrResultProcessor,
        eosTimeout: Int? = null
    ) {
        val audioShot = AudioShot.getSingleton(MultimodalKit.applicationContext)
        audioShot.init(actionHandler, videoSource, processor, eosTimeout)
        controller.updateView(audioShot, LayoutParams.WRAP_CONTENT, 60.dp2px())
    }

    fun hideFloatAudioView() {
        val audioShot = AudioShot.getSingleton(MultimodalKit.applicationContext)
        audioShot.release()
        controller.setOnClickListener(null)
        controller.removeView(audioShot)
    }

    fun showFloatView(view: View, params: LayoutParams) {
        controller.showFloatView(view, params)
    }

    fun removeFloatView(view: View) {
        controller.removeFloatView(view)
    }
}