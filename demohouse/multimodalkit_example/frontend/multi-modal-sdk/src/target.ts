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

/**
 * The callee of the function invocation or the receiver of an event
 */
export interface TargetEntity {
  /**
   * Searching scope such as 'system', 'view', 'page', etc. Used by the framework and the client for resolver grouping
   */
  scope: string;

  /**
   * Base condition for target searching
   *
   * If the given `scope` and `target` pair is enough to target an entity then the `instance` field is ignored. For
   * example: `{ scope: 'page', target: 'top' }` refers to the top page in the page stack.
   */
  target: string;

  /**
   * Additional information for target searching when needed
   */
  instance?: string;
}

/**
 * Check whether the input object is a TargetEntity
 *
 * @param input The input object to check
 * @returns Whether the input object is a TargetEntity
 */
export function isTargetEntity(input: object): input is TargetEntity {
  return (
    'scope' in input &&
    typeof input.scope === 'string' &&
    'target' in input &&
    typeof input.target === 'string' &&
    (!('instance' in input) ||
      typeof input.instance === 'undefined' ||
      typeof input.instance === 'string')
  );
}

/**
 * Predefined target entities
 *
 * @category Custom API
 */
export const targets = {
  /**
   * Targeting client functionality, including traditional JSB.
   */
  clientAPI: (): TargetEntity => ({
    scope: 'system',
    target: 'bridge',
  }),
} satisfies Record<string, (...args: never[]) => TargetEntity>;
