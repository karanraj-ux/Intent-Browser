# Intent Browser: The Cognitive Preservation Tool

**Intent Browser** is not just another web browser. It is a **Local-First Research Companion** designed for deep work, rapid synthesis, and absolute digital sovereignty. 

While mainstream browsers are designed to keep you scrolling, Intent Browser is engineered to protect your attention, bypass hostile mobile web design, and keep your data strictly on your devices.

## 🧠 Why We Built It (The Market Gap)
Modern web design strips away your control. Websites lock you out of zooming, blast you with sticky headers, and social media creates addictive doomscrolling loops. Intent Browser acts as an exoskeleton for your attention, breaking website restrictions and returning the locus of control to the user.

## ✨ Next-Gen Differentiators

- **💥 The "Guilt-Trip" Focus Mode**: Instead of hard-blocking useful sites like Reddit (for devs) or YouTube (for students) which causes psychological reactance, our Focus Mode dynamically injects bright red, non-dismissible banners over the sites ("Use for study only. Do not waste time."). It breaks the autopilot scrolling loop while still allowing you to get the data you need.
- **🌐 Serverless "AirDrop" (The Mesh Network)**: Seamlessly beam links, text, and clipboard data to a PC, Mac, or friend’s phone over local Wi-Fi. It spins up an instant E2EE tunnel. No cloud, no Google sync, no accounts required. 
- **🛡️ Hostile Web Overrides**: Tired of websites that disable zoom or force mobile views? Intent Browser breaks their CSS and forces your preferences. Zoom up to 500% anywhere, force Desktop Mode instantly, and deploy Reader Mode to strip away sticky ads and newsletters.
- **📝 In-Line Scratchpad**: Preserve your working memory. When you find a good quote, highlight it and save it directly to the built-in Scratchpad database. You never leave the webpage, eliminating the context-switching penalty of opening a separate Notes app.
- **📥 Offline-First Vault**: Save entire web pages instantly for offline reading. Your data is stored locally via Room database, completely severing your reliance on an internet connection.
- **⚡ Split Screen & AdBlocker**: Native AdBlocker cuts the noise, and built-in split-screen allows for parallel research without losing context.

## 🚀 Getting Started

1. Clone the repository.
2. Open the project in Android Studio (Minimum SDK 26).
3. Build and run on a physical device or emulator.
4. Access **Mesh Server** from the menu to connect to any other device on your Wi-Fi by simply typing in the provided IP address in their browser.

## 🛠️ Architecture & Stack

- **Kotlin** & **Jetpack Compose** (Material 3)
- **Room Database** (Offline Pages, Scratchpad, Bookmarks)
- **Local Sockets** (Local P2P Mesh Networking)
- **Custom Hardened WebView Engine** (CSS injection, DOM manipulation, User-Agent spoofing)

## 📄 Privacy & License

Your data belongs to you. Intent Browser contains zero telemetry, zero trackers, and zero cloud backups. It is designed to be fully self-contained. 

Licensed under the MIT License.
