from typing import List, AsyncIterable

from arkitect.core.component.llm import BaseChatLanguageModel
from arkitect.core.component.llm.model import ArkChatParameters, ArkMessage, ArkChatResponse
from arkitect.utils import AsyncTimedIterable


class VLMClient:
    endpoint_id: str

    def __init__(self, endpoint_id: str):
        self.endpoint_id = endpoint_id

    def chat_generation(self, messages: List[ArkMessage]) -> AsyncIterable[ArkChatResponse]:
        messages = list(filter(lambda m: m.role in ["system", "assistant", "user"], messages))

        vlm_chat = BaseChatLanguageModel(
            endpoint_id=self.endpoint_id,
            messages=messages,
            parameters=ArkChatParameters(temperature=1.0, top_p=0.7),
        )

        return AsyncTimedIterable(vlm_chat.astream(), timeout=5)
