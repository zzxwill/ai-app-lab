package com.bytedance.ai.multimodal.copilot.bridge

import com.bytedance.ai.bridge.core.CoreAIBridgeMethod
import com.bytedance.ai.bridge.core.annotation.AIBridgeParamField
import com.bytedance.ai.bridge.core.annotation.AIBridgeParamModel
import com.bytedance.ai.bridge.core.annotation.AIBridgeResultModel
import com.bytedance.ai.bridge.core.model.idl.ParamModel
import com.bytedance.ai.bridge.core.model.idl.ResultModel

abstract class AbsLikeMethodIDL :
    CoreAIBridgeMethod<AbsLikeMethodIDL.LikeParamModel, AbsLikeMethodIDL.LikeResultModel>() {

    override val name: String = "applet.multimodal.likeMessage"

    @AIBridgeParamModel
    interface LikeParamModel : ParamModel {
        @get:AIBridgeParamField(required = false, keyPath = "like")
        val like: Boolean?

        @get:AIBridgeParamField(required = false, keyPath = "dislike")
        val dislike: Boolean?
    }

    @AIBridgeResultModel
    interface LikeResultModel : ResultModel
}