package com.example.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object ClipboardServer {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    var currentClipboard = ""
    
    // Callbacks for browser integration
    var onClipboardChanged: ((String) -> Unit)? = null
    var onTabAction: ((action: String, payload: String) -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    
    // Current tabs state for API responses
    var currentTabsJson = "[]"

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(8080)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                if (headerLine.lowercase().startsWith("content-length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                headerLine = reader.readLine()
            }
            
            val method = requestLine.split(" ")[0]
            val path = requestLine.split(" ")[1]
            
            var body = ""
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                reader.read(bodyChars, 0, contentLength)
                body = String(bodyChars)
            }
            
            // Basic Routing
            when {
                path == "/api/tabs" && method == "GET" -> {
                    sendResponse(writer, "200 OK", "Content-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n", currentTabsJson)
                }
                path == "/api/tabs/close" && method == "POST" -> {
                    val tabId = parseParam(body, "id")
                    onTabAction?.invoke("close", tabId)
                    sendResponse(writer, "200 OK", "Content-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n", "{\"status\":\"ok\"}")
                }
                path == "/api/tabs/open" && method == "POST" -> {
                    val url = parseParam(body, "url")
                    onTabAction?.invoke("open", url)
                    sendResponse(writer, "200 OK", "Content-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n", "{\"status\":\"ok\"}")
                }
                path == "/api/message" && method == "POST" -> {
                    // Endpoint for Offline Phone Assistant to send OTPs/SMS to this browser
                    val msg = parseParam(body, "text")
                    onMessageReceived?.invoke(msg)
                    sendResponse(writer, "200 OK", "Content-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n", "{\"status\":\"ok\"}")
                }
                path == "/update" && method == "POST" -> {
                    val text = parseParam(body, "text")
                    currentClipboard = text
                    onClipboardChanged?.invoke(text)
                    sendResponse(writer, "303 See Other", "Location: /\r\n")
                }
                path == "/" -> {
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Browser Sync</title>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: system-ui, -apple-system, sans-serif; padding: 20px; max-width: 800px; margin: 0 auto; background: #f4f4f5; }
                                textarea { width: 100%; height: 200px; padding: 16px; border-radius: 12px; border: 1px solid #ddd; font-size: 16px; margin-bottom: 20px; resize: vertical; box-sizing: border-box; }
                                input { width: 100%; padding: 14px; margin-bottom: 12px; border-radius: 8px; border: 1px solid #ddd; box-sizing: border-box; }
                                button { background: #007bff; color: white; border: none; padding: 14px 28px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; width: 100%; margin-bottom: 12px; }
                                button:hover { background: #0056b3; }
                                .card { background: white; padding: 32px; border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); margin-bottom: 20px; }
                                h2 { margin-top: 0; color: #1a1a1a; }
                                p { color: #666; margin-bottom: 24px; }
                                .tabs-container { border: 1px solid #ddd; border-radius: 8px; padding: 12px; max-height: 300px; overflow-y: auto; }
                                .tab-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid #eee; }
                                .tab-item:last-child { border-bottom: none; }
                                .tab-title { font-weight: 500; text-overflow: ellipsis; white-space: nowrap; overflow: hidden; max-width: 70%; }
                                .tab-close { background: #dc3545; padding: 6px 12px; width: auto; margin: 0; }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <h2>Remote Tab Manager</h2>
                                <form action="/api/tabs/open" method="POST" target="dummyframe" onsubmit="setTimeout(() => window.location.reload(), 500)">
                                    <input type="text" name="url" placeholder="https://example.com" required>
                                    <button type="submit">Open URL in Browser</button>
                                </form>
                                <h3>Open Tabs</h3>
                                <div class="tabs-container" id="tabs-list">Loading...</div>
                            </div>

                            <div class="card">
                                <h2>Clipboard Sync</h2>
                                <p>Type or paste your text here. It syncs instantly to your mobile browser's clipboard vault!</p>
                                <form action="/update" method="POST">
                                    <textarea name="text">${currentClipboard.replace("&", "&amp;").replace("<", "&lt;")}</textarea><br>
                                    <button type="submit">Sync to Mobile</button>
                                </form>
                            </div>
                            
                            <div class="card">
                                <h2>Send Message / OTP</h2>
                                <p>Simulate sending an SMS/OTP from your Phone Assistant app to the browser.</p>
                                <form action="/api/message" method="POST" target="dummyframe">
                                    <textarea name="text" style="height: 100px;" placeholder="Your OTP is 123456"></textarea>
                                    <button type="submit" style="background:#28a745;">Push Message</button>
                                </form>
                            </div>

                            <iframe name="dummyframe" id="dummyframe" style="display: none;"></iframe>
                            
                            <script>
                                fetch('/api/tabs')
                                    .then(res => res.json())
                                    .then(tabs => {
                                        const container = document.getElementById('tabs-list');
                                        container.innerHTML = '';
                                        if (tabs.length === 0) {
                                            container.innerHTML = '<p>No open tabs</p>';
                                        }
                                        tabs.forEach(tab => {
                                            const div = document.createElement('div');
                                            div.className = 'tab-item';
                                            
                                            const titleSpan = document.createElement('span');
                                            titleSpan.className = 'tab-title';
                                            titleSpan.textContent = (tab.title || tab.url || 'New Tab');
                                            
                                            const form = document.createElement('form');
                                            form.action = '/api/tabs/close';
                                            form.method = 'POST';
                                            form.target = 'dummyframe';
                                            form.onsubmit = () => setTimeout(() => window.location.reload(), 500);
                                            
                                            const input = document.createElement('input');
                                            input.type = 'hidden';
                                            input.name = 'id';
                                            input.value = tab.id;
                                            
                                            const btn = document.createElement('button');
                                            btn.className = 'tab-close';
                                            btn.textContent = 'Close';
                                            
                                            form.appendChild(input);
                                            form.appendChild(btn);
                                            
                                            div.appendChild(titleSpan);
                                            div.appendChild(form);
                                            
                                            container.appendChild(div);
                                        });
                                    });
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    sendResponse(writer, "200 OK", "Content-Type: text/html; charset=UTF-8\r\n", html)
                }
                else -> {
                    // Ignore favicon and others
                    sendResponse(writer, "404 Not Found", "", "Not found")
                }
            }
            
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun parseParam(body: String, key: String): String {
        val param = body.split("&").find { it.startsWith("$key=") }
        return if (param != null) {
            URLDecoder.decode(param.substringAfter("$key="), "UTF-8")
        } else {
            ""
        }
    }
    
    private fun sendResponse(writer: PrintWriter, status: String, headers: String, body: String = "") {
        writer.print("HTTP/1.1 $status\r\n")
        writer.print(headers)
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(body)
        writer.flush()
    }
}
