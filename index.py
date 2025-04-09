from time import time
import uuid
from langchain_openai import ChatOpenAI
from browser_use import Agent, BrowserContextConfig, SystemPrompt
from browser_use.browser.browser import Browser, BrowserConfig
from browser_use.browser.context import BrowserContext
import asyncio
import logging
from dotenv import load_dotenv
from datetime import datetime
import os
from pathlib import Path
import base64
from fastapi import FastAPI, HTTPException, Request, WebSocket
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import json
from typing import AsyncGenerator
import uvicorn
from playwright.async_api import async_playwright
from cdp import websocket_endpoint, get_websocket_targets, get_websocket_version, get_inspector
from urllib.parse import urlparse

app = FastAPI()

llm_openai = "openai"
llm_deepseek = "deepseek"
llm_ark = "ark"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

# logging.getLogger('browser_use').setLevel(logging.DEBUG)

load_dotenv()

async def format_sse(data: dict) -> str:
    """Format data as SSE message"""
    message = f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    return message


async def run_task(task: str, task_id: str) -> AsyncGenerator[str, None]:
    """Run the task and yield SSE events"""
    browser = None
    context = None
    try:
        logging.debug(f"Starting task: {task}, task_id: {task_id}")
        # Send initial status
        yield await format_sse({"task_id": task_id, "status": "started"})

        base_dir = "videos"
        base_dir = os.path.join(base_dir, task_id)

        snapshot_dir = os.path.join(base_dir, "snapshots")
        Path(snapshot_dir).mkdir(parents=True, exist_ok=True)

        # gif_dir = os.path.join(base_dir, "gif")
        # Path(gif_dir).mkdir(parents=True, exist_ok=True)

        # recording_dir = os.path.join(base_dir, "recording")
        # Path(recording_dir).mkdir(parents=True, exist_ok=True)

        # trace_dir = os.path.join(base_dir, "trace")
        # Path(trace_dir).mkdir(parents=True, exist_ok=True)

        try:
            browser = Browser(
                config=BrowserConfig(
                    headless=True,
                    disable_security=True,
                    # deterministic_rendering=True
                    cdp_url="http://127.0.0.1:9222"
                )
            )

            yield await format_sse({"task_id": task_id, "status": "browser_initialized"})

            config = BrowserContextConfig(
                # save_recording_path=recording_dir,
                # save_downloads_path=os.path.join(base_dir, "download"),
                # trace_path=os.path.join(trace_dir, f"{task_id}.zip"),
                highlight_elements=False,
            )

            context = BrowserContext(
                browser=browser,
                config=config
            )

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

            logging.info(f"Creating agent with task: {task}, llm: {llm_name}, task_id: {task_id}")

            try:
                if llm_name == llm_openai:
                    agent = Agent(
                        task=task,
                        llm=ChatOpenAI(model="gpt-4o"),
                        use_vision=True,
                        browser_context=context,
                        # save_conversation_path=os.path.join(base_dir, "conversation"),
                        # generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                        register_new_step_callback=new_step_callback,
                    )
                elif llm_name == llm_deepseek:
                    class BaiduSystemPrompt(SystemPrompt):
                        action_description = """IMPORTANT: You must ALWAYS use Baidu.com for ALL searches.
                        1. NEVER use Google or any other search engine
                        2. ALWAYS start by navigating to https://www.baidu.com
                        3. Use Baidu's search box for all searches
                        4. This is a strict requirement - you must use Baidu.com"""

                    # It's a workaround as ChatOpenAI will check the api key
                    os.environ["OPENAI_API_KEY"] = "sk-dummy"
                    baidu_task = f"Remember to use ONLY baidu.com for searching. Task: {task}"

                    agent = Agent(
                        task=baidu_task,
                        llm=ChatOpenAI(
                            base_url="https://api.deepseek.com/v1",
                            model="deepseek-chat",
                            api_key=os.getenv("DEEPSEEK_API_KEY")
                        ),
                        use_vision=False,
                        browser_context=context,
                        # save_conversation_path=os.path.join(base_dir, "conversation"),
                        # generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                        register_new_step_callback=new_step_callback,
                        system_prompt_class=BaiduSystemPrompt
                    )
                elif llm_name == llm_ark:
                    # It's a workaround as ChatOpenAI will check the api key
                    os.environ["OPENAI_API_KEY"] = "sk-dummy"

                    class BaiduSystemPrompt(SystemPrompt):
                        action_description = """IMPORTANT: You must ALWAYS use Baidu.com for ALL searches.
                        1. NEVER use Google or any other search engine
                        2. ALWAYS start by navigating to https://www.baidu.com
                        3. Use Baidu's search box for all searches
                        4. This is a strict requirement - you must use Baidu.com"""




                    agent = Agent(
                        task=task,
                        initial_actions=[
                            {"go_to_url": {"url": "https://baidu.com"}}],
                        llm=ChatOpenAI(
                            base_url="https://ark.cn-beijing.volces.com/api/v3",
                            model=os.getenv("ARK_MODEL_ID"),
                            api_key=os.getenv("ARK_API_KEY"),
                            default_headers={
                                "X-Client-Request-Id": "vefaas-browser-use-20250403"}
                        ),
                        page_extraction_llm=ChatOpenAI(
                            base_url="https://ark.cn-beijing.volces.com/api/v3",
                            model=os.getenv("ARK_EXTRACT_MODEL_ID"),
                            api_key=os.getenv("ARK_API_KEY"),
                            default_headers={
                                "X-Client-Request-Id": "vefaas-browser-use-20250403"}
                        ),
                        use_vision=os.getenv(
                            "ARK_USE_VISION", "False").lower() == "true",
                        tool_calling_method=os.getenv(
                            "ARK_FUNCTION_CALLING", "raw").lower(),
                        browser_context=context,
                        # save_conversation_path=os.path.join(base_dir, "conversation"),
                        # generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                        register_new_step_callback=new_step_callback,
                        # register_done_callback=step_done_callback,
                        # system_prompt_class=BaiduSystemPrompt
                    )
                else:
                    raise ValueError(f"Unknown LLM type: {llm_name}")

            except Exception as e:
                logging.error(f"Failed to create agent: {str(e)}")
                yield await format_sse({
                    "task_id": task_id,
                    "status": "error",
                    "error": f"Agent creation failed: {str(e)}"
                })
                return

            yield await format_sse({"task_id": task_id, "status": "agent_initialized"})

            # Start the agent in a separate task
            agent_task = asyncio.create_task(agent.run(10))

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
                    [item.extracted_content for item in history_item.result if hasattr(
                        item, "extracted_content")]
                    for history_item in result.history
                ]

            # yield await format_sse({
            #     "task_id": task_id,
            #     "status": "completed",
            #     "metadata": {
            #         "type": "screentshot_gif_path",
            #         "data": gif_dir
            #     }
            # })

            # yield await format_sse({
            #     "task_id": task_id,
            #     "status": "completed",
            #     "metadata": {
            #         "type": "recording_path",
            #         "data": recording_dir
            #     }
            # })

            yield await format_sse({
                "task_id": task_id,
                "status": "completed",
                "choices": [{
                    "delta": {
                        "role": "assistant",  # 固定值 assistant
                        "content": final_result  # 不一定是完整的内容，只有 sse 请求执行完成后才会完成 内容输出
                    },
                }],
                "result": final_result
            })

        except Exception as e:
            logging.error(f"Agent execution failed: {str(e)}")
            yield await format_sse({
                "task_id": task_id,
                "status": "error",
                "error": f"Agent execution failed: {str(e)}"
            })
        finally:
            polling_task.cancel()
            try:
                await polling_task
            except asyncio.CancelledError:
                pass

            try:
                if context:
                    await context.close()
                if browser:
                    await browser.close()
            except Exception as e:
                logging.error(f"Failed to close browser/context: {str(e)}")

    except Exception as e:
        logging.error(f"Task execution failed: {str(e)}")
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

    prompt = ""
    for message in request.messages:
        if message.role == "user":
            prompt = message.content
            logging.debug(f"Found user message: {prompt}")
            break

    logging.debug(f"Final prompt value: {prompt}")
    return StreamingResponse(
        run_task(prompt, task_id),
        media_type="text/event-stream"
    )


