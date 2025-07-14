package com.bytedance.ai.multimodal.copilot.view

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.copilot.AppCore
import com.bytedance.ai.multimodal.copilot.databinding.FloatingSpeakingStatusBinding
import com.bytedance.ai.multimodal.copilot.view.floating.AudioShot
import com.bytedance.ai.multimodal.copilot.view.floating.BaseFloatingView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility")
class FloatSpeakerStatusView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : BaseFloatingView(context, attrs, defStyleAttr) {

    private val binding: FloatingSpeakingStatusBinding =
        FloatingSpeakingStatusBinding.inflate(LayoutInflater.from(context), this, true)


    fun runInMain(invoker: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            invoker.invoke()
        }
    }

    init {
        runInMain {
            binding.tvSpeakingStatus.addTextChangedListener(object : TextWatcher{
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(text: CharSequence?,
                                           start: Int,
                                           lengthBefore: Int,
                                           lengthAfter: Int) {
                    FLogger.i("FloatSpeakerStatusView", "onTextChanged $text")
                    post {
                        if (!isShowing) {
                            return@post
                        }
                        val availableWidth = binding.tvSpeakingStatus.width -
                                binding.tvSpeakingStatus.paddingLeft - binding.tvSpeakingStatus.paddingRight
                        val textWidth = binding.tvSpeakingStatus.layout.getLineWidth(0)

                        FLogger.i("FloatSpeakerStatusView", "post av=$availableWidth w=$textWidth $text")

                        if (textWidth > availableWidth) {
                            // 文本宽度大于可用宽度，左对齐并滚动到末尾
                            binding.tvSpeakingStatus.gravity = Gravity.START
                            val scrollX = (textWidth - availableWidth).toInt()
                            binding.tvSpeakingStatus.scrollTo(maxOf(0, scrollX), 0)
                            FLogger.i("FloatSpeakerStatusView", "post scroll=$scrollX")
                            binding.tvSpeakingStatus.invalidate()
                        }
                    }
                }

                override fun afterTextChanged(p0: Editable?) {
                }
            })
        }

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    AudioShot.getSingleton(AppCore.inst).stop()
                    dismiss()
                }
            }
            false
        }
    }

    fun updateText(text: String) {
        runInMain {
            binding.tvSpeakingStatus.text = text
        }
    }

    override fun getWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    override fun dismiss() {
        super.dismiss()
        binding.tvSpeakingStatus.text = ""
    }

}