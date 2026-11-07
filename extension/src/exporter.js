// Writes a Gmail mailbox as a Mailtice transfer file. The layout is the app's
// (shared/src/commonMain/kotlin/co/abaye/mailtice/transfer/TransferFormat.kt):
//
//   manifest.json
//   accounts/a1/account.json            the address and Gmail's labels as the API gives them
//   accounts/a1/messages/00000.jsonl    {"n":0,"gmail":{...full format...},"files":[0]} per line
//   accounts/a1/files/<n>/<index>       attachment bytes, after the chunk that lists them
//
// Messages are stored in Gmail's own format and the app reads them with the code its Gmail sync
// uses, so a message looks the same whichever way it arrived.

import { BATCH_SIZE, base64UrlDecode } from "./gmail.js";

export const FORMAT = "mailtice-transfer";
export const VERSION = 1;
export const CHUNK = 200;
/** Batch requests in flight at once; the quota limiter in gmail.js sets the real pace. */
const BATCHES_IN_FLIGHT = 3;
const ATTACHMENT_PARALLEL = 8;

/**
 * The parts the app counts as attachments, in its order: anything with a file name, and images
 * the HTML shows by Content-ID (GmailProvider.kt, isFilePart). Index n here is attachment n there.
 */
export function fileParts(payload) {
  const out = [];
  const walk = (part) => {
    if (!part) return;
    const cid = (part.headers || []).some((h) => h.name.toLowerCase() === "content-id" && h.value.trim());
    if ((part.filename || "") !== "" || ((part.mimeType || "").startsWith("image/") && cid)) out.push(part);
    for (const child of part.parts || []) walk(child);
  };
  walk(payload);
  return out;
}

/** Gmail search for the chosen window; "" = all mail. */
export function query(days) {
  return days > 0 ? `newer_than:${days}d` : "";
}

const pad = (n) => String(n).padStart(5, "0");

/** Runs `task` over `items` with at most `limit` at a time, keeping the order of the results. */
async function pool(items, limit, task, signal) {
  const results = new Array(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      if (signal && signal.aborted) throw new DOMException("Cancelled", "AbortError");
      const i = next++;
      results[i] = await task(items[i], i);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

/** A chunk's messages in order, by batch requests where the client has them; deleted ones dropped. */
async function fetchChunk(client, ids, signal) {
  if (!client.messages) return (await pool(ids, 5, (id) => client.message(id), signal)).filter(Boolean);
  const batches = [];
  for (let i = 0; i < ids.length; i += BATCH_SIZE) batches.push(ids.slice(i, i + BATCH_SIZE));
  const results = await pool(batches, BATCHES_IN_FLIGHT, (batch) => client.messages(batch), signal);
  return results.flat().filter(Boolean);
}

/**
 * Exports the signed-in mailbox. `options`: days (0 = everything), includeSpamTrash, and
 * attachmentLimit in bytes (0 = no files, Infinity = all). `onProgress({phase, done, total, files})`.
 * Returns the counts.
 */
export async function exportMailbox({ client, zip, options, generator, onProgress = () => {}, signal }) {
  const profile = await client.profile();
  const email = profile.emailAddress;
  const labels = await client.labels();
  onProgress({ phase: "listing", done: 0, total: 0, files: 0 });
  const ids = await client.messageIds(query(options.days), options.includeSpamTrash, (n) => onProgress({ phase: "listing", done: n, total: 0, files: 0 }));

  await zip.put(
    "manifest.json",
    JSON.stringify({ format: FORMAT, version: VERSION, createdAt: Date.now(), generator, accounts: ["a1"] }),
  );
  await zip.put(
    "accounts/a1/account.json",
    JSON.stringify({ key: "a1", kind: "Gmail", email, gmailLabels: labels, messages: ids.length }),
  );

  let done = 0;
  let files = 0;
  let n = 0;
  const chunks = [];
  for (let start = 0; start < ids.length; start += CHUNK) chunks.push(ids.slice(start, start + CHUNK));
  // The next chunk downloads while this one's attachments do, so the connection never sits idle.
  let next = chunks.length ? fetchChunk(client, chunks[0], signal) : null;
  for (let chunk = 0; chunk < chunks.length; chunk++) {
    const messages = await next;
    next = chunk + 1 < chunks.length ? fetchChunk(client, chunks[chunk + 1], signal) : null;
    const lines = [];
    const wanted = [];
    for (const message of messages) {
      const number = n++;
      const indexes = [];
      if (options.attachmentLimit > 0) {
        fileParts(message.payload).forEach((part, index) => {
          if ((part.body?.size || 0) <= options.attachmentLimit) {
            indexes.push(index);
            wanted.push({ number, index, messageId: message.id, part });
          }
        });
      }
      lines.push(JSON.stringify(indexes.length ? { n: number, files: indexes, gmail: message } : { n: number, gmail: message }));
    }
    await zip.put(`accounts/a1/messages/${pad(chunk)}.jsonl`, lines.join("\n"));
    done += chunks[chunk].length;
    onProgress({ phase: "messages", done, total: ids.length, files });

    // After the chunk that lists them, several at a time, each written as soon as it arrives; one
    // that fails is left out, not fatal.
    let writing = Promise.resolve();
    await pool(wanted, ATTACHMENT_PARALLEL, async ({ number, index, messageId, part }) => {
      let bytes = null;
      try {
        const raw = part.body?.data ?? (part.body?.attachmentId ? await client.attachment(messageId, part.body.attachmentId) : "");
        bytes = base64UrlDecode(raw);
      } catch {
        bytes = null;
      }
      if (!bytes) return;
      writing = writing.then(() => zip.put(`accounts/a1/files/${number}/${index}`, bytes, false)).then(() => {
        files++;
        onProgress({ phase: "messages", done, total: ids.length, files });
      });
      await writing;
    }, signal);
    await writing;
  }
  await zip.close();
  return { email, messages: n, files };
}
