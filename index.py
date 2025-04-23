import uuid
from langchain_openai import ChatOpenAI
from browser_use import Agent, BrowserContextConfig
from browser_use.browser.browser import Browser, BrowserConfig
from browser_use.browser.context import BrowserContext
import asyncio
import logging
from dotenv import load_dotenv
from datetime import datetime
import os
from pathlib import Path
from fastapi import FastAPI, WebSocket
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import json
from typing import AsyncGenerator
import uvicorn
from cdp import get_websocket_version
from utils import enforce_log_format, check_llm_config, ModelLoggingCallback
from contextlib import asynccontextmanager
from cdp import websocket_endpoint, websocket_browser_endpoint, get_websocket_targets, get_websocket_version, get_inspector
from utils import enforce_log_format, check_llm_config
from browser import start_browser
from task import TaskManager

app = FastAPI()
load_dotenv()

llm_openai = "openai"
llm_deepseek = "deepseek"
llm_ark = "ark"
llm_name = llm_openai


# Global variable to track the port
CURRENT_CDP_PORT = 9222

# Global task queue and task storage
taskManager = TaskManager()


def format_sse(data: dict) -> str:
    """Format data as SSE message"""
    message = f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    return message


async def new_step_callback(state, model_output, step_number):
    if model_output:
        conversation_update = {
            "step": step_number-1,  # need to minus 1 to refect actual step number
            "goal": model_output.current_state.next_goal if hasattr(model_output.current_state, "next_goal") else "",
            "memory": model_output.current_state.memory if hasattr(model_output.current_state, "memory") else "",
            "evaluation": model_output.current_state.evaluation_previous_goal if hasattr(model_output.current_state, "evaluation_previous_goal") else "",
        }
        if hasattr(model_output, "action"):
            conversation_update["actions"] = [a.model_dump(exclude_none=True)
                                              for a in model_output.action]
        return conversation_update


