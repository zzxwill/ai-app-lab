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

import { defineConfig } from '@rsbuild/core';
import { pluginLess } from '@rsbuild/plugin-less';
import { pluginReact } from '@rsbuild/plugin-react';

export default defineConfig({
  plugins: [pluginReact(), pluginLess()],
  html: {
    title: 'Multi modal SDK example',
    inject: 'body',
    scriptLoading: 'blocking',
  },
  output: {
    inlineScripts: true,
    inlineStyles: true,
  },
  source: {
    define: {
      "process.env.MODEL": JSON.stringify(process.env.MODEL),
      "process.env.API_KEY": JSON.stringify(process.env.API_KEY),
    }
  }
});
