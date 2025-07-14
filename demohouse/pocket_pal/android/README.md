## 环境准备

1. 请参阅 [TTSASRInitializer](copilot/src/main/java/com/bytedance/ai/multimodal/copilot/init/TTSASRInitializer.kt)、[VLMInitializer](copilot/src/main/java/com/bytedance/ai/multimodal/copilot/init/VLMInitializer.kt)、[DefaultConfig](copilot/src/main/java/com/bytedance/ai/multimodal/copilot/init/DefaultConfig.kt) 完成配置。

2. 需分别下载物体分割([MobileSAM](https://github.com/ChaoningZhang/MobileSAM))/物体识别([RT-DETR](https://github.com/lyuwenyu/RT-DETR))模型文件并放置在Android工程 `copilot/src/main/assets` 目录下，使用对应模型文件时需遵守其许可证要求。 模型文件如下：
   - [mobile_sam_decoder.onnx](https://lf3-static.bytednsdoc.com/obj/eden-cn/lm_sth/ljhwZthlaukjlkulzlp/ark/assistant/models/mobile_sam_decoder.onnx)
   - [mobile_sam_encoder.onnx](https://lf3-static.bytednsdoc.com/obj/eden-cn/lm_sth/ljhwZthlaukjlkulzlp/ark/assistant/models/mobile_sam_encoder.onnx)
   - [detr_v2_s.onnx](https://lf3-static.bytednsdoc.com/obj/eden-cn/lm_sth/ljhwZthlaukjlkulzlp/ark/assistant/models/rt-detr_v2_s.onnx)
3. 需要使用 jdk11 进行编译。

## 注意事项

1. 手机助手 Android 工程需与前端工程产物完成结合，目前工程内默认读取屏幕内容并实时完成回答，不弹出前端页面。开发者如需弹出前端页面，需编译前端产物后内置或部署后，通过Function Call在客户端触发弹出，详见`com.bytedance.ai.multimodal.copilot.view.floating.AudioShot`。
2. 本工程使用了多模态SDK，关于多模态SDK的使用请参考`demohouse/multimodalkit_example`内的README.md进行了解。