# Intent Browser v2

An attention-first Android browser for phone-first students and builders. Built on Android WebView with Kotlin and Jetpack Compose. No accounts, no cloud, no telemetry.

**Status:** under active rebuild. Phase 0 (make it compile, strip dead weight) is done; working through the roadmap in `../intent-browser-audit/ROADMAP.md`.

## Who it's for

Students and early-career builders (16–24, India-first) who do serious reading, research, and watching on their phones — while fighting doomscrolling, on patchy Wi-Fi, moving between a phone and shared PCs. If that's you, this browser is built for your day.

## The idea in one paragraph

Intent Browser is a normal, smooth browser with one unusual superpower: your devices find and control each other over your own Wi-Fi — no accounts, no cloud. Drop tabs and files between phone and PC with real end-to-end encryption, sync clipboards, control tabs remotely, and discover nearby devices automatically. Plus the essentials done right: ad/tracker blocking, reader mode, offline vault, scratchpad, split-screen.

## Features

**Today**
- Tabbed browsing with incognito tabs (separate profile where the system WebView supports it)
- Ad and tracker blocking from domain lists plus a weekly-refreshed hosts blocklist (toggle in Settings)
- Reader mode, desktop/mobile site toggle, force zoom up to 500% on pages that disable it
- Offline Vault: save full pages as web archives, read them without internet
- Scratchpad: save selected text or notes without leaving the page; clipboard history
- Split-screen dual-tab view
- Mesh chat and Local Hub: PIN-gated local-network sharing (clipboard sync, tab push, chat) — see the privacy policy for the honest crypto details
- Nearby discovery: phones running the app find each other automatically over mDNS — no IP typing
- Follow mode: PIN-paired co-browsing — one phone leads, others auto-open every page it visits (classroom/demo friendly)
- Local Hub manual backup: QR code + IP address in the hub dialog, for networks where mDNS discovery can't see the phone
- Drop files: chunked end-to-end encrypted file transfer both ways (phone→PC via the page menu, PC→phone via the hub page file picker)
- Split-screen: long-press any link → “Open in other pane”
- Borderless screenshots: capture the page with no browser UI, saved to Pictures
- Multiple search engines (Google, DuckDuckGo, Brave, Bing, Yahoo, Ecosia, Startpage)

**Drop (the flagship) — shipped**
Account-less, E2EE phone↔PC dropping of the current tab or text over local Wi-Fi. ECDH P-256 key exchange (phone ↔ paired browser via WebCrypto), HKDF-SHA256 to AES-256-GCM; the PIN shown on your phone stops active attackers from pairing, the server only ever relays ciphertext. No Google sync, no accounts, no cloud. Use it from the page menu ("Drop this tab") once Local Hub is paired.

## The bar: DuckDuckGo parity, not Chrome

DuckDuckGo's Android app is also a WebView shell — that's an achievable bar for a solo-built browser, and it's the one we're aiming at. We are not trying to be Chrome, and WebView makes some Chrome things impossible. Honest limits:

- **No extensions.** WebView doesn't expose the extension APIs; this is an Android platform limit, not a missing feature.
- **No Widevine DRM video.** Netflix, Hotstar, Spotify, and similar web players won't play protected video in any WebView app. Use their native apps.
- **Renderer tied to the system WebView.** Page rendering quality and web-platform support follow whatever WebView version is installed on the device.

## Build it

Requirements: Android Studio (recent), JDK 17+.

```bash
git clone https://github.com/karanraj-ux/Intent-Browser.git
cd Intent-Browser
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

- `minSdk 26` (Android 8.0), `targetSdk` / `compileSdk 35`
- Package: `com.intentbrowser.app`
- CI builds on every push via `.github/workflows/android.yml`

## Project structure

```
app/src/main/
  java/com/intentbrowser/app/
    MainActivity.kt            # entry point, clipboard listener, WebView warmup
    ui/
      BrowserScreen.kt         # tabs, address bar, menus, overlays (~1100 lines)
      HardenedWebView.kt       # the WebView engine: clients, focus/adblock injection
      MainViewModel.kt         # tab state, servers, settings state
      MainApp.kt               # navigation (browser/history/bookmarks/notes/settings)
      SettingsScreen.kt        # toggles: adblock, third-party cookies, sync server
      ...screens (History, Notes, Downloads)
    data/                      # Room: history, bookmarks, tabs, downloads, notes, offline pages
    util/
      AdBlocker.kt             # domain lists + hosts-file blocklist
      WebViewPool.kt           # WebView reuse pool
      WebViewSettingsManager.kt# hardened WebSettings defaults
      CustomDownloadManager.kt # OkHttp download engine
      MeshChatServer.kt        # LAN mesh chat (PIN-gated)
      ClipboardServer.kt       # LAN clipboard/tab sync (PIN-gated)
  res/                         # themes, strings, icons
  assets/                      # offline.html, focus_blocked.html
```

## Docs

- `PRIVACY_POLICY.md` — what data stays, what leaves, plain language
- `FAQ.md` — extensions, DRM, crypto honesty, permissions, offline pages
- `../intent-browser-audit/AUDIT.md` — the full engineering audit this rebuild is based on
- `../intent-browser-audit/ROADMAP.md` — the phased build plan

## License

MIT.
