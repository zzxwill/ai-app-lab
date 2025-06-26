import asyncio
import logging
import os
import aiohttp
from playwright.async_api import async_playwright
from playwright.async_api import Browser
from playwright.async_api._generated import Playwright as AsyncPlaywright

browser_ready_event = asyncio.Event()


class BrowserWrapper:
    def __init__(self, port, browser: Browser, playwright: AsyncPlaywright):
        self.port = port
        self.browser = browser
        self.playwright = playwright

    async def stop(self):
        if self.browser:
            logging.info(f"Closing browser on port {self.port}...")
            # try:
            #     for context in self.browser.contexts:
            #         await context.close()
            # except Exception as e:
            #     logging.error(f"Error closing contexts: {e}")
            await self.browser.close()
            logging.info(f"Browser on port {self.port} closed successfully")
        if self.playwright:
            logging.info(f"Closing playwright session {self.port}...")
            await self.playwright.stop()
            logging.info(
                f"Paywright session on port {self.port} closed successfully")


async def start_browser(port):
    logging.info(f"Attempting to start browser on port {port}")
    p = None
    browser = None

    try:
        p = await async_playwright().start()

        w = BrowserWrapper(port, None, p)

        try:
            # Configure proxy if environment variables are set
            proxy_config = None
            proxy_server = os.getenv('PROXY_SERVER')
            if proxy_server:
                proxy_config = {
                    'server': proxy_server,
                    'bypass': '127.0.0.1,localhost'
                }

            browser = await p.chromium.launch(
                # executable_path="/opt/chromium.org/browser_use/chromium/chromium-browser-use",
                headless=False,
                args=[
                    f'--remote-debugging-port={port}',
                    '--remote-allow-origins=*',
                    '--remote-debugging-address=0.0.0.0',
                    '--no-sandbox'
                ],
                proxy=proxy_config
            )

            w = BrowserWrapper(port, browser, p)

            logging.info(f"Browser launched successfully on port {port}")

            # Verify CDP is actually running
            try:
                async with aiohttp.ClientSession() as session:
                    async with session.get(f"http://127.0.0.1:{port}/json/version", timeout=5) as response:
                        if response.status == 200:
                            version_info = await response.json()
                            logging.info(
                                f"successfully connected to cdp: {version_info}")
                        else:
                            logging.error(
                                f"Failed to get CDP version. Status: {response.status}")
            except Exception as cdp_e:
                logging.error(f"Error checking CDP availability: {cdp_e}")

                logging.info('closing playwright driver and browser')
                await w.stop()
                raise
            return BrowserWrapper(port, browser, p)

        except Exception as launch_e:
            logging.error(f"Failed to launch browser: {launch_e}")
            browser_ready_event.clear()

            logging.info('closing playwright driver and browser')
            await w.stop()
            raise
    except Exception as p_e:
        logging.error(f"Playwright initialization error: {p_e}")
        browser_ready_event.clear()

        logging.info('closing playwright driver and browser')
        await w.stop()
        raise
