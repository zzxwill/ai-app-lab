package com.bytedance.ai.multimodal.copilot.bridge

import com.bytedance.ai.bridge.core.CoreAIBridgeMethod
import com.bytedance.ai.bridge.core.annotation.AIBridgeParamField
import com.bytedance.ai.bridge.core.annotation.AIBridgeParamModel
import com.bytedance.ai.bridge.core.annotation.AIBridgeResultModel
import com.bytedance.ai.bridge.core.model.idl.ParamModel
import com.bytedance.ai.bridge.core.model.idl.ResultModel

abstract class AbsCopyMessageMethodIDL :
    CoreAIBridgeMethod<AbsCopyMessageMethodIDL.CopyMessageParamModel, AbsCopyMessageMethodIDL.CopyMessageResultModel>() {

    override val name: String = "applet.multimodal.copyMessage"

    @AIBridgeParamModel
    interface CopyMessageParamModel : ParamModel {
        @get:AIBridgeParamField(required = true, keyPath = "message")
        val message: String
    }

    @AIBridgeResultModel
    interface CopyMessageResultModel : ResultModel
}