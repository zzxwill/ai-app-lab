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
import {
  EventMessage,
  type Message,
  type MessageCallback,
  parseMessage,
} from '../../message';
import {
  BRIDGE_READY_EVENT_NAME,
  MESSAGE_PORT_CHANNEL_ACK,
  MESSAGE_PORT_CHANNEL_INIT,
} from '../constants';
import { Bridge, type GlobalBridgeReceiver } from '../interface';
export const WEBVIEW_BRIDGE_TRACE_TAG = '[WebView]';
const trace = (...args: unknown[]) =>
  bridgeTrace(WEBVIEW_BRIDGE_TRACE_TAG, ...args);

declare const AppletBridgeModule: {
  /**
   * Send message to the in WebView
   */
  postMessage: (message: string) => void;
};

interface WebViewGlobalBridgeReceiver extends GlobalBridgeReceiver<string> {
  port?: MessagePort;
}

declare global {
  /**
   * Receive message from client in WebView.
   *
   * For historical reason, the input must be in JSON format
   */
  // eslint-disable-next-line no-var
  var onWebViewMessage: WebViewGlobalBridgeReceiver;
}

function sendMessageFromGlobal(message: Message) {
  trace('(global) call to client', message);
  AppletBridgeModule.postMessage(JSON.stringify(message.toPlainObject()));
}

function sendMessage(message: Message) {
  const { port } = globalThis.onWebViewMessage;
  if (port) {
    trace('(MessagePort) call to client', message);
    port.postMessage(JSON.stringify(message.toPlainObject()));
  } else {
    sendMessageFromGlobal(message);
  }
}

function makeGlobalBridgeReceiver(): WebViewGlobalBridgeReceiver {
  const listeners = new Set<MessageCallback>();
  let handshake = false;
  let available = true;
  const portHandshakeListener = (ev: MessageEvent<string>) => {
    const port = ev.ports[0];
    if (ev.data !== MESSAGE_PORT_CHANNEL_INIT || !port) return;
    globalThis.removeEventListener('message', portHandshakeListener);
    receiver.port = port;
    port.onmessage = (message) => {
      if (!message || typeof message.data !== 'string') return;
      trace('(MessagePort) receive from client', message.data);
      try {
        receiver(message.data);
      } catch (e) {
        trace('(MessagePort) receive raw message error', e);
        throw e;
      }
    };
    port.postMessage(MESSAGE_PORT_CHANNEL_ACK);
  };

  const receiver = (message: string) => {
    trace('(global) receive from client', message);
    const parsedMessage = parseMessage(JSON.parse(message));
    for (const listener of listeners) {
      listener(parsedMessage);
    }
  };
  receiver.on = (listener: MessageCallback) => void listeners.add(listener);
  receiver.off = (listener: MessageCallback) => void listeners.delete(listener);
  receiver.isAvailable = () => available;
  receiver.notifyReady = () => {
    if (handshake) return;
    try {
      sendMessageFromGlobal(
        new EventMessage({ name: BRIDGE_READY_EVENT_NAME }),
      );
    } catch (e) {
      console.error('Applet bridge initialization failed');
      trace(e);
      available = false;
    }
    handshake = true;
  };
  receiver.port = undefined as MessagePort | undefined;
  receiver._testReset = () => {
    globalThis.removeEventListener('message', portHandshakeListener);
    globalThis.addEventListener?.('message', portHandshakeListener);
  };
  globalThis.addEventListener?.('message', portHandshakeListener);
  return receiver;
}

class WebViewBridgeImpl extends Bridge {
  private receiver?: WebViewGlobalBridgeReceiver;
  private get available() {
    return this.receiver?.isAvailable() ?? false;
  }

  init(): void {
    globalThis.onWebViewMessage ??= makeGlobalBridgeReceiver();
    this.receiver = globalThis.onWebViewMessage;
  }

  notifyReady(): void {
    this.receiver?.notifyReady();
  }

  call(message: Message): void {
    if (!this.available) throw new MultiModalSDKBridgeError();
    sendMessage(message);
  }

  listen(handler: (message: Message) => void): void {
    this.receiver?.on(handler);
  }
}

export const bridge: Bridge = /*#__PURE__*/ new WebViewBridgeImpl();
