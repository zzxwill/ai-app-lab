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
import { useCallback, useEffect, useMemo, useState } from "react";

export interface SelectionProps {
  items: string[] | { key: string; item: string }[] | Record<string, string>;
  value?: number;
  onChange?: (value: number) => void;
}

const Selection = ({ items, value, onChange }: SelectionProps) => {
  const [selection, setSelection] = useState(value || 0);
  useEffect(() => {
    if (value !== undefined) {
      setSelection(value);
    }
  }, [value]);
  const mappedItems = useMemo(() => {
    if (Array.isArray(items)) {
      return items.map((item) => {
        if (typeof item === "string") return { key: item, item };
        return item;
      });
    }
    return Object.entries(items).map(([key, item]) => ({ key, item }));
  }, [items]);
  const onClick = useCallback(
    (index: number) => {
      if (index !== selection) {
        onChange?.(index);
        setSelection(index);
      }
    },
    [onChange, selection]
  );
  return (
    <div className="flex flex-col gap-[1px] border border-gray-300 bg-gray-300 rounded-sm overflow-hidden">
      {mappedItems.map(({ key, item }, index) => (
        <div
          key={key}
          className={clsx(
            "p-2 cursor-pointer select-none transition-colors duration-150",
            selection === index ? "bg-blue-100" : "bg-white"
          )}
          onClick={() => onClick(index)}
        >
          {item}
        </div>
      ))}
    </div>
  );
};

export default Selection;
