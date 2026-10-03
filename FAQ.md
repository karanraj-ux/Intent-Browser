# Intent Browser — FAQ

## Why are there no extensions?

Extensions need the Chromium extension APIs, which Google only exposes in full Chromium builds (Chrome, Edge, Brave). Android's WebView — the component every non-Chrome Android browser is built on, including DuckDuckGo's — doesn't include them. No WebView-based browser can support Chrome extensions. This is a platform limit, not a feature we haven't gotten to.

## Why won't Netflix / Hotstar / Spotify play video in the browser?

Protected video needs Widevine DRM, which is also not available inside WebView. Any site that requires DRM playback will fail in Intent Browser, DuckDuckGo, and every other WebView browser. Use the services' native Android apps for those.

## What does "encrypted" actually mean for Mesh Chat and Local Hub?

Honest answer, because the words matter:

- Both features run only when you start them, and only on your local Wi-Fi. Anyone on that network can reach the server, so the PIN gate is the access control — don't use them on networks you don't trust.
- Mesh chat message contents are encrypted with AES-GCM, using a key derived from the 4-digit session PIN shown on the host phone. That stops casual snooping, but a 4-digit PIN is guessable by someone determined on the same network. There is no forward secrecy yet.
- **Drop** (Local Hub → "Drop this tab" / "Drop to phone") is real E2EE: ECDH on P-256 between your phone and the paired browser, HKDF-SHA256 key derivation, AES-256-GCM per payload. The PIN (shown on your phone) stops an active attacker from pairing; ECDH stops a passive Wi-Fi sniffer from reading dropped content. The server only ever relays ciphertext.
- So: "PIN-encrypted local chat" is accurate for mesh chat, and "E2EE" is accurate for Drop. "Military-grade" anything would not be. point this answer gets rewritten.

## What permissions does the app ask for, and why?

- **Internet** — it's a browser.
- **Camera / Microphone** — only when a website you visit requests them (video calls, voice input). Approved per site, per request; never in the background.
- **Location** — only when a website requests it, approved per site.
- **Notifications** — download progress and completion alerts.
- **Storage / media access** — saving downloads and offline pages to your device, and picking files when a site asks you to upload one.

Nothing else. No contacts, no SMS, no background location.

## How do offline pages work?

Menu → Save Offline stores the current page as a web archive (MHTML) in the app's private storage. Open it later from the Offline Pages screen with no internet. Limits: pages behind a login may not render fully offline, and pages that load most content via JavaScript after load may be incomplete. It's a snapshot, not a live page.

## Can I set Intent Browser as my default browser?

Not yet. The intent filters that let Android offer it as a default browser (and open links from other apps) are on the roadmap. For now it works as a standalone browser you open directly.

## Does it block ads?

Yes — known ad and tracker domains are blocked at the network layer, backed by a public hosts blocklist refreshed about once a week. Toggle it in Settings. It's domain-list blocking (like DNS filtering), not cosmetic filtering, so some ad placeholders may remain visible as empty boxes.

## Does the browser block distracting sites or strip images (focus tools)?

No — deliberately. Those are extension-grade features; half-doing them inside a WebView breaks websites and makes the browser feel unreliable. If you want hard content blocking, use a browser with extension support. Intent Browser stays a smooth, unrestricted browser and puts its energy into the multi-device LAN features instead.

## Where does my data go if I lose my phone?

Nowhere recoverable by us — there's no account and no cloud copy. If you had Android's device backup enabled, your app data may be in your own Google Drive backup; otherwise it's gone with the device. Export anything important (notes, offline pages you care about) while you have the phone.

## Can I drop files, not just tabs?

Yes, both directions, still end-to-end encrypted. On the phone: menu → Drop file → pick a file. On the PC: open the Local Hub page → Drop card → choose a file. Files are split into 256KB chunks, each encrypted separately with the same session key, and reassembled on arrival — the server only ever relays ciphertext. Received files land in Downloads (phone) or as a download link (PC).

## How do I report a bug or request a feature?

Open an issue at `https://github.com/karanraj-ux/Intent-Browser`. Include: your Android version, your device's WebView version (Settings → Apps → Android System WebView), what you were doing, and what happened instead. Screenshots help; screen recordings help more.
