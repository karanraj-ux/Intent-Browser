# Intent Browser

**Intent Browser** is a powerful, modern, privacy-first Android web browser built from the ground up with Jetpack Compose. Designed for speed, security, and seamless sharing, Intent Browser gives you ultimate control over your web experience.

## ✨ Core Features

- **🌐 Secure Mesh Chat (E2EE)**: Connect securely to peers directly over your local network. True End-to-End Encryption, PIN authentication, and ephemeral RAM-only sessions ensure zero data collection and absolute privacy. Chat safely without internet access.
- **⚡ Split Screen Multitasking**: Browse two sites simultaneously with our intuitive split-screen view. Perfect for power users, researchers, and multitaskers.
- **🛡️ Built-in AdBlocker**: Native AdBlocker powered by EasyList cuts through the noise, blocking ads, trackers, and malicious scripts to keep your browsing fast and distraction-free.
- **📥 Offline Reading Vault**: Save entire web pages for offline access. Your articles are cleaned up and stored securely in a local Room Database for reading anywhere, anytime—no connection required.
- **📝 Scratchpad & Notes**: Jot down ideas, save snippets, and manage quick notes directly within the browser using the built-in Scratchpad.
- **📋 Universal Clipboard Sync**: Instantly sync clipboard history and push tabs between your desktop and mobile device over the local network via a lightweight, built-in server.
- **📥 Advanced Download Manager**: Custom overlay for managing concurrent downloads, pausing, and organizing files securely on-device.

## 🚀 Getting Started

1. Clone the repository.
2. Open the project in Android Studio (Minimum SDK 26).
3. Build and run on a physical device or emulator.
4. Access the **Mesh Chat** and **Clipboard Sync** from the browser's main menu.

## 🛠️ Tech Stack

- **Kotlin** & **Jetpack Compose**
- **Room Database** (Local Persistence)
- **Coil** (Image Loading)
- **Coroutines & Flow** (Asynchronous operations)
- Custom local server (NanoHTTPD style custom sockets) for Mesh Networking

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
