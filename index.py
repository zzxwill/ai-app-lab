import uuid
from langchain_openai import ChatOpenAI
from browser_use import Agent, BrowserContextConfig
from browser_use.browser.browser import Browser, BrowserConfig
from browser_use.browser.context import BrowserContext
from browser_use.agent.views import AgentHistoryList, AgentHistory
import asyncio
import logging
from dotenv import load_dotenv
from datetime import datetime
import os
from pathlib import Path
import base64
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import json
from typing import AsyncGenerator

app = FastAPI()

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

logging.getLogger('browser_use').setLevel(logging.DEBUG)

load_dotenv()

async def format_sse(data: dict) -> str:
    """Format data as SSE message"""
    message = f"data: {json.dumps(data)}\n\n"
    return message

async def run_task(task: str, task_id: str) -> AsyncGenerator[str, None]:
    """Run the task and yield SSE events"""
    try:
        # Send initial status
        yield await format_sse({"task_id": task_id, "status": "started"})

        base_dir = "videos"
        base_dir = os.path.join(base_dir, task_id)

        snapshot_dir =os.path.join(base_dir, "snapshots")
        Path(snapshot_dir).mkdir(parents=True, exist_ok=True)

        gif_dir = os.path.join(base_dir, "gif")
        Path(gif_dir).mkdir(parents=True, exist_ok=True)

        recording_dir = os.path.join(base_dir, "recording")
        Path(recording_dir).mkdir(parents=True, exist_ok=True)

        trace_dir = os.path.join(base_dir, "trace")
        Path(trace_dir).mkdir(parents=True, exist_ok=True)

        browser = Browser(
            config=BrowserConfig(
                headless=True
            )
        )

        config = BrowserContextConfig(
            save_recording_path=recording_dir,
            save_downloads_path=os.path.join(base_dir, "download"),
            trace_path=os.path.join(trace_dir, f"{task_id}.zip"),
            highlight_elements=False
        )

        context = BrowserContext(
            browser=browser,
            config=config
        )

        try:
            yield await format_sse({"task_id": task_id, "status": "browser_initialized"})

            async def new_step_callback(state, model_output, step_number):
                if model_output:
                    conversation_update = {
                        "step": step_number,
                        "goal": model_output.current_state.next_goal if hasattr(model_output.current_state, "next_goal") else "",
                        "memory": model_output.current_state.memory if hasattr(model_output.current_state, "memory") else "",
                        "evaluation": model_output.current_state.evaluation_previous_goal if hasattr(model_output.current_state, "evaluation_previous_goal") else "",
                        "actions": [a.dict() for a in model_output.action] if hasattr(model_output, "action") else []
                    }

                    # Create and send conversation update without yielding
                    conv_message = await format_sse(
                        {
                            "task_id": task_id,
                            "status": "conversation_update",
                            "metadata": {
                                "type": "planning_step",
                                "data": conversation_update
                            }
                        }
                    )
                    asyncio.create_task(send_sse_message(conv_message))

                # if state and state.screenshot:
                #     sse_message = await format_sse(
                #         {
                #             "task_id": task_id,
                #             "status": "live_screenshot",
                #             "metadata": {
                #                 "type": "browser_live_screenshot_base64",
                #                 "data": state.screenshot,
                #             }
                #         }
                #     )

                #     # Use asyncio.create_task to send the message without waiting
                #     asyncio.create_task(send_sse_message(sse_message))


                #     filename = f"snapshot_{step_number:03d}.png"
                #     filepath = os.path.join(snapshot_dir, filename)
                #     with open(filepath, "wb") as f:
                #         f.write(base64.b64decode(state.screenshot))

                return True

            async def send_sse_message(message):
                nonlocal sse_queue
                await sse_queue.put(message)

            sse_queue = asyncio.Queue()

            async def snapshot_polling(browser_ctx, interval=1.0):  # interval in seconds
                try:
                    counter = 0
                    while True:
                        try:
                            counter += 1
                            screenshot = await browser_ctx.take_screenshot()
                            sse_message = await format_sse(
                                {
                                    "task_id": task_id,
                                    "status": "polling_snapshot",
                                    "metadata": {
                                        "type": "browser_live_screenshot_base64",
                                        "data": screenshot,
                                    }
                                }
                                )
                            asyncio.create_task(send_sse_message(sse_message))

                            filename = f"polling_snapshot_{counter:03d}.png"
                            filepath = os.path.join(snapshot_dir, filename)
                            with open(filepath, "wb") as f:
                                f.write(base64.b64decode(screenshot))

                        except Exception as e:
                            pass

                        await asyncio.sleep(interval)
                except asyncio.CancelledError:
                    pass

            polling_task = asyncio.create_task(snapshot_polling(context))

            agent = Agent(
                task=task,
                llm=ChatOpenAI(model="gpt-4o"),
                browser_context=context,
                save_conversation_path=os.path.join(base_dir, "conversation"),
                generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                register_new_step_callback=new_step_callback,
            )

            yield await format_sse({"task_id": task_id, "status": "agent_initialized"})

            # Start the agent in a separate task
            agent_task = asyncio.create_task(agent.run())

            while not agent_task.done() or not sse_queue.empty():
                if not sse_queue.empty():
                    yield await sse_queue.get()
                else:
                    await asyncio.sleep(0.1)

            result = await agent_task

            final_result = None
            for history_item in reversed(result.history):
                for result_item in history_item.result:
                    if hasattr(result_item, "is_done") and result_item.is_done == True:
                        final_result = result_item.extracted_content
                        break
                if final_result:
                    break

            if not final_result:
                final_result = [
                    [item.extracted_content for item in history_item.result if hasattr(item, "extracted_content")]
                    for history_item in result.history
                ]

            yield await format_sse({
                "task_id": task_id,
                "status": "completed",
                "metadata": {
                    "type": "screentshot_gif_path",
                    "data": gif_dir
                }
            })

            yield await format_sse({
                "task_id": task_id,
                "status": "completed",
                "metadata": {
                    "type": "recording_path",
                    "data": recording_dir
                }
            })

            yield await format_sse({
                "task_id": task_id,
                "status": "completed",
                "choices": [{
                    "delta": {
                        "role": "assistant", # 固定值 assistant
                        "content": final_result #不一定是完整的内容，只有 sse 请求执行完成后才会完成 内容输出
                    },
                }],
                "result": final_result
            })

        except Exception as e:
            # Send error status
            yield await format_sse({
                "task_id": task_id,
                "status": "error",
                "error": str(e)
            })
            raise
        finally:
            polling_task.cancel()
            try:
                await polling_task
            except asyncio.CancelledError:
                pass

            await browser.close()

    except Exception as e:
        # Send error status for any outer exceptions
        yield await format_sse({
            "task_id": task_id,
            "status": "error",
            "error": str(e)
        })



class Message(BaseModel):
    role: str
    content: str

class Messages(BaseModel):
    messages: list[Message]

class TaskRequest(BaseModel):
    task: str

@app.get("/")
async def root():
    return {"message": "Hello World"}

@app.post("/run")
async def run(request: Messages):
    task_id = str(uuid.uuid4())

    task = ""
    for message in request.messages:
        if message.role == "user":
            task = message.content
            break

    return StreamingResponse(
        run_task(task, task_id),
        media_type="text/event-stream"
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)