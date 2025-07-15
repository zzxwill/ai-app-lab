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

import type { Message, MessageCallback } from '../message';

/**
 * Abstract bridge interface for front-end
 *
 * This class acts as the abstract bridge layer and thus should be as generic as possible and defines the basic data
 * transfer methods.
 */
export abstract class Bridge {
  /**
   * Prepare global interfaces if needed
   */
  abstract init(): void;

  /**
   * Register message handler for incoming messages
   */
  abstract listen(handler: (message: Message) => void): void;

  /**
   * Send message to client
   *
   * Messages sent through this interface including function call, callback, event broadcasting, etc.
   */
  abstract call(message: Message): void;

  /**
   * Indicate that the framework is ready to receive messages from the client and send handshake message.
   */
  abstract notifyReady(): void;
}

export interface GlobalBridgeReceiver<ReceiveType = unknown> {
  (message: ReceiveType): void;
  on: (listener: MessageCallback) => void;
  off: (listener: MessageCallback) => void;
  isAvailable: () => boolean;
  notifyReady: () => void;
}
