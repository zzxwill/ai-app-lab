from browser_use.agent.prompts import SystemPrompt
from langchain_core.messages import SystemMessage


class SystemPromptDecorator(SystemPrompt):
    extra_prompt_content = ""
    next_class = None

    @classmethod
    def create_system_prompt_class(cls, class_name: str, content: str, next_class=None):
        """工厂方法动态创建"""
        return type(
            f"{class_name.capitalize()}",
            (cls,),
            {
                "extra_prompt_content": content,
                "next_class": next_class
            }
        )

    def get_system_message(self) -> SystemMessage:
        # 获取基础系统消息
        msg = super().get_system_message()
        # 遍历装饰器链累积所有附加内容
        current = self
        while current:
            msg.content += '\n\n'
            msg.content += current.extra_prompt_content
            next = current.next_class() if current.next_class else None
            current = next if isinstance(next, SystemPromptDecorator) else None
        return msg
