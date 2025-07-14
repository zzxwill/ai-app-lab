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

let bridgeTraceEnabled = false;
let traceLogger: (...args: unknown[]) => void = console.debug;

/**
 * The trace tag that will be inserted in front of the other arguments automatically
 */
export const APPLET_BRIDGE_TRACE_TAG = '[Applet Bridge]';

/**
 * Print trace logs for Applet bridge if enabled
 *
 * @param args Log arguments
 * @internal Only used by the bridge framework, do not re-export.
 */
export function bridgeTrace(...args: unknown[]) {
  if (bridgeTraceEnabled) traceLogger(APPLET_BRIDGE_TRACE_TAG, ...args);
}

/**
 * Switch on/off the bridge trace logger
 *
 * @param enabled whether the bridge trace logs should be printed.
 */
export function setBridgeTraceEnabled(enabled: boolean) {
  bridgeTraceEnabled = enabled;
}

/**
 * Set the custom bridge trace logger
 *
 * This is mainly used by the unit tests, but can also be used elsewhere.
 *
 * @param logger The actual logger
 */
export function setBridgeTraceLogger(logger: (...args: unknown[]) => void) {
  traceLogger = logger;
}
