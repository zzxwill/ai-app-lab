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
from cdp import websocket_endpoint, websocket_browser_endpoint, get_websocket_targets, get_websocket_version, get_inspector
from urllib.parse import urlparse
import aiohttp

app = FastAPI()

llm_openai = "openai"
llm_deepseek = "deepseek"
llm_ark = "ark"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

# logging.getLogger('browser_use').setLevel(logging.DEBUG)

load_dotenv()

# Global variable to track the port
CURRENT_CDP_PORT = 9222

# Global task queue and task storage
task_queue = asyncio.Queue()
active_tasks = {}

async def format_sse(data: dict) -> str:
    """Format data as SSE message"""
    message = f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    return message


async def run_task(task: str, task_id: str, current_port: int) -> AsyncGenerator[str, None]:
    logging.info(f"Starting task: {task}, task_id: {task_id}, with CDP port: {current_port}")
    
    browser = None
    context = None
    try:
        # Send initial status and update task
        active_tasks[task_id].update({
            'status': 'starting',
            'started_at': datetime.now().isoformat()
        })
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

        # browser_task = asyncio.create_task(start_browser(current_port))
        
        # await browser_ready_event.wait()
        
        # # # Wait for browser task to complete (which should be never)
        # await browser_task

        try:
            browser = Browser(
                config=BrowserConfig(
                    headless=True,
                    disable_security=True,
                    # deterministic_rendering=True
                    cdp_url=f"http://127.0.0.1:{current_port}"
                )
            )

            yield await format_sse({"task_id": task_id, "status": "browser_initialized"})
            active_tasks[task_id].update({
                'status': 'browser_initialized',
                'last_update': datetime.now().isoformat()
            })

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

                    # Update active_tasks with current step and goal
                    active_tasks[task_id].update({
                        'status': 'running',

                    })

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
                active_tasks[task_id].update({
                    'status': 'failed',
                    'error': f"Agent creation failed: {str(e)}",
                    'failed_at': datetime.now().isoformat()
                })
                return

            yield await format_sse({"task_id": task_id, "status": "agent_initialized"})
            active_tasks[task_id].update({
                'status': 'agent_initialized',
                'last_update': datetime.now().isoformat()
            })

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
            
            active_tasks[task_id].update({
                'status': 'completed',
                'completed_at': datetime.now().isoformat(),
                'result': final_result
            })

        except Exception as e:
            logging.error(f"Agent execution failed: {str(e)}")
            yield await format_sse({
                "task_id": task_id,
                "status": "error",
                "error": f"Agent execution failed: {str(e)}"
            })
            active_tasks[task_id].update({
                'status': 'failed',
                'error': f"Agent execution failed: {str(e)}",
                'failed_at': datetime.now().isoformat()
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
        
        # await browser_task

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

# start a task, in which a browser will be started
@app.post("/tasks")
async def run(request: Messages):
    task_id = str(uuid.uuid4())

    prompt = ""
    for message in request.messages:
        if message.role == "user":
            prompt = message.content
            logging.debug(f"Found user message: {prompt}")
            break

    logging.debug(f"Final prompt value: {prompt}")

    global CURRENT_CDP_PORT
    
    # Increment the port for each task
    CURRENT_CDP_PORT += 1
    current_port = CURRENT_CDP_PORT

    browser_task = asyncio.create_task(start_browser(current_port))
    
    await browser_ready_event.wait()

    # Add task to queue
    await task_queue.put({
        'task_id': task_id, 
        'task': prompt, 
        'port': current_port
    })

    # Initialize task with status and metadata
    global active_tasks
    active_tasks[task_id] = {
        'prompt': prompt,
        'port': current_port,
        'status': 'queued',
        'created_at': datetime.now().isoformat(),
    }

    # Return task ID immediately
    return {
        "task_id": task_id,
        "status": "queued"
    }

@app.get("/tasks")
async def list_tasks():
    """Endpoint to list all active and recent tasks"""
    return {
        "active_tasks": active_tasks,
        "queue_size": task_queue.qsize()
    }

@app.get("/tasks/{task_id}")
async def get_task_status(task_id: str):
    """Endpoint to get status of a specific task"""
    task = active_tasks.get(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    return task

@app.get("/tasks/{task_id}/stream")
async def stream_task_results(task_id: str):
    """Stream results for a specific task"""
    port = await get_task_port(task_id)
    
    async def result_generator():
        task_info = active_tasks[task_id]
        async for result in run_task(task_info['prompt'], task_id, port):
            yield await format_sse({
                "task_id": task_id,
                "data": result
            })
    
    return StreamingResponse(result_generator(), media_type="text/event-stream")

@app.get("/devtools/json/list")
async def json_list(task_id: str):
    port = await get_task_port(task_id)
    return await get_websocket_targets(port)

@app.get("/devtools/json/version")
async def json_version(task_id: str):
    logging.info(f"Received request for /devtools/json/version with task_id: {task_id}")
    logging.info(f"active_tasks: {active_tasks}")
    
    port = await get_task_port(task_id)
    return await get_websocket_version(port)

@app.websocket("/devtools/page/{page_id}")
async def cdp_websocket(websocket: WebSocket, page_id: str):
    query_params = dict(websocket.query_params)
    task_id = query_params.get("task_id")
    
    port = await get_task_port(task_id, websocket)
    if port is None:
        return
        
    await websocket_endpoint(websocket, page_id, port)

@app.websocket("/devtools/browser/{browser_id}")
async def cdp_websocket_browser(websocket: WebSocket, browser_id: str):
    query_params = dict(websocket.query_params)
    task_id = query_params.get("task_id")
    logging.info(f"Received request for /devtools/browser/{browser_id}?task_id={task_id}")
    
    port = await get_task_port(task_id, websocket)
    if port is None:
        return
        
    await websocket_browser_endpoint(websocket, browser_id, port)

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

async def start_browser(port):
    logging.info(f"Attempting to start browser on port {port}")
    
    try:
        p = await async_playwright().start()
        
        try:
            browser = await p.chromium.launch(
                headless=True,
                args=[
                    f'--remote-debugging-port={port}',
                    '--remote-allow-origins=*',
                    '--remote-debugging-address=0.0.0.0', 
                    '--no-sandbox'
                ]
            )
            
            logging.info(f"Browser launched successfully on port {port}")
            
            # Verify CDP is actually running
            try:
                async with aiohttp.ClientSession() as session:
                    async with session.get(f"http://127.0.0.1:{port}/json/version", timeout=5) as response:
                        if response.status == 200:
                            version_info = await response.json()
                            logging.info(f"CDP version info: {version_info}")
                        else:
                            logging.error(f"Failed to get CDP version. Status: {response.status}")
            except Exception as cdp_e:
                logging.error(f"Error checking CDP availability: {cdp_e}")
            
            # Create a global event to signal browser is ready
            browser_ready_event.set()
            
            # Keep the browser running indefinitely
            while True:
                try:
                    # Basic health check
                    contexts = browser.contexts
                    logging.debug(f"Active contexts: {len(contexts)}")
                    
                    # Periodic check of CDP availability
                    async with aiohttp.ClientSession() as session:
                        async with session.get(f"http://127.0.0.1:{port}/json/list", timeout=5) as response:
                            if response.status != 200:
                                logging.warning(f"CDP endpoint not responding on port {port}")
                    
                    await asyncio.sleep(60)
                
                except Exception as health_e:
                    logging.error(f"Browser health check error: {health_e}")
                    break
        
        except Exception as launch_e:
            logging.error(f"Failed to launch browser: {launch_e}")
            browser_ready_event.clear()
            raise
    
    except Exception as p_e:
        logging.error(f"Playwright initialization error: {p_e}")
        browser_ready_event.clear()
        raise
    finally:
        # Ensure Playwright is stopped
        try:
            await p.stop()
        except Exception as stop_e:
            logging.error(f"Error stopping Playwright: {stop_e}")

async def task_worker():
    while True:
        try:
            try:
                task_info = await asyncio.wait_for(task_queue.get(), timeout=60)
            except asyncio.TimeoutError:
                continue

            task_id = task_info['task_id']
            task_prompt = task_info['task']
            current_port = task_info['port']

            try:
                # Update task status to running
                active_tasks[task_id].update({
                    'status': 'running',
                    'started_at': datetime.now().isoformat()
                })

                task_results = []
                async for result in run_task(task_prompt, task_id, current_port):
                    task_results.append(result)
                    
                    try:
                        if isinstance(result, str):
                            result_data = json.loads(result)
                            if isinstance(result_data, dict) and 'status' in result_data:
                                active_tasks[task_id]['last_status'] = result_data['status']
                    except json.JSONDecodeError:
                        pass

                # Update task status to completed
                active_tasks[task_id].update({
                    'status': 'completed',
                    'completed_at': datetime.now().isoformat(),
                })

            except Exception as e:
                logging.error(f"Task {task_id} failed: {e}")
                # Update task status to failed
                active_tasks[task_id].update({
                    'status': 'failed',
                    'error': str(e),
                    'failed_at': datetime.now().isoformat()
                })

            finally:
                task_queue.task_done()

        except Exception as e:
            logging.error(f"Task worker error: {e}")

async def get_task_port(task_id: str, websocket: WebSocket = None) -> int:
    if not task_id:
        if websocket:
            await websocket.close(code=4000, reason="task_id is required")
            return None
        raise HTTPException(status_code=400, detail="task_id is required")

    if task_id not in active_tasks:
        if websocket:
            await websocket.close(code=4000, reason=f"Task ID {task_id} not found")
            return None
        raise HTTPException(status_code=404, detail=f"Task {task_id} not found")
    
    port = active_tasks[task_id].get('port')
    if not port:
        if websocket:
            await websocket.close(code=4000, reason=f"No port found for task ID {task_id}")
            return None
        raise HTTPException(status_code=500, detail=f"No port found for task {task_id}")
    
    return port

if __name__ == "__main__":
    check_llm_config()
    uvicorn.run(app, host="0.0.0.0", port=8000)
