package com.bytedance.ai.multimodal.copilot.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.bytedance.ai.bridge.context.IAIBridgeContext
import com.bytedance.ai.bridge.core.CompletionBlock
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.MultimodalKit
import com.bytedance.ai.multimodal.common.utils.ToastUtil

class CopyMessageMethod : AbsCopyMessageMethodIDL() {

    companion object {
        private val TAG = "CopyMessageMethod"
    }

    override fun handle(
        bridgeContext: IAIBridgeContext,
        params: CopyMessageParamModel,
        callback: CompletionBlock<CopyMessageResultModel>
    ) {
        FLogger.i(TAG, "handle ${params.message}")
        // 获取系统剪贴板服务
        val clipboard = MultimodalKit.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 创建 ClipData 对象，包含要复制的文本
        val clip = ClipData.newPlainText("label", params.message)

        // 设置 ClipData 到剪贴板
        clipboard.setPrimaryClip(clip)

        ToastUtil.showToast("复制成功")
    }
}