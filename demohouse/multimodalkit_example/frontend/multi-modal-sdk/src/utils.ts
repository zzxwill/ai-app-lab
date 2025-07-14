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

export type TypeGuard<From, To extends From> = (from: From) => from is To;

export function hasStringProperty<
  Key extends string | number | symbol,
  Optional extends boolean,
>(
  obj: object,
  key: Key,
  options?: { optional?: Optional },
): obj is false extends Optional
  ? { [key in Key]: string }
  : { [key in Key]?: string } {
  const child = (obj as { [key in Key]?: unknown })[key] as unknown;
  if (child === undefined) return options?.optional === true;
  return typeof child === 'string';
}

export function assertStringProperty<
  Key extends string | number | symbol,
  Optional extends boolean,
>(
  obj: object,
  key: Key,
  options?: { optional?: Optional },
): asserts obj is false extends Optional
  ? { [key in Key]: string }
  : { [key in Key]?: string } {
  if (!hasStringProperty(obj, key, options))
    throw new TypeError(`expect type of "${String(key)}" to be string`);
}

export function hasNumberProperty<
  Key extends string | number | symbol,
  Optional extends boolean,
>(
  obj: object,
  key: Key,
  options?: { optional?: Optional },
): obj is false extends Optional
  ? { [key in Key]: number }
  : { [key in Key]?: number } {
  const child = (obj as { [key in Key]?: unknown })[key] as unknown;
  if (child === undefined) return options?.optional === true;
  return typeof child === 'number';
}

export function assertNumberProperty<
  Key extends string | number | symbol,
  Optional extends boolean,
>(
  obj: object,
  key: Key,
  options?: { optional?: Optional },
): asserts obj is false extends Optional
  ? { [key in Key]: number }
  : { [key in Key]?: number } {
  if (!hasNumberProperty(obj, key, options))
    throw new TypeError(`expect type of "${String(key)}" to be number`);
}

export function hasObjectProperty<
  Key extends string | number | symbol,
  Type extends object,
  Optional extends boolean,
>(
  obj: object,
  key: Key,
  options?: {
    typeGuard?: (obj: object) => obj is Type;
    optional?: Optional;
  },
): obj is false extends Optional
  ? { [key in Key]: Type }
  : { [key in Key]?: Type } {
  const { typeGuard, optional } = options ?? {};
  const child = (obj as { [key in Key]?: unknown })[key];
  if (child === undefined) return optional === true;
  return (
    typeof child === 'object' && child !== null && typeGuard?.(child) !== false
  );
}

export function assertObjectProperty<
  Key extends string | number | symbol,
  Type extends object,
  Optional extends boolean,
>(
  obj: object,
  key: Key,
  options?: {
    typeGuard?: (obj: object) => obj is Type;
    expectedType?: string;
    optional?: Optional;
  },
): asserts obj is false extends Optional
  ? { [key in Key]: Type }
  : { [key in Key]?: Type } {
  const { expectedType = 'object' } = options ?? {};
  if (!hasObjectProperty(obj, key, options))
    throw new TypeError(
      `expect type of "${String(key)}" to be ${expectedType}`,
    );
}
