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

from typing import AsyncIterable

from arkitect.core.component.llm.model import ArkChatRequest, ArkChatResponse, ArkMessage

from app.clients.llm import LLMClient
from app.constants import LLM_ENDPOINT_ID
from app.generators.base import Generator
from app.generators.phase import Phase, PhaseFinder
from app.generators.phases.common import get_correction_completion_chunk
from app.mode import Mode

ROLE_DESCRIPTION_SYSTEM_PROMPT = ArkMessage(
    role="system",
    content="""
# Task
 You will generate character descriptions based on the most recent story content and storyboard scenes marked with the phase "Script" and "StoryBoard" in the conversation history.

# Requirements
- The overall visual style should be cartoon-like illustrations with a cute, child-friendly aesthetic, rendered in 3D.
- Each character description must be concise, no more than 30 characters, and include key facial features.
- Each character description must include their clothing and the location where they appear.
- Number of characters per output: 1 to 4.
- Every character must wear clothes.
- Each character must specify its exact type, such as rabbit, squirrel, little boy, little girl, mother near, etc.
- [Important] If the user’s input appears valid and complete, prepend the result with "phase=RoleDescription" before returning the descriptions.

# Constraints
- Do not include any inappropriate, suggestive, restricted, or adult content.
- Do not write any sentences that involve physical contact with children.
- Do not request or mention any sensitive personal information, such as home addresses.
- Do not add any "phase=xxx" prefixes other than the required "phase=RoleDescription".

# Output Format
 If the number of characters is between 1 and 4, follow this structure. If there is only one character, only return Character 1:

phase=RoleDescription
Character 1: 
Character: Little Bear Character 
Description: Little Bear, round head and face, small black nose. Attire: Blue hat and brown vest with a yellow star pattern (Forest) 
Character 2: 
Character: Little Fox Character 
Description: Little Fox, pointed face and ears, slender eyes. Attire: Red cloak with gold embroidery (Forest) 
Character 3: 
Character: Little Bird Character 
Description: Little Bird, small and delicate, round eyes. Attire: White bib (On a tree branch)
"""
)


class RoleDescriptionGenerator(Generator):
    llm_client: LLMClient
    request: ArkChatRequest
    mode: Mode
    phase_finder: PhaseFinder

    def __init__(self, request: ArkChatRequest, mode: Mode.NORMAL):
        super().__init__(request, mode)

        chat_endpoint_id = LLM_ENDPOINT_ID
        if request.metadata:
            chat_endpoint_id = request.metadata.get("chat_endpoint_id", LLM_ENDPOINT_ID)

        self.llm_client = LLMClient(chat_endpoint_id)
        self.request = request
        self.mode = mode
        self.phase_finder = PhaseFinder(request)

    async def generate(self) -> AsyncIterable[ArkChatResponse]:
        if self.mode == Mode.CORRECTION:
            yield get_correction_completion_chunk(self.request.messages[-1], Phase.ROLE_DESCRIPTION)
        else:
            _, script_message = self.phase_finder.get_phase_message(Phase.SCRIPT)
            _, storyboard_message = self.phase_finder.get_phase_message(Phase.STORY_BOARD)
            messages = [
                ROLE_DESCRIPTION_SYSTEM_PROMPT,
                script_message,
                storyboard_message,
                self.request.messages[-1],
            ]

            async for resp in self.llm_client.chat_generation(messages):
                yield resp
