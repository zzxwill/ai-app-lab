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

import clsx from "clsx";
import type { ReactNode } from "react";

export interface ButtonProps {
  block?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  children?: ReactNode;
}

const Button = ({
  block = false,
  disabled = false,
  onClick,
  children,
}: ButtonProps) => (
  <div
    className={clsx(
      "px-3 py-2 text-white rounded-sm select-none transition-colors duration-300",
      block ? "block" : "inline-block",
      disabled
        ? "bg-blue-300 cursor-not-allowed"
        : "bg-blue-500 hover:bg-blue-600 cursor-pointer"
    )}
    onClick={() => {
      if (!disabled && onClick) onClick();
    }}
  >
    {children}
  </div>
);

export default Button;
