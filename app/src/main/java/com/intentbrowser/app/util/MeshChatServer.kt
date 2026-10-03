package com.intentbrowser.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID

data class MeshMessage(val id: String, val encryptedPayload: String, val sender: String, val timestamp: Long)

class MeshChatServer(private val port: Int = 8081, private val serverIp: String) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val hostPin = (1000..9999).random().toString()
    
    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())
    val messages = _messages.asStateFlow()
    
    // For long polling
    private val messageFlow = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 100)
    
    private val activeSessions = mutableSetOf<String>()

    fun getPin(): String = hostPin

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                Log.d("MeshChatServer", "Mesh chat server started on port $port with PIN $hostPin")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    Thread { handleClient(socket) }.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        _messages.value = emptyList() // Ephemeral: clear RAM
        activeSessions.clear()
    }
    
    fun sendLocalMessage(encryptedPayload: String, sender: String) {
        val msg = MeshMessage(UUID.randomUUID().toString(), encryptedPayload, sender, System.currentTimeMillis())
        _messages.value = _messages.value + msg
        messageFlow.tryEmit(msg)
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            var path = parts[1]

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) break
                val headerParts = line.split(": ", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].lowercase()] = headerParts[1]
                    if (headerParts[0].lowercase() == "content-length") {
                        contentLength = headerParts[1].toIntOrNull() ?: 0
                    }
                }
            }
            
            // CORS Protection: Strictly reject unexpected origins
            val origin = headers["origin"]
            if (origin != null && !origin.contains(serverIp)) {
                sendResponse(writer, "403 Forbidden", "", "CORS Error")
                socket.close()
                return
            }

            // Extract session
            val cookieHeader = headers["cookie"] ?: ""
            val sessionToken = cookieHeader.split(";").find { it.trim().startsWith("mesh_session=") }?.substringAfter("=") ?: ""
            val isAuthenticated = activeSessions.contains(sessionToken)
            
            if (method == "GET" && path == "/") {
                sendResponse(writer, "200 OK", "Content-Type: text/html; charset=UTF-8\r\n", getHtmlApp())
                socket.close()
                return
            }
            
            if (method == "POST" && path == "/auth") {
                val body = readBody(reader, contentLength)
                val pin = parseParam(body, "pin")
                if (pin == hostPin) {
                    val newToken = UUID.randomUUID().toString()
                    activeSessions.add(newToken)
                    sendResponse(writer, "200 OK", "Set-Cookie: mesh_session=$newToken; HttpOnly; Path=/\r\nContent-Type: application/json\r\n", """{"status":"ok"}""")
                } else {
                    sendResponse(writer, "401 Unauthorized", "Content-Type: application/json\r\n", """{"status":"error","message":"Invalid PIN"}""")
                }
                socket.close()
                return
            }
            
            if (!isAuthenticated) {
                sendResponse(writer, "401 Unauthorized", "", "Not authenticated")
                socket.close()
                return
            }
            
            if (method == "GET" && path == "/messages") {
                // Return all current messages
                val json = _messages.value.joinToString(",", "[", "]") { 
                    """{"id":"${it.id}","encryptedPayload":"${it.encryptedPayload}","sender":"${it.sender}","timestamp":${it.timestamp}}""" 
                }
                sendResponse(writer, "200 OK", "Content-Type: application/json\r\n", json)
                socket.close()
                return
            }
            
            if (method == "POST" && path == "/message") {
                val body = readBody(reader, contentLength)
                val encryptedPayload = parseParam(body, "payload")
                val senderName = parseParam(body, "sender")
                
                if (encryptedPayload.isNotBlank()) {
                    val msg = MeshMessage(UUID.randomUUID().toString(), encryptedPayload, senderName.ifBlank { "Web Client" }, System.currentTimeMillis())
                    _messages.value = _messages.value + msg
                    messageFlow.tryEmit(msg)
                }
                sendResponse(writer, "200 OK", "Content-Type: application/json\r\n", """{"status":"ok"}""")
                socket.close()
                return
            }
            
            if (method == "GET" && path == "/poll") {
                // Basic Long Polling (Block until new message or timeout)
                // Realistically, without coroutines here, we can just block thread briefly
                var waited = 0
                val initialSize = _messages.value.size
                while(waited < 20000) { // 20 sec timeout
                    if (_messages.value.size > initialSize) {
                        break
                    }
                    Thread.sleep(500)
                    waited += 500
                }
                val newMessages = if (_messages.value.size > initialSize) {
                    _messages.value.drop(initialSize)
                } else emptyList()
                
                val json = newMessages.joinToString(",", "[", "]") { 
                    """{"id":"${it.id}","encryptedPayload":"${it.encryptedPayload}","sender":"${it.sender}","timestamp":${it.timestamp}}""" 
                }
                sendResponse(writer, "200 OK", "Content-Type: application/json\r\n", json)
                socket.close()
                return
            }
            
            sendResponse(writer, "404 Not Found", "", "Not found")
            socket.close()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""
        val chars = CharArray(length)
        reader.read(chars, 0, length)
        return String(chars)
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
    
    private fun getHtmlApp(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Secure Mesh Chat</title>
                <style>
                    body { font-family: -apple-system, system-ui, sans-serif; background: #121212; color: #ffffff; margin: 0; padding: 20px; display: flex; flex-direction: column; height: 100vh; box-sizing: border-box; }
                    #auth-screen, #chat-screen { display: none; flex: 1; flex-direction: column; }
                    .card { background: #1e1e1e; padding: 24px; border-radius: 16px; max-width: 400px; margin: auto; text-align: center; width: 100%; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
                    input { width: 100%; padding: 14px; margin-bottom: 16px; border-radius: 8px; border: 1px solid #333; background: #2c2c2c; color: white; font-size: 16px; box-sizing: border-box; text-align: center; }
                    button { background: #6200ee; color: white; border: none; padding: 14px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; width: 100%; }
                    button:hover { background: #3700b3; }
                    #messages { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; padding-bottom: 20px; }
                    .message { max-width: 80%; padding: 12px 16px; border-radius: 16px; word-wrap: break-word; }
                    .msg-mine { background: #6200ee; align-self: flex-end; border-bottom-right-radius: 4px; }
                    .msg-theirs { background: #333333; align-self: flex-start; border-bottom-left-radius: 4px; }
                    .sender-name { font-size: 12px; color: #aaaaaa; margin-bottom: 4px; }
                    #input-area { display: flex; gap: 8px; margin-top: auto; padding-top: 10px; border-top: 1px solid #333; }
                    #msg-input { text-align: left; margin: 0; flex: 1; }
                    #send-btn { width: auto; padding: 14px 24px; }
                </style>
            </head>
            <body>
                <div id="auth-screen">
                    <div class="card">
                        <h2>Mesh Network Access</h2>
                        <p style="color:#aaa; font-size: 14px;">Enter the 4-digit PIN displayed on the host device.</p>
                        <input type="password" id="pin-input" placeholder="Enter PIN" maxlength="4">
                        <button onclick="authenticate()">Connect Securely</button>
                    </div>
                </div>

                <div id="chat-screen">
                    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #333; padding-bottom: 10px; margin-bottom: 10px;">
                        <h3 style="margin: 0;">Mesh Chat <span style="font-size:12px;color:#888;">(PIN-encrypted)</span></h3>
                        <span style="font-size: 12px; color: #4caf50;">● Connected</span>
                    </div>
                    <div id="messages"></div>
                    <div id="input-area">
                        <input type="text" id="msg-input" placeholder="Message...">
                        <button id="send-btn" onclick="sendMessage()">Send</button>
                    </div>
                </div>

                <script>
                    const KEY_NAME = 'mesh_e2ee_key';
                    let cryptoKey = null;
                    let username = 'Web-' + Math.floor(Math.random() * 1000);
                    let seenMessages = new Set();

                    // Generate or derive a simple shared key for this session
                    // In a real E2EE, we'd use ECDH. For local mesh with PIN, we'll derive a symmetric AES-GCM key from the PIN
                    async function deriveKey(pin) {
                        const enc = new TextEncoder();
                        const keyMaterial = await crypto.subtle.importKey(
                            "raw", enc.encode(pin + "mesh_salt_123"),
                            { name: "PBKDF2" }, false, ["deriveBits", "deriveKey"]
                        );
                        return await crypto.subtle.deriveKey(
                            { name: "PBKDF2", salt: enc.encode("fixed_salt"), iterations: 100000, hash: "SHA-256" },
                            keyMaterial, { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]
                        );
                    }

                    async function encrypt(text) {
                        if (!cryptoKey) return text;
                        const iv = crypto.getRandomValues(new Uint8Array(12));
                        const encoded = new TextEncoder().encode(text);
                        const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv: iv }, cryptoKey, encoded);
                        const ivHex = Array.from(iv).map(b => b.toString(16).padStart(2, '0')).join('');
                        const cipherHex = Array.from(new Uint8Array(ciphertext)).map(b => b.toString(16).padStart(2, '0')).join('');
                        return ivHex + ":" + cipherHex;
                    }

                    async function decrypt(encryptedHex) {
                        if (!cryptoKey) return encryptedHex;
                        try {
                            const [ivHex, cipherHex] = encryptedHex.split(":");
                            const iv = new Uint8Array(ivHex.match(/.{1,2}/g).map(byte => parseInt(byte, 16)));
                            const ciphertext = new Uint8Array(cipherHex.match(/.{1,2}/g).map(byte => parseInt(byte, 16)));
                            const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv: iv }, cryptoKey, ciphertext);
                            return new TextDecoder().decode(decrypted);
                        } catch (e) {
                            return "[Decryption Failed]";
                        }
                    }

                    async function authenticate() {
                        const pin = document.getElementById('pin-input').value;
                        if(pin.length !== 4) { alert("Enter 4 digit PIN"); return; }
                        
                        const res = await fetch('/auth', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: 'pin=' + encodeURIComponent(pin)
                        });
                        
                        if (res.ok) {
                            cryptoKey = await deriveKey(pin);
                            document.getElementById('auth-screen').style.display = 'none';
                            document.getElementById('chat-screen').style.display = 'flex';
                            loadMessages();
                            pollMessages();
                        } else {
                            alert("Invalid PIN. Connection rejected.");
                        }
                    }

                    function displayMessage(id, text, sender, timestamp) {
                        if(seenMessages.has(id)) return;
                        seenMessages.add(id);
                        
                        const msgDiv = document.createElement('div');
                        const isMine = sender === username;
                        msgDiv.className = 'message ' + (isMine ? 'msg-mine' : 'msg-theirs');
                        
                        if(!isMine) {
                            const nameSpan = document.createElement('div');
                            nameSpan.className = 'sender-name';
                            nameSpan.innerText = sender;
                            msgDiv.appendChild(nameSpan);
                        }
                        
                        const textSpan = document.createElement('div');
                        textSpan.innerText = text;
                        msgDiv.appendChild(textSpan);
                        
                        document.getElementById('messages').appendChild(msgDiv);
                        document.getElementById('messages').scrollTop = document.getElementById('messages').scrollHeight;
                    }

                    async function loadMessages() {
                        const res = await fetch('/messages');
                        if (res.ok) {
                            const msgs = await res.json();
                            for (let msg of msgs) {
                                const plain = await decrypt(msg.encryptedPayload);
                                displayMessage(msg.id, plain, msg.sender, msg.timestamp);
                            }
                        }
                    }

                    async function pollMessages() {
                        try {
                            const res = await fetch('/poll');
                            if (res.status === 401) { location.reload(); return; }
                            if (res.ok) {
                                const msgs = await res.json();
                                for (let msg of msgs) {
                                    const plain = await decrypt(msg.encryptedPayload);
                                    displayMessage(msg.id, plain, msg.sender, msg.timestamp);
                                }
                            }
                        } catch(e) { }
                        setTimeout(pollMessages, 1000); // Re-poll
                    }

                    async function sendMessage() {
                        const input = document.getElementById('msg-input');
                        const text = input.value.trim();
                        if(!text) return;
                        input.value = '';
                        
                        const encrypted = await encrypt(text);
                        
                        await fetch('/message', {
                            method: 'POST',
                            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                            body: 'payload=' + encodeURIComponent(encrypted) + '&sender=' + encodeURIComponent(username)
                        });
                    }
                    
                    document.getElementById('msg-input').addEventListener('keypress', function (e) {
                        if (e.key === 'Enter') sendMessage();
                    });

                    // Check if already authed by trying to fetch messages
                    fetch('/messages').then(res => {
                        if(res.ok) {
                            document.getElementById('auth-screen').style.display = 'none';
                            document.getElementById('chat-screen').style.display = 'flex';
                            // We don't have the PIN for key derivation if we reload, so we force them to re-auth in real life,
                            // but here we just show auth screen if we want E2EE to work across reloads.
                            // To keep it simple, if no cryptoKey, we force reload.
                            location.reload(); 
                        } else {
                            document.getElementById('auth-screen').style.display = 'flex';
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
