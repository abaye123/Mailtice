# Mailtice browser extension

Exports a Gmail mailbox to a `.mailtice` transfer file from a computer that has internet but no
Mailtice. The app imports the file on a computer without internet (Settings > Computer without
internet > Import). It works in Chrome, Edge, Brave and other Chromium browsers (116+).

- Read only (`gmail.readonly`): nothing in the mailbox changes, and mail goes nowhere but the file.
- Choose the period (everything, a year, 3 months, 30 days), the attachment size limit, and whether
  to include spam and trash. Gmail's archived mail (no label) lands in an Archive folder in the app.
- The file streams to disk through the save dialog, so mailbox size is not limited by memory.

## Install

From a release: download `mailtice-extension-<version>.zip`, unzip it, open `chrome://extensions`
(or `edge://extensions`), turn on **Developer mode** and choose **Load unpacked** on the folder.

From the source: `./build.sh` writes `dist/mailtice-extension/`; load that folder the same way.

## Google setup (once)

The extension signs in with a Google OAuth client of type **Web application** - the same client the
Android app uses (`GOOGLE_WEB_CLIENT_ID`) works:

1. In the Google Cloud Console, open the client and add this **Authorized redirect URI**:
   `https://pkhenaholjpdalldmcndfgkfdpabkjan.chromiumapp.org/`
   (the extension's ID is fixed by the `key` in `manifest.json`; the setup box in the extension
   shows the address too).
2. On the consent screen, add the `gmail.readonly` scope.
3. A release built with the `GOOGLE_WEB_CLIENT_ID` secret has the client built in. Otherwise paste
   the client ID in the extension's setup box.

## The file

The layout is described in `shared/src/commonMain/kotlin/co/abaye/mailtice/transfer/TransferFormat.kt`.
The extension writes messages in Gmail's own API format (`format=full`), and the app reads them with
the code its Gmail sync uses, so they look exactly as if the app had downloaded them.

## Test

```bash
node test/export.test.mjs /tmp/export-test.zip
```
