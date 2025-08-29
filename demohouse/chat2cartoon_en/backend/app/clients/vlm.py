# Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
# Licensed under the 【火山方舟】原型应用软件自用许可协议
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at 
#     https://www.volcengine.com/docs/82379/1433703
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from typing import List, AsyncIterable

from arkitect.core.component.llm.model import ArkChatParameters, ArkMessage, ArkChatResponse
from arkitect.utils import AsyncTimedIterable
from byteplussdkarkruntime import Ark
from byteplussdkarkruntime.types.chat import ChatCompletionChunk


class VLMClient:
    vlmclient: Ark
    endpoint: str

    def __init__(self, vlm_api_key: str, endpoint: str) -> None:
        self.vlmclient = Ark(api_key=vlm_api_key, region="cn-beijing")
        self.endpoint = endpoint

    def chat_generation(self, messages: List[ArkMessage]) -> AsyncIterable[ChatCompletionChunk]:
        messages = list(filter(lambda m: m.role in ["system", "assistant", "user"], messages))

        resp = self.vlmclient.chat.completions.create(
            model=self.endpoint,
            messages=messages,
            temperature=1.0,
            top_p=0.7,
            stream=True,
        )

        return AsyncTimedIterable(resp, timeout=5)
