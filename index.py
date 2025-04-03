import uuid
from langchain_openai import ChatOpenAI
from browser_use import Agent, BrowserContextConfig, SystemPrompt
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
import uvicorn

app = FastAPI()

llm_openai = "openai"
llm_deepseek = "deepseek"
llm_ark = "ark"

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

logging.getLogger('browser_use').setLevel(logging.DEBUG)

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

        gif_dir = os.path.join(base_dir, "gif")
        Path(gif_dir).mkdir(parents=True, exist_ok=True)

        recording_dir = os.path.join(base_dir, "recording")
        Path(recording_dir).mkdir(parents=True, exist_ok=True)

        trace_dir = os.path.join(base_dir, "trace")
        Path(trace_dir).mkdir(parents=True, exist_ok=True)

        try:
        #     browser = Browser(
        #     config=BrowserConfig(
        #         headless=True,
        #         extra_browser_args=[
        #             '--no-sandbox',
        #             '--disable-dev-shm-usage',
        #             '--disable-gpu',
        #             '--disable-software-rasterizer'
        #         ]
        #     )
        # )
            
            browser = Browser(
                config=BrowserConfig(
                    headless=True,
                    disable_security=True,
                    # deterministic_rendering=True
                )
            )

            yield await format_sse({"task_id": task_id, "status": "browser_initialized"})

            config = BrowserContextConfig(
                save_recording_path=recording_dir,
                save_downloads_path=os.path.join(base_dir, "download"),
                trace_path=os.path.join(trace_dir, f"{task_id}.zip"),
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

            use_vision = True
            


            logging.info(f"Creating agent with task: {task}, llm: {llm_name}, task_id: {task_id}")

            try:
                # if llm_name != llm_openai:
                #     os.environ["OPENAI_API_KEY"] = "sk-dummy"

                

                if llm_name == llm_openai:
                #     agent = Agent(
                #         task=task,
                #         llm=ChatOpenAI(model="gpt-4o"),
                #         use_vision=True,
                #         browser_context=context,
                #         save_conversation_path=os.path.join(base_dir, "conversation"),
                #         generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                #         register_new_step_callback=new_step_callback,
                #     )
                # # elif llm_name == llm_deepseek:
                # #     llm = ChatDeepSeek(model="deepseek-chat", api_key=os.getenv("DEEPSEEK_API_KEY"))
                # #     use_vision = False
                    class BaiduSystemPrompt(SystemPrompt):
                        action_description = """IMPORTANT: You must ALWAYS use Baidu.com for ALL searches. 
                        1. NEVER use Google or any other search engine
                        2. ALWAYS start by navigating to https://www.bing.com
                        3. Use Baidu's search box for all searches
                        4. This is a strict requirement - you must use Baidu.com"""

                    # Modify task to enforce Baidu usage
                    baidu_task = f"Remember to use ONLY bing.com for searching. Task: {task}"

                    agent = Agent(
                        task=baidu_task,
                        llm=ChatOpenAI(model="gpt-4o"),
                        use_vision=True,
                        browser_context=context,
                        save_conversation_path=os.path.join(base_dir, "conversation"),
                        generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                        register_new_step_callback=new_step_callback,
                        system_prompt_class=BaiduSystemPrompt
                    )
                elif llm_name == llm_deepseek:
                    class BaiduSystemPrompt(SystemPrompt):
                        action_description = """IMPORTANT: You must ALWAYS use Baidu.com for ALL searches. 
                        1. NEVER use Google or any other search engine
                        2. ALWAYS start by navigating to https://www.baidu.com
                        3. Use Baidu's search box for all searches
                        4. This is a strict requirement - you must use Baidu.com"""

                    # Modify task to enforce Baidu usage
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
                        save_conversation_path=os.path.join(base_dir, "conversation"),
                        generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                        register_new_step_callback=new_step_callback,
                        system_prompt_class=BaiduSystemPrompt
                    )
                elif llm_name == llm_ark:  
                    os.environ["OPENAI_API_KEY"] = "sk-dummy"
                    class BaiduSystemPrompt(SystemPrompt):
                        action_description = """IMPORTANT: You must ALWAYS use Baidu.com for ALL searches. 
                        1. NEVER use Google or any other search engine
                        2. ALWAYS start by navigating to https://www.baidu.com
                        3. Use Baidu's search box for all searches
                        4. This is a strict requirement - you must use Baidu.com"""

                    # Modify task to enforce Baidu usage
                    baidu_task = f"Remember to use ONLY baidu.com for searching. Task: {task}"

                    agent = Agent(
                        task=baidu_task,
                        llm=ChatOpenAI(
                            base_url="https://ark.cn-beijing.volces.com/api/v3",
                            model=os.getenv("ARK_MODEL_ID"),
                            api_key=os.getenv("ARK_API_KEY"),
                            default_headers={"X-Client-Request-Id": "vefaas-browser-use-20250403"}
                        ),
                        use_vision=False,
                        browser_context=context,
                        save_conversation_path=os.path.join(base_dir, "conversation"),
                        generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                        register_new_step_callback=new_step_callback,
                        system_prompt_class=BaiduSystemPrompt
                    )
                else:
                    raise ValueError(f"Unknown LLM type: {llm_name}")

                # agent = Agent(
                #     task=baidu_task,
                #     llm=llm,
                #     use_vision=use_vision,
                #     browser_context=context,
                #     save_conversation_path=os.path.join(base_dir, "conversation"),
                #     generate_gif=os.path.join(gif_dir, "screenshots.gif"),
                #     register_new_step_callback=new_step_callback,
                #     system_prompt_class=BaiduSystemPrompt
                # )
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
    
if __name__ == "__main__":
    check_llm_config()
    uvicorn.run(app, host="0.0.0.0", port=8000)