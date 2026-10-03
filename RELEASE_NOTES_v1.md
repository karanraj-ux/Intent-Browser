# v1 — Intent Browser v2 (LAN-native rebuild)

First v2 package. A restriction-free browser whose superpower is your own Wi-Fi:
no accounts, no cloud — your devices find and control each other.

## What's new
- **Drop** — end-to-end encrypted beaming of tabs, text, and files between phone
  and PC (ECDH P-256 + AES-256-GCM, PIN-gated pairing)
- **Nearby discovery** — phones find each other automatically over mDNS, no IP typing
- **Follow mode** — one phone leads, others auto-open every page it visits
- **Local Hub** — clipboard sync, tab control, QR + IP manual backup
- **Split-screen** — long-press any link → "Open in other pane"
- **Borderless screenshots** — page capture with zero browser UI
- Chrome-style grouped menu, rewritten privacy policy / README / FAQ

## Removed
- Focus Mode is gone entirely — no site blocking, no image stripping, no feed hiding.
  Deliberate: half-done blocking in a WebView breaks sites; the LAN layer is the moat.

## Notes
- Debug APK is built by CI on every push (Actions tab → Artifacts).
- Not yet tested on device — this release is for build verification and smoke testing.
