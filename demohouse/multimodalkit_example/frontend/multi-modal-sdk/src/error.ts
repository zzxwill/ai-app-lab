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

import {
  type CallResult,
  FailedCallResult,
  InternalErrorCallResult,
  ResultCode,
} from './call-result';

/**
 * Base error type for Bridge API
 */
export abstract class APICallError<ErrorType extends object> extends Error {
  readonly name: string = 'APICallError';

  constructor(
    protected readonly code: number,
    msg: string,
    protected readonly apiName?: string,
  ) {
    super(apiName ? `${apiName}: ${msg}` : msg);
    Object.setPrototypeOf(this, new.target.prototype);
  }

  /** @ignore */
  abstract toCallResult(): CallResult<ErrorType>;
}

/**
 * An error that indicates the function call was successfully returned, but the result is in failed state.
 */
export class APICallFailedError<
  ErrorType extends object,
> extends APICallError<ErrorType> {
  readonly name: string = 'APICallFailedError';
  readonly data?: ErrorType;

  constructor(msg: string, data?: ErrorType, apiName?: string) {
    super(ResultCode.FAILED, msg, apiName);
    this.data = data;
  }

  /** @ignore */
  toCallResult(): CallResult<object, ErrorType> {
    return new FailedCallResult(this.message, this.data);
  }
}

/**
 * An error that indicates the bridge call itself was failed
 */
export class APICallInternalError extends APICallError<never> {
  readonly name: string = 'APICallInternalError';

  protected constructor(code: number, msg: string, apiName?: string) {
    super(code, msg, apiName);
  }

  /* eslint-disable @typescript-eslint/no-use-before-define */
  /** @ignore */
  static fromCallResult(result: InternalErrorCallResult, apiName?: string) {
    switch (result.code) {
      case ResultCode.UNAUTHORIZED:
        return new APIUnauthorizedError(result.code, result.msg, apiName);
      case ResultCode.UNREGISTERED:
        return new APIUnregisteredError(result.code, result.msg, apiName);
      case ResultCode.INVALID_PARAMS:
        return new APIInvalidParamsError(result.code, result.msg, apiName);
      case ResultCode.INVALID_RESULT:
        return new APIInvalidResultError(result.code, result.msg, apiName);
      default:
        return new APICallInternalError(result.code, result.msg, apiName);
    }
  }
  /* eslint-enable @typescript-eslint/no-use-before-define */

  /** @ignore */
  toCallResult(): CallResult<never, object> {
    return new InternalErrorCallResult(this.code, this.message);
  }
}

/** Function call failed because of unauthorized operation */
export class APIUnauthorizedError extends APICallInternalError {
  readonly name: string = 'APIUnauthorizedError';
}

/** Function call failed because the desired function not found */
export class APIUnregisteredError extends APICallInternalError {
  readonly name: string = 'APIUnregisteredError';
}

/** Function call failed because the input parameters don't meet the requirement */
export class APIInvalidParamsError extends APICallInternalError {
  readonly name: string = 'APIInvalidParamsError';
}

/** Function call failed because the result can't be recognized */
export class APIInvalidResultError extends APICallInternalError {
  readonly name: string = 'APIInvalidResultError';
}

/**
 * An error that indicates the Bridge is broken.
 */
export class MultiModalSDKBridgeError extends APICallError<never> {
  readonly name: string = 'MultiModalSDKBridgeError';

  constructor() {
    super(ResultCode.UNREGISTERED, 'Bridge unavailable');
  }

  /** @ignore */
  toCallResult(): CallResult<never, object> {
    return new InternalErrorCallResult(
      ResultCode.UNREGISTERED,
      'Bridge unavailable',
    );
  }
}
