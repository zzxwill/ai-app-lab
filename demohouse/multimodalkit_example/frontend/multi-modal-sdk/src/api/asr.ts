// Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
// Licensed under the 【火山方舟】原型应用软件自用许可协议
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
//     https://www.volcengine.com/docs/82379/1433703
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import { createAPI, createEvent } from '../client-api';

/**
 * 开始语音识别(ASR)
 * @returns 返回 Promise 包含 ASR 开启状态
 * @example
 * ```ts
 * import { startASR } from 'multi-modal-sdk';
 *
 * const ASRstatus = await startASR();
 * console.log('ASR 开启状态:', ASRstatus.status);
 * ```
 */
export const startASR = createAPI('applet.multimodal.startASR');

/**
 * 停止语音识别(ASR)
 * @example
 * ```ts
 * import { stopASR } from 'multi-modal-sdk';
 *
 * stopASR();
 * ```
 */
export const stopASR = createAPI('applet.multimodal.stopASR');

/**
 * 监听 ASR 识别结果
 * @param callback - 回调函数
 * @param callback.text - ASR 流式识别的文字结果
 * @param callback.isFinished - 表示识别内容是否结束，true 表示整段话识别结束
 * @example
 * ```ts
 * // 监听流式结果
 * onASRResult(({ text }) => {
 *   console.log('当前识别:', text);
 * });
 *
 * // 监听完整结果
 * onASRResult(({ text, isFinished }) => {
 *   if (isFinished) {
 *     console.log('最终识别结果:', text);
 *   }
 * });
 * ```
 */
export const onASRResult = createEvent<{
  text: string;
  isFinished?: boolean;
}>('applet.multimodal.ASRResult');
