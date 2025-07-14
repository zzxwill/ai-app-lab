package com.bytedance.ai.multimodal.copilot.bridge

import com.bytedance.ai.bridge.context.IAIBridgeContext
import com.bytedance.ai.bridge.core.CompletionBlock
import com.bytedance.ai.multimodal.common.log.FLogger
import com.bytedance.ai.multimodal.common.utils.ToastUtil

class LikeMethod : AbsLikeMethodIDL() {
    override fun handle(
        bridgeContext: IAIBridgeContext,
        params: LikeParamModel,
        callback: CompletionBlock<LikeResultModel>
    ) {
        FLogger.i("LikeMethod", "handle like=${params.like} dislike=${params.dislike}")
        if (params.like == true) {
            ToastUtil.showToast("感谢你的喜欢")
        }
        if (params.dislike == true) {
            ToastUtil.showToast("感谢你的反馈")
        }

    }
}