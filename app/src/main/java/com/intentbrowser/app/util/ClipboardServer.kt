package com.intentbrowser.app.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID

/**
 * "Local Hub": clipboard sync + remote tab control over LAN.
 *
 * Security model (was: ZERO auth — anyone on the Wi-Fi could read your tabs,
 * open/close them, pop dialogs on your screen, and write your clipboard):
 * - The server shows a 4-digit PIN on the phone.
 * - A connecting device pairs once via POST /api/pair {pin} and receives a
 *   session token. Every other endpoint requires that token.
 * - Each client is handled on its own thread (was: synchronous accept loop —
 *   one slow client hung the whole server).
 */
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

    private val hostPin = (1000..9999).random().toString()
    private val activeTokens = mutableSetOf<String>()

    fun getPin(): String = hostPin

    // ---- Drop E2EE state ----
    private var dropKeyPair: java.security.KeyPair? = null
    private var dropAesKey: javax.crypto.spec.SecretKeySpec? = null
    private val dropOutbox = ArrayDeque<String>()
    private val dropLock = Object()

    // Follow mode: one-to-many navigation broadcast. Followers long-poll with
    // ?since=<seq>; each gets the latest URL newer than its seq, so no event
    // is lost no matter how many followers are connected.
    private val navLock = Object()
    private var navSeq = 0
    private var lastNavUrl: String? = null
    /** Fired when an encrypted drop arrives from a paired device (ciphertext). */
    var onDropReceived: ((String) -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        activeTokens.clear()
        dropAesKey = null
        dropOutbox.clear()
        try {
            dropKeyPair = DropCrypto.generateKeyPair()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Thread {
            try {
                serverSocket = ServerSocket(8080)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    // Per-client thread: one slow client must not block the server.
                    Thread { handleClient(socket) }.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        activeTokens.clear()
        dropAesKey = null
        dropKeyPair = null
        synchronized(dropLock) { dropOutbox.clear() }
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

            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]
            val route = fullPath.substringBefore("?")
            val query = fullPath.substringAfter("?", "")

            var body = ""
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(bodyChars, read, contentLength - read)
                    if (n == -1) break
                    read += n
                }
                body = String(bodyChars, 0, read)
            }

            when {
                route == "/api/pair" && method == "POST" -> {
                    val pin = parseParam(body, "pin")
                    if (pin == hostPin) {
                        val token = UUID.randomUUID().toString()
                        synchronized(activeTokens) { activeTokens.add(token) }
                        sendResponse(
                            writer, "200 OK", jsonHeaders(),
                            "{\"status\":\"ok\",\"token\":\"$token\"}"
                        )
                    } else {
                        sendResponse(
                            writer, "401 Unauthorized", jsonHeaders(),
                            "{\"status\":\"error\",\"message\":\"Invalid PIN\"}"
                        )
                    }
                }
                route == "/" -> {
                    sendResponse(writer, "200 OK", "Content-Type: text/html; charset=UTF-8\r\n", webUi())
                }
                !authorized(query, body) -> {
                    sendResponse(
                        writer, "401 Unauthorized", jsonHeaders(),
                        "{\"status\":\"error\",\"message\":\"Pair this device first (enter the PIN shown on the phone)\"}"
                    )
                }
                route == "/api/drop/pubkey" && method == "GET" -> {
                    val pub = try {
                        dropKeyPair?.let { DropCrypto.publicKeyToBase64(it) }
                    } catch (e: Exception) { null }
                    if (pub != null) {
                        sendResponse(writer, "200 OK", jsonHeaders(), "{\"pubkey\":\"" + pub + "\"}")
                    } else {
                        sendResponse(writer, "500 Internal Server Error", jsonHeaders(), "{\"status\":\"error\"}")
                    }
                }
                route == "/api/drop/pubkey" && method == "POST" -> {
                    val clientKey = parseParam(body, "key")
                    try {
                        val pair = dropKeyPair ?: throw IllegalStateException("no keypair")
                        dropAesKey = DropCrypto.deriveAesKey(pair, clientKey)
                        sendResponse(writer, "200 OK", jsonHeaders(), "{\"status\":\"ok\"}")
                    } catch (e: Exception) {
                        sendResponse(writer, "400 Bad Request", jsonHeaders(), "{\"status\":\"error\",\"message\":\"bad key\"}")
                    }
                }
                route == "/api/drop/send" && method == "POST" -> {
                    val payload = parseParam(body, "payload")
                    if (payload.isNotBlank()) {
                        onDropReceived?.invoke(payload)
                        sendResponse(writer, "200 OK", jsonHeaders(), "{\"status\":\"ok\"}")
                    } else {
                        sendResponse(writer, "400 Bad Request", jsonHeaders(), "{\"status\":\"error\"}")
                    }
                }
                route == "/api/drop/poll" && method == "GET" -> {
                    // Long-poll: phone->PC drops. Waits up to 25s for the next payload.
                    val payload = synchronized(dropLock) {
                        if (dropOutbox.isEmpty()) {
                            try { dropLock.wait(25000) } catch (_: InterruptedException) { }
                        }
                        if (dropOutbox.isNotEmpty()) dropOutbox.removeFirst() else null
                    }
                    val json = if (payload != null) {
                        "{\"payload\":\"" + payload.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"
                    } else {
                        "{\"payload\":null}"
                    }
                    sendResponse(writer, "200 OK", jsonHeaders(), json)
                }
                route == "/api/follow/poll" && method == "GET" -> {
                    // Follow mode long-poll: returns the newest navigation newer
                    // than ?since=<seq>, waiting up to ~25s. Late joiners pass
                    // since=0 and immediately get the current page.
                    val since = parseParam(query, "since").toIntOrNull() ?: 0
                    var url: String?
                    var seq: Int
                    val deadline = System.currentTimeMillis() + 25000
                    synchronized(navLock) {
                        while (navSeq <= since && System.currentTimeMillis() < deadline) {
                            val remaining = deadline - System.currentTimeMillis()
                            if (remaining <= 0) break
                            try { navLock.wait(remaining.coerceAtMost(5000)) } catch (_: InterruptedException) { }
                        }
                        url = if (navSeq > since) lastNavUrl else null
                        seq = navSeq
                    }
                    val json = if (url != null) {
                        "{\"url\":\"" + url!!.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"seq\":$seq}"
                    } else {
                        "{\"url\":null,\"seq\":$seq}"
                    }
                    sendResponse(writer, "200 OK", jsonHeaders(), json)
                }
                route == "/api/tabs" && method == "GET" -> {
                    sendResponse(writer, "200 OK", jsonHeaders(), currentTabsJson)
                }
                route == "/api/tabs/close" && method == "POST" -> {
                    val tabId = parseParam(body, "id")
                    onTabAction?.invoke("close", tabId)
                    sendResponse(writer, "200 OK", jsonHeaders(), "{\"status\":\"ok\"}")
                }
                route == "/api/tabs/open" && method == "POST" -> {
                    val url = parseParam(body, "url")
                    onTabAction?.invoke("open", url)
                    sendResponse(writer, "200 OK", jsonHeaders(), "{\"status\":\"ok\"}")
                }
                route == "/api/message" && method == "POST" -> {
                    val msg = parseParam(body, "text")
                    onMessageReceived?.invoke(msg)
                    sendResponse(writer, "200 OK", jsonHeaders(), "{\"status\":\"ok\"}")
                }
                route == "/update" && method == "POST" -> {
                    val text = parseParam(body, "text")
                    currentClipboard = text
                    onClipboardChanged?.invoke(text)
                    sendResponse(writer, "303 See Other", "Location: /?token=${parseParam(query, "token")}\r\n")
                }
                else -> {
                    sendResponse(writer, "404 Not Found", "", "Not found")
                }
            }

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun authorized(query: String, body: String): Boolean {
        val token = parseParam(query, "token").ifBlank { parseParam(body, "token") }
        if (token.isBlank()) return false
        synchronized(activeTokens) { return activeTokens.contains(token) }
    }

    private fun jsonHeaders() = "Content-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n"

    private fun parseParam(source: String, key: String): String {
        val param = source.split("&").find { it.startsWith("$key=") }
        return if (param != null) {
            try {
                URLDecoder.decode(param.substringAfter("$key="), "UTF-8")
            } catch (_: Exception) {
                ""
            }
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

    /** Encrypt a phone->PC drop. Null when no paired client has completed ECDH. */
    fun dropEncrypt(plain: String): String? = try {
        val key = dropAesKey ?: return null
        DropCrypto.encrypt(key, plain)
    } catch (e: Exception) { null }

    /** Decrypt a PC->phone drop. Null on failure (wrong key / tampered). */
    fun decryptDrop(payload: String): String? = try {
        val key = dropAesKey ?: return null
        DropCrypto.decrypt(key, payload)
    } catch (e: Exception) { null }

    /**
     * Broadcast a navigation to followers. Called by the app when leading and
     * the active tab commits a new http(s) URL.
     */
    fun broadcastNavigation(url: String) {
        if (!url.startsWith("http")) return
        synchronized(navLock) {
            navSeq++
            lastNavUrl = url
            navLock.notifyAll()
        }
    }

    /** Queue an encrypted payload for the paired PC (it long-polls /api/drop/poll). */
    fun dropToClient(encryptedPayload: String) {
        synchronized(dropLock) {
            dropOutbox.addLast(encryptedPayload)
            dropLock.notifyAll()
        }
    }

    /** Pairing gate + main UI. The token lives in localStorage after PIN pairing. */
    private fun webUi(): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Intent Browser — Local Hub</title>
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
                .pin-input { font-size: 24px; text-align: center; letter-spacing: 8px; }
                .error { color: #dc3545; }
            </style>
        </head>
        <body>
            <div class="card" id="pair-card">
                <h2>Pair this device</h2>
                <p>Enter the 4-digit PIN shown on your phone's Local Hub dialog. Pairing lasts until the server stops.</p>
                <input type="password" id="pin" class="pin-input" placeholder="••••" maxlength="4" inputmode="numeric">
                <p class="error" id="pair-error"></p>
                <button onclick="pair()">Pair</button>
            </div>

            <div id="main-ui" style="display:none">
                <div class="card">
                    <h2>Remote Tab Manager</h2>
                    <form id="open-form" action="/api/tabs/open" method="POST" target="dummyframe" onsubmit="setTimeout(() => loadTabs(), 500)">
                        <input type="text" name="url" placeholder="https://example.com" required>
                        <input type="hidden" name="token" id="token-field-1">
                        <button type="submit">Open URL in Browser</button>
                    </form>
                    <h3>Open Tabs</h3>
                    <div class="tabs-container" id="tabs-list">Loading...</div>
                </div>

                <div class="card">
                    <h2>Clipboard Sync</h2>
                    <p>Type or paste text here. It syncs instantly to your phone's clipboard vault.</p>
                    <form id="clip-form" action="/update" method="POST">
                        <textarea name="text" placeholder="Paste here…"></textarea><br>
                        <input type="hidden" name="token" id="token-field-2">
                        <button type="submit">Sync to Mobile</button>
                    </form>
                </div>

                <div class="card">
                    <h2>Send Message / OTP</h2>
                    <p>Push a message to the browser (e.g. an OTP from your PC).</p>
                    <form id="msg-form" action="/api/message" method="POST" target="dummyframe">
                        <textarea name="text" style="height: 100px;" placeholder="Your OTP is 123456"></textarea>
                        <input type="hidden" name="token" id="token-field-3">
                        <button type="submit" style="background:#28a745;">Push Message</button>
                    </form>
                </div>
            </div>

            <div class="card" id="drop-card">
                <h2>Drop (end-to-end encrypted)</h2>
                <p id="drop-status">Setting up encryption…</p>
                <div id="drop-inbox"></div>
                <h3>Drop to phone</h3>
                <textarea id="drop-text" style="height: 80px;" placeholder="Text or link to drop to your phone…"></textarea>
                <button onclick="dropSend()" style="background:#6f42c1;">Drop to phone</button>
                <div style="margin-top:12px;">
                    <input type="file" id="drop-file">
                    <button onclick="dropSendFile()" style="background:#6f42c1;margin-top:8px;">Drop file to phone</button>
                </div>
            </div>

            <iframe name="dummyframe" id="dummyframe" style="display: none;"></iframe>

            <script>
                function token() { return localStorage.getItem('hub_token') || ''; }
                function authed(path) {
                    const t = token();
                    return path + (path.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(t);
                }
                async function pair() {
                    const pin = document.getElementById('pin').value;
                    const res = await fetch('/api/pair', {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                        body: 'pin=' + encodeURIComponent(pin)
                    });
                    const data = await res.json();
                    if (data.status === 'ok') {
                        localStorage.setItem('hub_token', data.token);
                        showMain();
                    } else {
                        document.getElementById('pair-error').textContent = 'Wrong PIN. Check the phone screen.';
                    }
                }
                function showMain() {
                    document.getElementById('pair-card').style.display = 'none';
                    document.getElementById('main-ui').style.display = 'block';
                    ['token-field-1','token-field-2','token-field-3'].forEach(id => {
                        document.getElementById(id).value = token();
                    });
                    loadTabs();
                    dropSetup();
                }
                async function loadTabs() {
                    try {
                        const res = await fetch(authed('/api/tabs'));
                        if (res.status === 401) { localStorage.removeItem('hub_token'); location.reload(); return; }
                        const tabs = await res.json();
                        const container = document.getElementById('tabs-list');
                        container.innerHTML = '';
                        if (!tabs.length) { container.innerHTML = '<p>No open tabs</p>'; return; }
                        tabs.forEach(tab => {
                            const div = document.createElement('div');
                            div.className = 'tab-item';
                            const titleSpan = document.createElement('span');
                            titleSpan.className = 'tab-title';
                            titleSpan.textContent = (tab.title || tab.url || 'New Tab');
                            const form = document.createElement('form');
                            form.action = authed('/api/tabs/close');
                            form.method = 'POST';
                            form.target = 'dummyframe';
                            form.onsubmit = () => setTimeout(loadTabs, 500);
                            const input = document.createElement('input');
                            input.type = 'hidden'; input.name = 'id'; input.value = tab.id;
                            const tok = document.createElement('input');
                            tok.type = 'hidden'; tok.name = 'token'; tok.value = token();
                            const btn = document.createElement('button');
                            btn.className = 'tab-close'; btn.textContent = 'Close';
                            form.appendChild(input); form.appendChild(tok); form.appendChild(btn);
                            div.appendChild(titleSpan); div.appendChild(form);
                            container.appendChild(div);
                        });
                    } catch (e) {
                        document.getElementById('tabs-list').innerHTML = '<p>Could not load tabs.</p>';
                    }
                }
                // ---------- Drop E2EE (ECDH P-256 + AES-GCM, via WebCrypto) ----------
                let dropAes = null;
                const te = new TextEncoder(), td = new TextDecoder();
                function b64encode(bytes) {
                    let s = ''; const ch = String.fromCharCode;
                    for (let i = 0; i < bytes.length; i += 0x8000) {
                        s += ch.apply(null, bytes.subarray(i, i + 0x8000));
                    }
                    return btoa(s);
                }
                function b64decode(b64) {
                    const s = atob(b64); const b = new Uint8Array(s.length);
                    for (let i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
                    return b;
                }
                async function dropSetup() {
                    try {
                        const res = await fetch(authed('/api/drop/pubkey'));
                        const data = await res.json();
                        const phoneKey = await crypto.subtle.importKey(
                            'raw', b64decode(data.pubkey),
                            {name: 'ECDH', namedCurve: 'P-256'}, false, []);
                        const kp = await crypto.subtle.generateKey(
                            {name: 'ECDH', namedCurve: 'P-256'}, true, ['deriveBits']);
                        const myRaw = new Uint8Array(await crypto.subtle.exportKey('raw', kp.publicKey));
                        await fetch(authed('/api/drop/pubkey'), {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: 'key=' + encodeURIComponent(b64encode(myRaw))
                        });
                        const bits = await crypto.subtle.deriveBits(
                            {name: 'ECDH', public: phoneKey}, kp.privateKey, 256);
                        const hkdfKey = await crypto.subtle.importKey('raw', bits, 'HKDF', false, ['deriveKey']);
                        // Must match DropCrypto.kt: salt "intent-beam-v1", info "beam-aes-gcm".
                        dropAes = await crypto.subtle.deriveKey(
                            {name: 'HKDF', hash: 'SHA-256', salt: te.encode("intent-beam-v1"), info: te.encode("beam-aes-gcm")},
                            hkdfKey, {name: 'AES-GCM', length: 256}, false, ['encrypt', 'decrypt']);
                        document.getElementById('drop-status').textContent =
                            'Encrypted channel ready. The server only relays ciphertext.';
                        dropPoll();
                    } catch (e) {
                        document.getElementById('drop-status').textContent = 'Encryption setup failed: ' + e;
                    }
                }
                async function dropEncrypt(text) {
                    const iv = crypto.getRandomValues(new Uint8Array(12));
                    const ct = new Uint8Array(await crypto.subtle.encrypt(
                        {name: 'AES-GCM', iv: iv}, dropAes, te.encode(text)));
                    return b64encode(iv) + ':' + b64encode(ct);
                }
                async function dropDecrypt(payload) {
                    const parts = payload.split(':');
                    const pt = await crypto.subtle.decrypt(
                        {name: 'AES-GCM', iv: b64decode(parts[0])}, dropAes, b64decode(parts[1]));
                    return td.decode(pt);
                }
                async function dropPoll() {
                    if (!dropAes) return;
                    try {
                        const res = await fetch(authed('/api/drop/poll'));
                        const data = await res.json();
                        if (data.payload) dropInbox(await dropDecrypt(data.payload));
                    } catch (e) {}
                    dropPoll();
                }
                const dropFiles = {};
                function dropInbox(text) {
                    let msg = null;
                    try { msg = JSON.parse(text); } catch (e) {}
                    if (msg && msg.kind === 'file') {
                        let a = dropFiles[msg.tid];
                        if (!a) a = dropFiles[msg.tid] = {name: msg.name, mime: msg.mime, n: msg.n, parts: []};
                        if (!a.parts[msg.i]) a.parts[msg.i] = b64decode(msg.data);
                        const got = a.parts.filter(function(p){return !!p;}).length;
                        document.getElementById('drop-status').textContent =
                            'Receiving ' + msg.name + ' (' + got + '/' + msg.n + ')…';
                        if (got >= a.n) {
                            delete dropFiles[msg.tid];
                            const blob = new Blob(a.parts, {type: a.mime || 'application/octet-stream'});
                            const url = URL.createObjectURL(blob);
                            const div = document.createElement('div');
                            div.style.cssText = 'background:#e6f7e6;border-radius:8px;padding:12px;margin-bottom:8px;';
                            const link = document.createElement('a');
                            link.href = url; link.download = a.name;
                            link.textContent = 'Download ' + a.name;
                            div.appendChild(link);
                            document.getElementById('drop-inbox').prepend(div);
                            document.getElementById('drop-status').textContent =
                                'Encrypted channel ready. The server only relays ciphertext.';
                        }
                        return;
                    }
                    const body = (msg && msg.kind === 'text') ? msg.text : text;
                    const div = document.createElement('div');
                    div.style.cssText = 'background:#f0e6ff;border-radius:8px;padding:12px;margin-bottom:8px;word-break:break-all;';
                    const isUrl = body.indexOf('http://') === 0 || body.indexOf('https://') === 0;
                    const el = document.createElement(isUrl ? 'a' : 'span');
                    el.textContent = body;
                    if (isUrl) { el.href = body; el.target = '_blank'; }
                    div.appendChild(el);
                    document.getElementById('drop-inbox').prepend(div);
                }
                async function dropSend() {
                    const t = document.getElementById('drop-text').value;
                    if (!t || !dropAes) return;
                    const payload = await dropEncrypt(JSON.stringify({kind: 'text', text: t}));
                    await fetch(authed('/api/drop/send'), {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                        body: 'payload=' + encodeURIComponent(payload)
                    });
                    document.getElementById('drop-text').value = '';
                }
                async function dropSendFile() {
                    const input = document.getElementById('drop-file');
                    const f = input.files[0];
                    if (!f || !dropAes) return;
                    const tid = 'f' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
                    const buf = new Uint8Array(await f.arrayBuffer());
                    const CH = 256 * 1024;
                    const n = Math.max(1, Math.ceil(buf.length / CH));
                    const status = document.getElementById('drop-status');
                    for (let i = 0; i < n; i++) {
                        const chunk = buf.subarray(i * CH, Math.min(buf.length, (i + 1) * CH));
                        const env = JSON.stringify({kind: 'file', tid: tid, name: f.name,
                            mime: f.type || 'application/octet-stream', size: f.size,
                            i: i, n: n, data: b64encode(chunk)});
                        const payload = await dropEncrypt(env);
                        await fetch(authed('/api/drop/send'), {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: 'payload=' + encodeURIComponent(payload)
                        });
                        status.textContent = 'Dropping ' + f.name + ' (' + (i + 1) + '/' + n + ')…';
                    }
                    input.value = '';
                    status.textContent = 'Encrypted channel ready. The server only relays ciphertext.';
                }
                if (token()) showMain();
            </script>
        </body>
        </html>
    """.trimIndent()
}
