# Mailtice - Compose Multiplatform mail client (desktop + Android)

Unified inbox + notifications for Gmail/Workspace (Gmail API), Outlook/Microsoft 365 and Yahoo
(IMAP + XOAUTH2) and any IMAP server (password). Built on Compose Multiplatform and Nucleus.

## Project Structure

```
├── shared/       # ALL code (commonMain, jvmSharedMain, jvmMain, androidMain)
├── desktopApp/   # thin wrapper: window, title bar, tray, updater wiring
└── androidApp/   # thin wrapper: Application (schedules the worker), AppActivity
```

Source sets in `shared`: `commonMain` -> `jvmSharedMain` (JVM + Android: Angus Mail / IMAP lives
here) -> `jvmMain` (desktop) and `androidMain`.

## Build & Run

| Target | Command |
|--------|---------|
| Desktop (run) | `./gradlew :desktopApp:run` |
| Desktop (hot reload, demo data) | `./gradlew :desktopApp:hotRun --auto` |
| Desktop (hot reload, onboarding) | `./gradlew :desktopApp:hotRun --auto -Pdemo=empty` |
| Desktop (hot reload, real data) | `./gradlew :desktopApp:hotRun --auto -Pdemo=off` |
| Android (install) | `./gradlew :androidApp:installDebug` |
| Tests | `./gradlew :shared:jvmTest` |
| Release installer (GraalVM) | `./gradlew :desktopApp:packageGraalvmNsis` |
| Lint | `./gradlew detekt ktlintCheck` |

**Demo mode** (`dev/`): `-Pdemo=full|empty|off` (the app reads it as `-Dmailtice.demo`, or `MAILTICE_DEMO`).
Hot reload runs default to `full`, `run` to `off`. Every account is served by the in-memory
`DemoMailProvider` (no network, no OAuth), data lives in `Mailtice-Demo` next to the real app dir
and is wiped on every launch, and the title bar shows a "Demo" badge. A new message arrives every
third sync round per account ("Check now" counts), so notifications can be tested on demand. The
quiet account stays empty for the empty-inbox screen. Desktop only.

## Architecture

Single-state MVI: one `AppState`, one `AppViewModel`, one `onIntent`. **SQLite is the source of
truth**: sync writes to it, the UI reads SQLDelight Flows. The view model never holds mail itself.

- `domain/` - `Account`, `Folder`, `MailMessage`, `Capabilities`, `ProviderKind`, `UserSettings`.
- `data/` - `MailRepository` (the only SQL user; schema in `sqldelight/.../Mail.sq`), `SettingsStore`
  (key=value snapshot, settings only), `SecretStore` (refresh tokens + IMAP passwords; never in the DB).
- `auth/` - `OAuthProvider` (Google / Microsoft / Yahoo endpoints and scopes), `AuthManager`
  (exchange, refresh, revoke, passwords), `Authorizer` (desktop: `LoopbackAuthorizer`; Android:
  Play-services Authorization API for Google, Custom Tabs for Microsoft). `OAuthSecrets` is GENERATED.
- `provider/` - `MailProvider` interface; `gmail/` (REST) and `imap/` (Angus Mail, jvmSharedMain).
  Providers never touch the DB: they take stored state and return a `SyncBatch`.
  Sending: Gmail posts raw MIME (`MimeBuilder`, common code) to the API; IMAP accounts send over
  SMTP next to the IMAP server with the same credential (XOAUTH2 or password), and a generic IMAP
  account also gets the copy appended to its Sent folder. "Delete" always means move to trash.
  Microsoft needs the `SMTP.Send` scope: accounts signed in before it must sign in again to send.
- `sync/` - `SyncEngine`: `syncOnce(account)` (shared by the desktop loop, the open Android app and
  the Android `SyncWorker`), per-account mutex, backoff, optimistic actions.
- `notify/` - `Notifier` (desktop: Nucleus; Android: NotificationCompat), `MailNotifications`
  (individual vs summary), `NotificationActions` (OS -> app channel).
- `app/`, `main/`, `ui/`, `theme/` - MVI + screens. `LocalCompactLayout` switches to phone layout.
- `platform/` - expect/actual. `di/` - Metro graph; `AppGraphHolder` = one graph per process.

**Capability rule:** every action button checks `account.capabilities`. Never show something the
account cannot do.

**Schema change:** SQLDelight migrations (`.sqm`) once 1.0 ships; before that a schema change may
bump the DB. No UPSERT in SQL: Android 8-10 ship SQLite older than 3.24.

**Adding a setting:** field on `UserSettings` -> key in `SnapshotCodec` -> intent -> row in
`SettingsScreen`. A setting that skips `SnapshotCodec` silently forgets itself.

**Adding a string:** both `values/strings.xml` and `values-he/strings.xml`. `StringResourcesTest`
fails the build when keys or placeholders differ between languages.

**Languages and direction:** UI is Hebrew + English; default follows the OS (English when the OS
language is unsupported). Layout direction comes from `UiLanguage.rtl` via `LocalLayoutDirection`,
so use start/end, never left/right, and `Icons.AutoMirrored.*` for directional icons. Mail content
uses `TextDirection.Content`; addresses, hosts and passwords use `TextDirection.Ltr`.
Adding a language = a `UiLanguage` entry (with its `rtl` flag) + a `values-xx` folder.

## Conventions

- Code comments and console output (`println`) in English only - Hebrew does not render in the
  Windows console. UI strings live in resources.
- Never log tokens, passwords, auth codes, addresses or message content.

## Dependency Management

- Version catalog only (`gradle/libs.versions.toml`).
- Pinned train: Kotlin 2.4.10 / Compose 1.11.1 / Nucleus 2.4.7. Bumping material3 or anything that
  pulls compose.ui 1.12 breaks the Tao decorated window.
- `composenativetray` stays on 2.1.0 - later versions are built on Compose 1.12.
