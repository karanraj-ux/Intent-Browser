# EcoAI Browser

EcoAI Browser is a modern, privacy-first, power-user Android web browser built entirely with Jetpack Compose. It focuses on speed, efficiency, and integrating on-device and cloud AI seamlessly into the browsing experience.

## Features

- **Built-in AdBlocker**: Uses EasyList to block ads and trackers natively. Real-time adult-content blocking using Cloudflare Family DNS.
- **Offline Vault**: Save web pages for offline reading. Uses a local Room Database to store cleaned-up HTML, removing banners and unnecessary scripts for distraction-free reading.
- **Tab Suspension**: Put background tabs to sleep to reduce memory usage and save battery.
- **AI Integration**: Summarize and chat with web pages using Gemini API, local SLMs, or custom endpoints.
- **Power User Settings**: Inject custom CSS, JavaScript, and manage URL redirects.
- **Command Palette**: Quickly search tabs, history, and trigger browser actions.
- **Privacy First**: Quick incognito tabs, customizable DNS, and robust tracking protection.

## Build Requirements

- Android Studio / Gradle 8+
- Jetpack Compose
- Kotlin
- Minimum SDK: 26

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run `app` on a physical device or emulator.
4. For AI features, you can add your API keys in the Power Settings menu inside the app.

## Contributing

Contributions are welcome! If you find a bug or have a feature request, please open an issue.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
