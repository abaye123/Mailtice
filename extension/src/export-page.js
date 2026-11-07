import { DEFAULT_CLIENT_ID } from "./config.js";
import { exportMailbox } from "./exporter.js";
import { AuthError, GmailClient, GoogleAuth, redirectUrl } from "./gmail.js";
import { localize, t } from "./i18n.js";
import { ZipWriter, bufferedWrite } from "./zip.js";

localize();

const $ = (id) => document.getElementById(id);
const MB = 1024 * 1024;

let auth = null;
let running = null;

async function clientId() {
  const stored = await chrome.storage.local.get("clientId");
  return (stored.clientId || DEFAULT_CLIENT_ID || "").trim();
}

function setStatus(text, { error = false, progress = null, busy = false } = {}) {
  $("status").hidden = false;
  $("status").classList.toggle("error", error);
  $("statusText").textContent = text;
  $("keepOpen").hidden = !busy;
  if (progress === null) $("bar").removeAttribute("value");
  else $("bar").value = progress;
  $("bar").hidden = !busy && progress === null;
}

function showSignedIn(next) {
  auth = next;
  $("account").textContent = t("signedInAs", next.email);
  $("signin").textContent = t("switchAccount");
  $("signin").classList.remove("primary");
  $("start").disabled = false;
  $("status").hidden = true;
}

/** The account signed in last time, back without a window when Google allows it. */
async function resumeSignIn() {
  const id = await clientId();
  const { email } = await chrome.storage.local.get("email");
  if (!id || !email) return;
  const next = new GoogleAuth(id);
  if (await next.resume(email)) showSignedIn(next);
}

async function signIn() {
  const id = await clientId();
  if (!id) {
    $("setupBox").open = true;
    setStatus(t("noClient"), { error: true });
    return;
  }
  try {
    const next = new GoogleAuth(id);
    await next.signIn();
    const profile = await new GmailClient(next).profile();
    next.email = profile.emailAddress;
    await chrome.storage.local.set({ email: profile.emailAddress });
    // The address is known only now: keep the session's token under it.
    try {
      await chrome.storage.session?.set({ token: { value: next.accessToken, expiresAt: next.expiresAt, email: next.email } });
    } catch {
      // No session storage: the quiet sign-in covers the next visit.
    }
    showSignedIn(next);
  } catch (e) {
    console.warn("Mailtice: sign-in failed", e);
    $("setupBox").open = true;
    setStatus(t("authFailed"), { error: true });
  }
}

/**
 * Where the file goes: straight to disk through the save dialog where the browser has one (any
 * size), else gathered into a download.
 */
async function openOutput(name) {
  if (window.showSaveFilePicker) {
    const handle = await window.showSaveFilePicker({
      suggestedName: name,
      types: [{ description: "Mailtice", accept: { "application/zip": [".mailtice"] } }],
    });
    const stream = await handle.createWritable();
    return {
      name: handle.name,
      write: (bytes) => stream.write(bytes),
      finish: () => stream.close(),
      discard: () => stream.abort(),
    };
  }
  const parts = [];
  return {
    name,
    write: async (bytes) => { parts.push(bytes); },
    finish: async () => {
      const url = URL.createObjectURL(new Blob(parts, { type: "application/zip" }));
      const a = Object.assign(document.createElement("a"), { href: url, download: name });
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
    discard: async () => { parts.length = 0; },
  };
}

async function start() {
  if (!auth || running) return;
  const date = new Date().toISOString().slice(0, 10);
  const safe = auth.email.replace(/[^a-zA-Z0-9.@_-]/g, "_");
  let output;
  try {
    output = await openOutput(`gmail-${safe}-${date}.mailtice`);
  } catch {
    return; // the save dialog was closed
  }
  const attachments = Number($("attachments").value);
  chrome.storage.local.set({ choices: { range: $("range").value, attachments: $("attachments").value, spamTrash: $("spamTrash").checked } });
  const options = {
    days: Number($("range").value),
    includeSpamTrash: $("spamTrash").checked,
    attachmentLimit: attachments < 0 ? Infinity : attachments * MB,
  };
  const controller = new AbortController();
  running = controller;
  $("start").disabled = true;
  $("signin").disabled = true;
  $("cancel").hidden = false;
  setStatus(t("listing", 0), { busy: true });
  try {
    const buffered = bufferedWrite(output.write);
    const zip = new ZipWriter(buffered.write);
    const result = await exportMailbox({
      client: new GmailClient(auth),
      zip,
      options,
      generator: `Mailtice extension ${chrome.runtime.getManifest().version}`,
      signal: controller.signal,
      onProgress: ({ phase, done, total, files }) => {
        if (phase === "listing") setStatus(t("listing", done), { busy: true });
        else setStatus(t("progress", done, total, files), { busy: true, progress: total ? done / total : 1 });
      },
    });
    await buffered.flush();
    await output.finish();
    setStatus(t("done", result.messages, result.email, output.name), { progress: 1 });
  } catch (e) {
    await output.discard().catch(() => {});
    if (e && e.name === "AbortError") setStatus(t("cancelled"));
    else if (e instanceof AuthError) setStatus(t("authFailed"), { error: true });
    else setStatus(t("failed", (e && e.message) || String(e)), { error: true });
    console.warn("Mailtice: export failed", e);
  } finally {
    running = null;
    $("start").disabled = false;
    $("signin").disabled = false;
    $("cancel").hidden = true;
  }
}

$("signin").addEventListener("click", signIn);
$("start").addEventListener("click", start);
$("cancel").addEventListener("click", () => running && running.abort());
$("redirect").textContent = redirectUrl();
$("copy").addEventListener("click", () => navigator.clipboard.writeText(redirectUrl()));
$("saveClient").addEventListener("click", async () => {
  await chrome.storage.local.set({ clientId: $("clientId").value.trim() });
  $("savedNote").textContent = t("saved");
});
window.addEventListener("beforeunload", (event) => {
  if (running) event.preventDefault();
});

clientId().then((id) => {
  $("clientId").value = id;
  if (!id) $("setupBox").open = true;
});
resumeSignIn();

// The choices made last time.
chrome.storage.local.get("choices").then(({ choices }) => {
  if (!choices) return;
  if (choices.range) $("range").value = choices.range;
  if (choices.attachments) $("attachments").value = choices.attachments;
  $("spamTrash").checked = Boolean(choices.spamTrash);
});
