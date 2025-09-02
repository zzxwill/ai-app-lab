import dataclasses
from pydantic import BaseModel
from typing import Any, Dict


def to_dict(obj: Any, **kwargs: Any) -> Dict[str, Any]:
    """
    Intelligently and robustly converts a supported object to a dictionary,
    passing through any additional keyword arguments to the underlying
    serialization methods of Pydantic models.
    This utility is the single source of truth for serialization.
    Handles:
    - Pydantic V2 models (using .model_dump(**kwargs))
    - Pydantic V1 models (using .dict(**kwargs))
    - Standard Python dataclasses (does not support kwargs)
    - Objects with a __dict__ attribute (does not support kwargs)
    """
    # Pydantic models are the only ones that accept kwargs for serialization.
    if isinstance(obj, BaseModel):
        if hasattr(obj, "model_dump"):
            return obj.model_dump(**kwargs)
        return obj.dict(**kwargs)  # Fallback for Pydantic V1
    # For other types, kwargs are not supported, so we raise an error
    # if they are provided, to avoid silent failures.
    if kwargs:
        raise TypeError(
            f"Conversion of type {type(obj).__name__} does not support "
            f"extra keyword arguments: {list(kwargs.keys())}"
        )
    if dataclasses.is_dataclass(obj) and not isinstance(obj, type):
        return dataclasses.asdict(obj)
    if hasattr(obj, "__dict__"):
        return obj.__dict__
    raise TypeError(f"Object of type {type(obj).__name__} is not serializable.")
