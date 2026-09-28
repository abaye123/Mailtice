# Mailtice

A unified inbox with notifications for several mail accounts at once: Gmail and Google Workspace,
Outlook and Microsoft 365, Yahoo, and any IMAP server. It runs on Windows, macOS, Linux and Android,
with a Hebrew (right-to-left) and an English interface, and is built with Compose Multiplatform and
[Nucleus](https://github.com/NucleusFramework/Nucleus).

Website and downloads: <https://mailtice.abaye.co>

## Features

- **Home dashboard** - every account at a glance: unread count, how fresh the newest unread mail is,
  the latest unread messages, a week of incoming mail, and whether the account is connected.
- **Unified and per-account views** - the standard folders across all accounts, and each account as a
  foldable tree with its own folders and labels. Accounts can be reordered.
- **Labels** - pin, hide, rename, recolour (Gmail's palette), create and delete labels or IMAP folders.
- **Reader** - HTML mail in the platform's own browser engine (WebView2, WKWebView, WebKitGTK, Android
  WebView), locked down: no scripts, remote images on request, inline images, links opened in the
  browser, quoted earlier messages folded.
- **Translation** - a Gmail-style bar offers to translate mail written in another language, in place
  for HTML; the quoted part is not sent.
- **Attachments** - a viewer for images, text, HTML, PDF, audio and video, with download and "open in
  its app"; a choosable download folder.
- **Compose** - rich text, attachments by picker or drag and drop, draft autosave, scheduled send, and
  sending from Gmail "Send mail as" aliases with their signatures.
- **Smart checking** - learns when each account usually gets mail and checks more often then, and
  right after you send, in case a reply comes.
- **Offline mode** - keeps the whole mailbox and every body (and attachments up to a chosen size) on the
  device; mail sent and actions taken without a connection are queued and go out as soon as one appears.
- **Search** - Gmail-style operators (`from:`, `subject:`, `has:attachment`, `newer_than:`...) with a
  search options panel, and older mail fetched from the server beyond what is kept locally.
- **Also** - the Hebrew date next to the civil one, a choice of fonts (Rubik, Heebo, Noto Sans), list
  density and pane styles, light and dark themes, tray and launch at login on desktop.

Mail is stored in a local SQLite database (`mailtice.db`). Each account chooses which folders or labels
to sync and notify about, and how many days back to keep. An action an account cannot do (say, archive on
an IMAP server without an Archive folder) is simply not offered.

## First setup: OAuth

All values go in `oauth.local.properties` (copy `oauth.local.properties.example`; the file is in
`.gitignore`). CI reads the same names from repository secrets. A provider without a client is not
offered in the app. A plain IMAP account (password or app password) needs no setup.

**Google (Gmail API)**

1. In the [Google Cloud Console](https://console.cloud.google.com/) create a project and enable the
   **Gmail API**.
2. On the consent screen choose External and add the `gmail.modify` scope, the only one the app uses. In
   **Testing** the refresh token expires after 7 days; for personal use switch to **In production**
   without submitting for verification, and accept the "unverified app" screen once per account.
3. Create three OAuth clients in the same project:
   - **Desktop app** - its values go in `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
   - **Web application** - its values go in `GOOGLE_WEB_CLIENT_ID` and `GOOGLE_WEB_CLIENT_SECRET`;
     Android exchanges its authorization code with it (on the device, there is no server).
   - **Android** - package `co.abaye.mailtice` and the SHA-1 of each signing key (debug and release);
     it has no values for the file.

**Microsoft (Outlook / 365)**

An app registration in Entra for "any organizational directory + personal Microsoft accounts". Under the
**Mobile and desktop** platform add two redirects: `http://localhost` and `co.abaye.mailtice://oauth`.
Permissions: `IMAP.AccessAsUser.All` (Office 365 Exchange Online), `offline_access`, `openid`, `email`.
It is a public client without a secret; the only value is `MICROSOFT_CLIENT_ID`.

**Yahoo / AOL**

Yahoo requires an application and approval for the mail scope, and an https redirect. Host
`website/oauth-relay.html` (for example on GitHub Pages), register its address with Yahoo and put it in
`YAHOO_REDIRECT_URI`. Until then, add a Yahoo account as "Other provider (IMAP)" with an app password.
Yahoo OAuth is not wired on Android yet.

## Running and building

```bash
./gradlew :desktopApp:run                  # run on desktop
./gradlew :desktopApp:hotRun --auto        # hot reload
./gradlew :androidApp:installDebug         # install on a device or emulator
./gradlew :shared:jvmTest                  # tests
./gradlew detekt ktlintCheck               # the checks CI runs
./gradlew :desktopApp:packageGraalvmNsis   # Windows release installer (GraalVM)
```

Behind a TLS-inspecting filter (such as NetFree), set
`$env:GRADLE_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'` before building. In the app, desktop
IMAP uses the operating system's certificate store (Nucleus native SSL) and Android trusts the filter's
roots through `network_security_config`.

## Releases

A `v*` tag runs `release.yaml`, which builds Windows, Linux x64/ARM64, macOS ARM/Intel and Android and
publishes them to a single GitHub Release together with the self-update manifests. A manual run of the
workflow builds everything without publishing. Signing uses the `ANDROID_KEYSTORE_*` / `ANDROID_KEY_*`
secrets for Android and the optional `LINUX_GPG_*` secrets for the Linux packages.

## Open items

- **Desktop secrets** are kept in `secrets.txt` in the app folder, unencrypted; to move to Credential
  Manager / Keychain / libsecret.
- **Several Google accounts on Android** - Play services may pick the last used account; account choice
  through Credential Manager is still to do (see `AndroidAuthorizer`).
- **IMAP IDLE** (push instead of polling) is not in yet.
- **Code signing** for Windows and notarization for macOS are not set up, so both warn on first launch.

Code structure and conventions: `AGENTS.MD`.

## Credits

Temporary app icon (to be removed once Mailtice has its own icon):
<a target="_blank" href="https://icons8.com/icon/td499GRWwrWC/gmail-logo">Gmail Logo</a> icon by <a target="_blank" href="https://icons8.com">Icons8</a>
