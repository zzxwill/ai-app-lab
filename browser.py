import logging
import aiohttp
import asyncio
from playwright.async_api import async_playwright
from playwright._impl._browser import Browser
from playwright.async_api._generated import Playwright as AsyncPlaywright

# TODO kuoxin@: delete this
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
browser_ready_event = asyncio.Event()


class BrowserWrapper:
    def __init__(self, port, browser: Browser, playwright: AsyncPlaywright):
        self.port = port
        self.browser = browser
        self.playwright = playwright

    def stop(self):
        print("kuoxin@: stop")
        # TODO: kuoxin@ why bother close and stop?
        if self.browser:
            logging.info(f"Closing browser on port {self.port}...")
            self.browser.close()
            logging.info(f"Browser on port {self.port} closed successfully")
        if self.playwright:
            logging.info(f"Closing playwright session {self.port}...")
            self.playwright.stop()
            logging.info(
                f"Paywright session on port {self.port} closed successfully")


async def start_browser(port):
    print("kuoxin@: start")
    logging.info(f"Attempting to start browser on port {port}")
    p = None
    browser = None

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
                            logging.info(
                                f"CDP version info: {version_info}")
                        else:
                            logging.error(
                                f"Failed to get CDP version. Status: {response.status}")
            except Exception as cdp_e:
                logging.error(f"Error checking CDP availability: {cdp_e}")

            # await asyncio.sleep(500)
            # Create a global event to signal browser is ready
            # browser_ready_event.set()

            # task_id = None
            # for tid, task in active_tasks.items():
            #     if task.get('port') == port:
            #         task_id = tid
            #         break

            # if task_id:
            #     active_tasks[task_id]['browser_instance'] = browser
            #     active_tasks[task_id]['playwright_instance'] = p

            # # Keep the browser running until the task is done
            # while True:
            #     is_active = False
            #     for tid, task in active_tasks.items():
            #         if task.get('port') == port and task.get('status') not in ['completed', 'failed']:
            #             logging.info(
            #                 f"kuoxin@ task status { task.get('status')}")
            #             is_active = True
            #             break

            #     if not is_active:
            #         logging.info(
            #             f"No active tasks for browser on port {port}, shutting down")
            #         break

            #     try:
            #         # Basic health check
            #         contexts = browser.contexts
            #         logging.debug(f"Active contexts: {len(contexts)}")

            #         # Periodic check of CDP availability
            #         async with aiohttp.ClientSession() as session:
            #             async with session.get(f"http://127.0.0.1:{port}/json/list", timeout=5) as response:
            #                 if response.status != 200:
            #                     logging.warning(
            #                         f"CDP endpoint not responding on port {port}")

            #         await asyncio.sleep(5)

            #     except Exception as health_e:
            #         logging.error(
            #             f"Browser health check error: {health_e}")
            #         break
            return BrowserWrapper(port, browser, p)

        except Exception as launch_e:
            logging.error(f"Failed to launch browser: {launch_e}")
            browser_ready_event.clear()
            raise
    except Exception as p_e:
        logging.error(f"Playwright initialization error: {p_e}")
        browser_ready_event.clear()
        try:
            if browser:
                logging.info(f"Closing browser on port {port}...")
                await browser.close()
                logging.info(f"Browser on port {port} closed successfully")

            if p:
                await p.stop()
                logging.info(
                    f"Playwright for browser on port {port} stopped successfully")
        except Exception as stop_e:
            logging.error(f"Error stopping browser/playwright: {stop_e}")
        raise
