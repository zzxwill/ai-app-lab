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

import { MultiModalSDKBridgeError } from '../../error';
import { bridgeTrace } from '../../log';
import { EventMessage, type Message, parseMessage } from '../../message';
import { BRIDGE_READY_EVENT_NAME } from '../constants';
import { Bridge } from '../interface';

declare global {
  // biome-ignore lint/suspicious/noExplicitAny: complex lynx API
  const lynx: any;
  const __LEPUS__: boolean;
  // biome-ignore lint/suspicious/noExplicitAny: complex lynx API
  const NativeModules: any;
}

export const LYNXVIEW_BRIDGE_TRACE_TAG = '[LynxView]';
const trace = (...args: unknown[]) =>
  bridgeTrace(LYNXVIEW_BRIDGE_TRACE_TAG, ...args);

export class LynxBridgeImpl extends Bridge {
  private handshake = false;
  private available = true;
  private receiver?: (message: Message) => void;

  init() {
    if (lynx.__isSSRInternalUsage) return;
    try {
      if (!__LEPUS__) {
        lynx
          .getJSModule('GlobalEventEmitter')
          .addListener('__APPLET_BRIDGE__', (message: unknown) => {
            trace('receive from client', message);
            this.receiver?.(parseMessage(message));
          });
      }
    } catch (e) {
      console.error('Applet bridge initialization failed');
      trace(e);
      this.available = false;
    }
  }

  notifyReady(): void {
    if (this.handshake || lynx.__isSSRInternalUsage) return;
    // Send handshake message
    NativeModules.AppletBridgeModule.postMessage(
      new EventMessage({ name: BRIDGE_READY_EVENT_NAME }).toPlainObject(),
    );
    this.handshake = true;
  }

  call(message: Message) {
    // should not throw error in Lynx rocket env.
    if (lynx.__isSSRInternalUsage) return;
    trace('call to client', message);
    if (!this.available) throw new MultiModalSDKBridgeError();
    NativeModules.AppletBridgeModule.postMessage(message.toPlainObject());
  }

  listen(handler: (message: Message) => void): void {
    this.receiver = handler;
  }
}

export const bridge: Bridge = /*#__PURE__*/ new LynxBridgeImpl();
