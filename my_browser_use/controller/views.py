from pydantic import BaseModel

class PauseAction(BaseModel):
    reason: str