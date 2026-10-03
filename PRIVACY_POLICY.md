# Privacy Policy — Intent Browser

**Last updated:** 2026-10-02
**Applies to:** Intent Browser v2 (`com.intentbrowser.app`)

## The short version

Your browsing data stays on your phone. We have no servers, no accounts, no analytics, and no crash reporting. The only network requests this app makes on its own are: loading the websites you visit, sending your searches to the search engine you chose, and downloading a public ad-block list about once a week. Everything else on this page is detail.

## What stays on your device

- Browsing history, bookmarks, open tabs, notes, clipboard history, and settings — stored in a local database (Room) and app preferences (SharedPreferences).
- Saved offline pages and downloads — stored in the app's own storage on your device.
- There is no sign-in, no sync account, and no cloud backup run by us. Nothing is uploaded to us because there is no "us" to upload to.
- One caveat: Android's own auto-backup may include app data in *your* Google Drive backup if you have device backup turned on. That is your phone's feature, not ours — we never see it.

## What leaves your device

Only these:

1. **The websites you visit.** That is what a browser does. Sites see the same things they see from any browser (your IP address, user agent).
2. **Your searches.** Sent only to the search engine you selected in the app (Google, DuckDuckGo, Brave, etc.). We don't log or proxy them.
3. **The ad-block list.** About once a week, the app downloads the public StevenBlack hosts file from `raw.githubusercontent.com` to refresh its tracker blocklist. This is a plain file download — it sends no personal data, but the server will see a normal download request from your IP, like any file fetch.
4. **Nothing else.** No telemetry, no analytics SDKs, no advertising IDs, no crash reporters.

Bookmark icons (favicons) are captured locally from the sites you visit. We do not fetch them from Google or any third-party icon service.

## Local network features (Local Hub and Mesh Chat)

These features only run when **you** start them, and they stop when you close them:

- **Local Hub** (clipboard sync, tab sharing) and **Mesh Chat** open a small web server on your phone, reachable by other devices on the same Wi-Fi network.
- Anyone on that Wi-Fi network can reach the server address — so only use these on networks you trust, and stop the server when you're done.
- Access is gated by a PIN shown on your phone. The other device must enter it to connect.
- **Mesh chat messages are PIN-encrypted, not end-to-end encrypted in the strong sense.** Messages are encrypted with AES-GCM using a key derived from the 4-digit session PIN. This stops casual snooping on the network, but a 4-digit PIN can be guessed by a determined attacker on the same network. Don't drop anything through it that you wouldn't say out loud in the room. We're working toward a stronger key exchange (see the FAQ).

## Permissions and why

- **Camera / Microphone** — only requested when a website you visit asks for them (e.g., a video call). You approve per site, per request.
- **Location** — only when a website asks; you approve per site. The app never tracks your location in the background.
- **Notifications** — download progress and completion.
- **Storage / media** — saving downloads and offline pages, and letting you pick files when a website asks for an upload.

## What we will never do

- Sell, rent, or share your data — there is nothing to sell.
- Show ads inside the app.
- Change this policy silently. If it changes, the date at the top changes and the new version ships with the app update.

## Questions

Open an issue at `https://github.com/karanraj-ux/Intent-Browser` and we'll answer it there.