@app.get("/devtools/json/list")
async def json_list():
    return await get_websocket_targets()

@app.get("/devtools/json/version")
async def json_version():
    return await get_websocket_version()

@app.websocket("/devtools/page/{page_id}")
async def cdp_websocket(websocket: WebSocket, page_id: str):
    await websocket_endpoint(websocket, page_id)

@app.get("/devtools/inspector.html")
async def inspector(request: Request):
    return await get_inspector(request.url)

def check_llm_config() -> bool:
    global llm_name
    llm_name = ""

    if os.getenv("OPENAI_API_KEY"):
        llm_name = llm_openai
    elif os.getenv("DEEPSEEK_API_KEY"):
        llm_name = llm_deepseek
    elif os.getenv("ARK_API_KEY"):
        if os.getenv("ARK_MODEL_ID") == "":
            raise Exception("ARK_MODEL_ID is not set, please set ARK_MODEL_ID in environment variables")
        llm_name = llm_ark
    else:
        raise Exception("No LLM API key found, please set OPENAI_API_KEY, DEEPSEEK_API_KEY or ARK_API_KEY/ARK_MODEL_ID in environment variables")

    print("using llm: ", llm_name)

# Event to signal when the browser is ready
browser_ready_event = asyncio.Event()

async def start_browser():
    p = await async_playwright().start()
    
    try:
        browser = await p.chromium.launch(
            headless=True,
            args=[
                '--remote-debugging-port=9222',  # This exposes the CDP service
                '--remote-allow-origins=*',  # Allow any origin to connect
                '--remote-debugging-address=0.0.0.0',  # Listen on all interfaces
                '--no-sandbox'
            ]
        )
        
        # Create a global event to signal browser is ready
        browser_ready_event.set()
        
        # Keep the browser running indefinitely
        while True:
            try:
                # Check browser health periodically
                contexts = browser.contexts
                if not contexts:
                    logging.warning("No active browser contexts. Attempting to recover.")
                    try:
                        await browser.close()
                    except:
                        pass
                    
                    # Relaunch browser
                    browser = await p.chromium.launch(
                        headless=True,
                        args=[
                            '--remote-debugging-port=9222',
                            '--remote-allow-origins=*',
                            '--remote-debugging-address=0.0.0.0',
                            '--no-sandbox'
                        ]
                    )
                
                # Wait for a while before next check
                await asyncio.sleep(60)  # Check every minute
            
            except Exception as e:
                logging.error(f"Error in browser monitoring: {e}")
                await asyncio.sleep(10)  # Wait before retry
    
    except Exception as e:
        logging.error(f"Failed to start browser: {e}")
        browser_ready_event.clear()
        raise
    finally:
        # Ensure playwright is closed properly
        await p.stop()

# Modify main startup to use this approach
async def main():
    # Start browser in the background
    browser_task = asyncio.create_task(start_browser())
    
    # Wait for browser to be ready before starting other services
    await browser_ready_event.wait()
    
    # Start FastAPI server
    config = uvicorn.Config(app, host="0.0.0.0", port=8000)
    server = uvicorn.Server(config)
    
    # Run server
    await server.serve()
    
    # Wait for browser task to complete (which should be never)
    await browser_task

if __name__ == "__main__":
    check_llm_config()
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Shutting down...")
