from flask import Flask, render_template_string, jsonify
import requests
import json

app = Flask(__name__)

# API endpoint to get the list of pages from CDP
@app.route('/api/pages')
def get_pages():
    try:
        response = requests.get("https://scqevor9btoi2t6dnkcpg.apigateway-cn-beijing.volceapi.com/devtools/json/list", timeout=2) # Add timeout
        response.raise_for_status() # Raise HTTPError for bad responses (4xx or 5xx)
        pages = response.json()
        # Prepend the base URL to the devtoolsFrontendUrl
        base_url = "https://scqevor9btoi2t6dnkcpg.apigateway-cn-beijing.volceapi.com"
        for page in pages:
            if page.get("devtoolsFrontendUrl"):
                page["fullDevtoolsUrl"] = base_url + page["devtoolsFrontendUrl"]
        return jsonify(pages)
    except requests.exceptions.RequestException as e:
        print(f"Error fetching page list: {e}")
        return jsonify({"error": str(e)}), 500
    except json.JSONDecodeError as e:
        print(f"Error decoding JSON: {e}")
        return jsonify({"error": "Invalid JSON received from CDP endpoint"}), 500

# Main route to serve the viewer page
@app.route('/')
def index():
    html_template = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>CDP Page Viewer</title>
        <style>
            body { font-family: sans-serif; display: flex; height: 100vh; margin: 0; }
            #sidebar { width: 250px; border-right: 1px solid #ccc; padding: 10px; overflow-y: auto; flex-shrink: 0; }
            #devtools-container { flex-grow: 1; }
            #devtools-frame { width: 100%; height: 100%; border: none; }
            ul { list-style: none; padding: 0; }
            li { padding: 5px; cursor: pointer; border-bottom: 1px solid #eee; font-size: 0.9em; }
            li:hover { background-color: #f0f0f0; }
            .page-title { font-weight: bold; }
            .page-url { color: #555; font-size: 0.8em; word-break: break-all; }
            .page-type { font-style: italic; color: #888; font-size: 0.8em; }
            h2 { margin-top: 0; font-size: 1.2em; }
        </style>
    </head>
    <body>
        <div id="sidebar">
            <h2>Available Pages</h2>
            <button onclick="fetchPages()">Refresh List</button>
            <ul id="page-list"></ul>
        </div>
        <div id="devtools-container">
            <iframe id="devtools-frame" src="about:blank" title="DevTools Frame"></iframe>
        </div>

        <script>
            const pageList = document.getElementById('page-list');
            const devtoolsFrame = document.getElementById('devtools-frame');
            let knownPageIds = new Set(); // Track pages we've already seen
            let lastSelectedId = null; // Track the currently selected page

            async function fetchPages() {
                try {
                    const response = await fetch('/api/pages');
                    if (!response.ok) {
                        throw new Error(`HTTP error! status: ${response.status}`);
                    }
                    const pages = await response.json();
                    
                    if (pages.error) {
                        console.error("API Error:", pages.error);
                        pageList.innerHTML = '<li>Error loading pages. Is CDP running?</li>';
                        return;
                    }
                    
                    pageList.innerHTML = ''; // Clear existing list
                    if (pages.length === 0) {
                         pageList.innerHTML = '<li>No pages found.</li>';
                         return;
                    }

                    // Find new pages we haven't seen before
                    const newPages = pages.filter(page => !knownPageIds.has(page.id));
                    
                    // Sort pages with newest first (based on whether they're new to us)
                    const sortedPages = [...newPages, ...pages.filter(page => knownPageIds.has(page.id))];
                    
                    // Update our known page IDs
                    pages.forEach(page => knownPageIds.add(page.id));
                    
                    // Find the newest page (prioritize actual pages over iframes)
                    let newestPage = null;
                    if (newPages.length > 0) {
                        // First try to find a main page (not iframe)
                        newestPage = newPages.find(p => p.type === 'page');
                        // If no main page, take any new page
                        if (!newestPage) {
                            newestPage = newPages[0];
                        }
                    }

                    // Render all pages
                    sortedPages.forEach(page => {
                        if (!page.fullDevtoolsUrl) return; // Skip if no devtools URL

                        const li = document.createElement('li');
                        li.dataset.devtoolsUrl = page.fullDevtoolsUrl;
                        li.dataset.pageId = page.id;
                        
                        // Highlight if this is new
                        if (newPages.some(p => p.id === page.id)) {
                            li.style.backgroundColor = '#f0fff0'; // Light green for new pages
                        }
                        
                        // Mark selected page
                        if (page.id === lastSelectedId) {
                            li.style.fontWeight = 'bold';
                            li.style.borderLeft = '3px solid #007bff';
                        }
                        
                        li.onclick = () => {
                            // Update selected state
                            document.querySelectorAll('#page-list li').forEach(item => {
                                item.style.fontWeight = 'normal';
                                item.style.borderLeft = 'none';
                            });
                            li.style.fontWeight = 'bold';
                            li.style.borderLeft = '3px solid #007bff';
                            lastSelectedId = page.id;
                            
                            console.log("Loading DevTools:", page.fullDevtoolsUrl);
                            devtoolsFrame.src = page.fullDevtoolsUrl;
                        };
                        
                        const title = page.title || page.url || 'Untitled Page';
                        const type = page.type || 'unknown';
                        
                        li.innerHTML = `
                            <div class="page-title">${escapeHtml(title)}</div>
                            <div class="page-type">Type: ${escapeHtml(type)}</div>
                            <div class="page-url">${escapeHtml(page.url)}</div>
                        `;
                        pageList.appendChild(li);
                    });
                    
                    // Auto-select newest page if available
                    if (newestPage && newestPage.fullDevtoolsUrl) {
                        console.log("Auto-selecting newest page:", newestPage.title || newestPage.url);
                        lastSelectedId = newestPage.id;
                        devtoolsFrame.src = newestPage.fullDevtoolsUrl;
                        
                        // Highlight the selected item in the list
                        const selectedItem = document.querySelector(`li[data-page-id="${newestPage.id}"]`);
                        if (selectedItem) {
                            selectedItem.style.fontWeight = 'bold';
                            selectedItem.style.borderLeft = '3px solid #007bff';
                            // Scroll the item into view
                            selectedItem.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                        }
                    }
                } catch (error) {
                    console.error('Error fetching pages:', error);
                    pageList.innerHTML = '<li>Error loading pages. Check console.</li>';
                }
            }
            
            function escapeHtml(unsafe) {
                if (!unsafe) return '';
                return unsafe
                     .toString()
                     .replace(/&/g, "&amp;")
                     .replace(/</g, "&lt;")
                     .replace(/>/g, "&gt;")
                     .replace(/"/g, "&quot;")
                     .replace(/'/g, "&#039;");
             }

            // Fetch pages initially and then every 3 seconds (more frequent updates)
            fetchPages();
            setInterval(fetchPages, 3000);
        </script>
    </body>
    </html>
    """
    return render_template_string(html_template)

if __name__ == '__main__':
    # Run on a different port to avoid conflict with CDP
    app.run(host='127.0.0.1', port=9223, debug=True) 