async def run_task(task: str, task_id: str, current_port: int) -> AsyncGenerator[str, None]:
    logging.info(
        f"[{task_id}] Starting task with prompt: '{task}', CDP port: {current_port}")

    browser = None
    context = None
    agent = None
    agent_task = None

    # Send initial status and update task
    taskManager.update_task(task_id, {
        'status': 'starting',
        'started_at': datetime.now().isoformat()
    })
    yield format_sse({"task_id": task_id, "status": "started"})

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
                cdp_url=f"http://127.0.0.1:{current_port}"
            )
        )

        yield format_sse({"task_id": task_id, "status": "browser_initialized"})
        taskManager.update_task(task_id, {
            'status': 'browser_initialized',
            'last_update': datetime.now().isoformat()
        })
        logging.info(
            f"[{task_id}] Browser initialized on port {current_port}")

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

        async def new_progress_callback(msg: str):
            taskManager.update_task(task_id, {
                'status': 'running',
            })
            # Create and send conversation update without yielding
            conv_message = format_sse(
                {
                    "task_id": task_id,
                    "status": "conversation_update",
                    "metadata": {
                        "type": "message",
                        "data": {
                            "message": msg
                        }
                    }
                }
            )
            asyncio.create_task(send_sse_message(conv_message))

        async def new_step_callback_wrapper(state, model_output, step_number):
            conversation_update = await new_step_callback(state, model_output, step_number)
            # Update active_tasks with current step and goal
            taskManager.update_task(task_id, {
                'status': 'running',
            })
            # Create and send conversation update without yielding
            conv_message = format_sse(
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

        # setup a message queue to pass intermediate result
        async def send_sse_message(message):
            nonlocal sse_queue
            await sse_queue.put(message)
        sse_queue = asyncio.Queue()

        # the real browser-use agent
        logging.info(
            f"Creating agent with task: {task}, llm: {llm_name}, task_id: {task_id}")
        try:
            if llm_name == llm_openai:
                logging.info(
                    f"[{task_id}] Creating OpenAI agent for task: {task}")
                agent = Agent(
                    task=task,
                    llm=ChatOpenAI(model="gpt-4o"),
                    use_vision=True,
                    browser_context=context,
                    # save_conversation_path=os.path.join(base_dir, "conversation"),
                    # generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                    register_new_step_callback=new_step_callback_wrapper,
                )
            elif llm_name == llm_ark:
                logging.info(
                    f"[{task_id}] Creating Ark agent for task: {task}")
                # It's a workaround as ChatOpenAI will check the api key
                os.environ["OPENAI_API_KEY"] = "sk-dummy"

                agent = Agent(
                    task=task,
                    initial_actions=[
                        {"go_to_url": {"url": "https://baidu.com"}}],
                    llm=ChatOpenAI(
                        base_url="https://ark.cn-beijing.volces.com/api/v3",
                        model=os.getenv("ARK_MODEL_ID"),
                        api_key=os.getenv("ARK_API_KEY"),
                        default_headers={
                            "X-Client-Request-Id": "vefaas-browser-use-20250403"},
                        callbacks=[ModelLoggingCallback()],
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
                    register_new_step_callback=new_step_callback_wrapper,
                    register_new_progress_callback=new_progress_callback
                )
            else:
                raise ValueError(f"Unknown LLM type: {llm_name}")

        except Exception as e:
            logging.error(f"Failed to create agent: {str(e)}")
            yield format_sse({
                "task_id": task_id,
                "status": "error",
                "error": f"Agent creation failed: {str(e)}"
            })
            taskManager.update_task(task_id, {
                'status': 'failed',
                'error': f"Agent creation failed: {str(e)}",
                'failed_at': datetime.now().isoformat()
            })

            logging.info('closing playwright driver and browser')
            browser_cdp = taskManager.get_task_by_id(task_id)['browser']
            if browser_cdp:
                await browser_cdp.stop()

            return

        yield format_sse({"task_id": task_id, "status": "agent_initialized"})
        taskManager.update_task(task_id, {
            'status': 'agent_initialized',
            'last_update': datetime.now().isoformat()
        })
        logging.info(f"[{task_id}] Agent initialized and ready to run")

        # Start the agent in a separate async task
        agent_task = asyncio.create_task(agent.run(20))
        logging.info(f"[{task_id}] Agent started running")

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

        # task completed
        yield format_sse({
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
        taskManager.update_task(task_id, {
            'status': 'completed',
            'completed_at': datetime.now().isoformat(),
            'result': final_result
        })
        logging.info(f"[{task_id}] Task completed successfully")
    except Exception as e:
        logging.error(f"[{task_id}] Agent execution failed: {str(e)}")
        yield format_sse({
            "task_id": task_id,
            "status": "error",
            "error": f"Agent execution failed: {str(e)}"
        })
        taskManager.update_task(task_id, {
            'status': 'failed',
            'error': f"Agent execution failed: {str(e)}",
            'failed_at': datetime.now().isoformat()
        })
    finally:
        # some cleanup work
        async def cleanup():
            agent.stop()
            await agent_task
            try:
                if context:
                    await context.close()
                browser_cdp = taskManager.get_task_by_id(task_id)['browser']
                if browser_cdp:
                    await browser_cdp.stop()
            except Exception as e:
                logging.error(f"Failed to close browser/context: {str(e)}")

        # put cleanup in a new task so it won't block the main loop
        asyncio.create_task(cleanup())


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

    # TODO: kuoxin@ refine this logic
    # Increment the port for each task
    CURRENT_CDP_PORT += 1
    current_port = CURRENT_CDP_PORT

    browser = await start_browser(current_port)
    taskManager.add_task(task_id, {
        'prompt': prompt,
        'port': current_port,
        'status': 'queued',
        'created_at': datetime.now().isoformat(),
        'browser': browser,
    })

    # Return task ID immediately
    return {
        "task_id": task_id,
        "status": "queued"
    }


@app.get("/tasks/{task_id}/stream")
async def stream_task_results(task_id: str):
    """Stream results for a specific task"""
    port = await taskManager.get_task_port(task_id)

    async def result_generator():
        task_info = taskManager.get_task_by_id(task_id)
        async for result in run_task(task_info['prompt'], task_id, port):
            yield format_sse({
                "task_id": task_id,
                "data": result
            })

    return StreamingResponse(result_generator(), media_type="text/event-stream")


@app.get("/tasks/{task_id}/devtools/json/version")
async def json_version(task_id: str):
    logging.info(
        f"Received request for /devtools/json/version with task_id: {task_id}")
    logging.info(f"active_tasks: {taskManager.get_active_tasks}")

    port = await taskManager.get_task_port(task_id)
    return await get_websocket_version(port)


@app.websocket("/tasks/{task_id}/devtools/browser/{browser_id}")
async def cdp_websocket_browser(websocket: WebSocket, task_id: str, browser_id: str):
    logging.info(
        f"Received request for /devtools/browser/{browser_id}?task_id={task_id}")

    port = await taskManager.get_task_port(task_id, websocket)
    if port is None:
        return

    await websocket_browser_endpoint(websocket, browser_id, port)


async def cleanup_stale_tasks():
    logging.info("Starting stale task cleanup service")
    
    while True:
        try:
            current_time = datetime.now()
            stale_tasks = []
            
            # Find all queued tasks that are older than 5 minutes
            for task_id, task_info in taskManager.get_active_tasks().items():
                if task_info.get('status') == 'queued':
                    created_at = datetime.fromisoformat(task_info.get('created_at', ''))
                    time_diff = (current_time - created_at).total_seconds()
                    
                    # If task has been queued for more than 5 minutes
                    if time_diff > 300:  # 300 seconds = 5 minutes
                        stale_tasks.append(task_id)
            
            # Clean up each stale task
            for task_id in stale_tasks:
                logging.warning(f"Cleaning up stale queued task: {task_id}")
                task_info = taskManager.get_task_by_id(task_id)
                
                if task_info and 'browser' in task_info:
                    browser_instance = task_info['browser']
                    if browser_instance:
                        try:
                            logging.info(f"Closing browser for stale task: {task_id}")
                            await browser_instance.stop()
                        except Exception as e:
                            logging.error(f"Error closing browser for stale task {task_id}: {e}")
                
                taskManager.remove_task(task_id)
                
                logging.info(f"Stale task {task_id} cleaned up")
        
        except Exception as e:
            logging.error(f"Error in stale task cleanup: {e}")
        
        await asyncio.sleep(60)


if __name__ == "__main__":
    llm_name = check_llm_config()
    enforce_log_format()
    
    @asynccontextmanager
    async def lifespan(app):
        cleanup_task = asyncio.create_task(cleanup_stale_tasks())
        app.state.cleanup_task = cleanup_task
        logging.info("Stale task cleanup service started")
        yield
        if not cleanup_task.done():
            cleanup_task.cancel()
    
    app.router.lifespan_context = lifespan
    
    uvicorn.run(app, host="0.0.0.0", port=8000)
