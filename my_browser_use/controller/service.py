import logging
import re

from browser_use.browser import BrowserSession
from browser_use.controller.views import SearchGoogleAction
from browser_use.agent.views import ActionResult
from browser_use.controller.service import Controller
from bs4 import BeautifulSoup
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.prompts import PromptTemplate
import markdownify
from playwright.async_api import Page
from pydantic import BaseModel

from my_browser_use.controller.views import PauseAction
from my_browser_use.i18n import _

logger = logging.getLogger(__name__)


class MyController(Controller):
    """Custom controller extending base Controller with additional actions.
    
    Features:
    - Inherits core controller functionality
    - Adds custom pause action handler
    - Maintains action registry with exclusion support
    """

    def __init__(
        self,
        exclude_actions: list[str] = [],
        output_model: type[BaseModel] | None = None,
    ):
        super().__init__(exclude_actions, output_model)

        # Basic Navigation Actions
        @self.registry.action(
            _('Search the query in Baidu in the current tab, the query should be a search query like humans search in Baidu, concrete and not vague or super long. More the single most important items.'),
            param_model=SearchGoogleAction,
        )
        async def search_google(params: SearchGoogleAction, browser_session: BrowserSession):
            search_url = f'https://www.baidu.com/s?wd={params.query}'
            
            page = await browser_session.get_current_page()
            await page.goto(search_url)
            await page.wait_for_load_state()
            msg = _('🔍 Searched for "{query}" in Baidu').format(query=params.query)
            logger.info(msg)
            return ActionResult(extracted_content=msg, include_in_memory=True)
        
        @self.registry.action(
            _('Pause agent'),
            param_model=PauseAction,
        )
        async def pause(params: PauseAction):
            msg = _('👩 Pause agent, reason: {reason}').format(reason=params.reason)
            logger.info(msg)
            return ActionResult(extracted_content=msg, include_in_memory=True)

        # Content Actions
        @self.registry.action(
            _('Extract page content to retrieve specific information from the page, e.g. all company names, a specific description, all information about xyc, 4 links with companies in structured format. Use include_links true if the goal requires links'),
        )
        async def extract_content(
            goal: str,
            page: Page,
            page_extraction_llm: BaseChatModel,
            include_links: bool = False,
        ):
            raw_content = await page.content()
            soup = BeautifulSoup(
                raw_content, 'html.parser')
            # remove all unnecessary http metadata
            for s in soup.select('script'):
                s.decompose()
            for s in soup.select('style'):
                s.decompose()
            for s in soup.select('textarea'):
                s.decompose()
            for s in soup.select('img'):
                s.decompose()
            for s in soup.find_all(style=re.compile("background-image.*")):
                s.decompose()
            content = markdownify.markdownify(str(soup))

            # manually append iframe text into the content so it's readable by the LLM (includes cross-origin iframes)
            for iframe in page.frames:
                if iframe.url != page.url and not iframe.url.startswith('data:'):
                    content += f'\n\nIFRAME {iframe.url}:\n'
                    content += markdownify.markdownify(await iframe.content())

            prompt = _('Your task is to extract the content of the page. You will be given a page and a goal and you should extract all relevant information around this goal from the page. If the goal is vague, summarize the page. Respond in json format. Extraction goal: {goal}, Page: {page}')
            template = PromptTemplate(input_variables=['goal', 'page'], template=prompt)
            try:
                output = await page_extraction_llm.ainvoke(template.format(goal=goal, page=content))
                msg = _('📄 Extracted from page\n: {content}\n').format(content=output.content)
                logger.info(msg)
                return ActionResult(extracted_content=msg, include_in_memory=True)
            except Exception as e:
                logger.debug(_('Error extracting content: {error}').format(error=e))
                msg = _('📄 Extracted from page\n: {content}\n').format(content=content)
                logger.info(msg)
                return ActionResult(extracted_content=msg)

