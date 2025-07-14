package com.bytedance.ai.multimodal.copilot.view.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.graphics.Outline
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.Keep
import com.bytedance.ai.multimodal.common.base.StreamingString
import com.bytedance.ai.multimodal.common.base.VisualImageObject
import com.bytedance.ai.multimodal.common.base.VisualObject
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.utils.ToastUtil
import com.bytedance.ai.multimodal.common.utils.gone
import com.bytedance.ai.multimodal.common.utils.visible
import com.bytedance.ai.multimodal.copilot.R
import com.bytedance.ai.multimodal.copilot.databinding.AudioShotBinding
import com.bytedance.ai.multimodal.visual.realtime.IRealtimeService
import com.bytedance.ai.multimodal.visual.realtime.IVideoSource
import com.bytedance.ai.multimodal.visual.realtime.RealtimeServiceProvider
import com.bytedance.ai.multimodal.visual.realtime.RealtimeServiceState
import com.bytedance.ai.multimodal.visual.realtime.RealtimeSpeaker
import com.bytedance.ai.multimodal.visual.realtime.StateListener
import com.bytedance.ai.multimodal.visual.realtime.local.LocalRealtimeServiceConfig
import com.bytedance.ai.multimodal.visual.realtime.local.createRealtimeService
import com.bytedance.ai.multimodal.visual.realtime.processor.AsrResultProcessor
import com.bytedance.ai.multimodal.vlm.api.tools.AITool
import com.bytedance.ai.multimodal.vlm.api.tools.AIToolFunction
import com.bytedance.ai.multimodal.vlm.api.tools.AIToolParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AudioShot @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val TAG = "AudioShot"

        @Volatile
        private var instance: AudioShot? = null
        fun getSingleton(context: Context): AudioShot {
            return instance ?: synchronized(this) {
                instance ?: AudioShot(context).also {
                    instance = it
                }
            }
        }
    }

    private var tools: CopilotTools = CopilotTools()
    private var videoSource: IVideoSource? = null
    private var binding: AudioShotBinding = AudioShotBinding.inflate(LayoutInflater.from(context))
    private val floatWidgetView: FloatWidget by lazy {
        FloatWidget(context)
    }

    private var currentState: RealtimeServiceState =  RealtimeServiceState.NotStarted

    private var actionHandler: ActionHandler? = null

    private val commandCloseAssistant by lazy {
        context.getString(R.string.floating_close_ui_assistant)
    }

    private val commandCloseAssistant2 by lazy {
        "$commandCloseAssistant。"
    }

    private var clientRealtimeCore: IRealtimeService? = null
    private var floatingReceiver: BroadcastReceiver? = null

    private val stateListener: StateListener = object : StateListener {
        override fun onStateChanged(newState: RealtimeServiceState) {
            binding.root.post {
                switchStatus(newState)
                if (newState is RealtimeServiceState.Error) {
                    ToastUtil.showToast(String.format(context.getString(R.string.floating_error_tip), newState.errorMessage))
                }
            }
        }

        override fun llmResponse(string: StreamingString) {

        }

        override fun asrResponse(string: StreamingString) {

        }

        override fun onAudioInput(data: ByteArray) {
        }
    }

    init {
        addView(binding.root)
        initViews()
        initReceiver()
    }

    fun init(
        actionHandler: ActionHandler?,
        videoSource: IVideoSource,
        processor: AsrResultProcessor,
        eosTimeout: Int?
    ){
        processor.addInterceptor(object : AsrResultProcessor.Interceptor{
            override fun intercept(totalText: String): Boolean {
                return if ((totalText == commandCloseAssistant || totalText == commandCloseAssistant2) && actionHandler != null) {
                    FLogger.d(TAG, "close assistant")
                    release()
                    true
                } else {
                    //默认拦截，并弹出底部弹窗
                    floatWidgetView.dismiss(null)
                    false
                }
            }
        })
        val serviceConfig = LocalRealtimeServiceConfig(
            context = context,
            videoSource = videoSource,
            recordSession = null,
            alwaysForeground = true,
            autoStartAsr = false,
            asrResultProcessor = processor,
            realtimeSpeakerConfig = RealtimeSpeaker.RealtimeSpeakerConfig(
                autoReconnect = true,
                enableEos = true,
                eosTimeout = eosTimeout
            ),
            stateListener = stateListener
        )
        this.videoSource = videoSource
        clientRealtimeCore = RealtimeServiceProvider.createRealtimeService(serviceConfig)
        this.actionHandler = actionHandler
        //TODO：若需弹出前端弹窗，需要在此处注册工具，并完成前端产物部署或内置到APK内
//        VLMChatProvider.globalVlmChat?.aiToolContext?.registerAITool(tools)
    }


    fun release() {
        clientRealtimeCore?.release(context)
        clientRealtimeCore = null
        actionHandler?.onCloseAction()
        actionHandler = null
        floatingReceiver?.apply {
            context.unregisterReceiver(this)
        }
        floatingReceiver = null
        //TODO：同上
//        VLMChatProvider.globalVlmChat?.aiToolContext?.unregisterAITool(tools)
    }

    private fun initViews() = with(binding) {
        switchStatus(RealtimeServiceState.NotStarted)
        binding.contentContainer.setOnClickListener {
            // 添加震动效果
            (context.getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.let { vibrator ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }

            if (binding.aiWriteLeft.visibility == VISIBLE) {
                dismissAIWriteLeft()
            } else {
                switchSpeakerStatus()
            }
        }

        binding.contentContainer.setOnLongClickListener {
            if (currentState != RealtimeServiceState.Idle) {
                return@setOnLongClickListener false
            }
            if (binding.aiWriteLeft.visibility == VISIBLE) {
                dismissAIWriteLeft()
                return@setOnLongClickListener true
            }
            showAIWriteLeft()
            true
        }
        binding.aiWriteLeft.setOnClickListener {
            dismissAIWriteLeft()
            CoroutineScope(Dispatchers.Main).launch {
                delay(100)
                //获取截图
                startScreenCapture {
                    handleAIWrite(it)
                }
            }
        }
        binding.btnStart.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height) // 设置为圆形裁剪
            }
        }
        binding.btnStart.clipToOutline = true
    }

    private fun showAIWriteLeft() {
        binding.aiWriteLeft.visibility = View.VISIBLE
    }

    private fun dismissAIWriteLeft() {
        binding.aiWriteLeft.visibility = View.INVISIBLE
    }

    private suspend fun startScreenCapture(handler: (VisualImageObject<*>?)->Unit) {
        FLogger.i(TAG, "startScreenCapture")
        videoSource?.getFrameFlow(true)?.collect(handler)
    }

    private fun handleAIWrite(bitmap: VisualObject<*>?) {
        //TODO 弹出底部弹窗
        ToastUtil.showToast("弹出底部弹窗")
    }

    fun stop() {
        clientRealtimeCore?.apply {
            videoSource?.pause()
            abortCurrentTask(true)
        }
    }

    private fun initReceiver() {
        if (floatingReceiver != null) {
            return
        }
    }

    private fun getDurationBaseOnText(totalText: String, duration: Long): Long {
        val time: Long = (1f * totalText.length).toLong()
        val maxDuration = 15000L // 15秒
        if (maxDuration > duration) {
            return time.coerceIn(duration, maxDuration)
        }
        return time.coerceIn(3000, maxDuration)
    }

     private fun switchSpeakerStatus() {
        when(currentState){
            is RealtimeServiceState.Idle,
            is RealtimeServiceState.NotStarted -> {
                clientRealtimeCore?.apply {
                    videoSource?.start()
                    startMic()
                }
            }
            is RealtimeServiceState.Listening -> {
                clientRealtimeCore?.apply {
                    videoSource?.pause()
                    muteMic()
                }
            }
           is RealtimeServiceState.Talking,
           is RealtimeServiceState.Error,
           is RealtimeServiceState.Handling -> {
                clientRealtimeCore?.abortCurrentTask(false)
            }
            else -> {}
        }
    }

    private fun switchStatus(status: RealtimeServiceState)  = with(binding) {
        currentState = status
        when (status) {
            is RealtimeServiceState.Idle,
            is RealtimeServiceState.NotStarted -> {
                btnStart.setImageResource(R.drawable.floating_ready)
                binding.btnStart.visible()
                binding.tvRetry.gone()
            }

            is RealtimeServiceState.Starting -> {
                btnStart.gone()
                binding.tvRetry.gone()
            }

            is RealtimeServiceState.Idle,
            is RealtimeServiceState.Listening -> {
                btnStart.gone()
                binding.tvRetry.gone()
            }

            is RealtimeServiceState.Talking -> {
                btnStart.gone()
                binding.tvRetry.gone()
            }

            is RealtimeServiceState.Handling -> {
                btnStart.gone()
                binding.tvRetry.gone()
            }

            is RealtimeServiceState.Error -> {
                btnStart.gone()
                binding.tvRetry.visible()
            }

            else -> {}
        }
    }

    interface ActionHandler {
        fun onCloseAction()

        fun onLongClickAction()
    }


    @Keep
    inner class CopilotTools : AITool {
        @AIToolFunction(
            name = "showPopup",
            description = "作为手机助手解答各种手机屏幕上的相关问题，当用户明确询问问题时，调用此工具弹出页面，调用此工具时不需要额外过多解释。如果仅聊天，则不调用此工具"
        )
        fun showPopup(@AIToolParam(description = "用户原始的提问") originQuery: String) {
            CoroutineScope(Dispatchers.Main).launch {
                startScreenCapture {
                    //todo 弹出前端弹窗，并将用户原始提问originQuery透传到前端页面
                    ToastUtil.showToast("弹出底部弹窗")
                }
            }
        }
    }
}