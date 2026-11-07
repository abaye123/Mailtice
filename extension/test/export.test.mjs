// node test/export.test.mjs <out.zip> - exports a fake mailbox and checks what was written.
import { writeFileSync } from "node:fs";
import assert from "node:assert/strict";
import { exportMailbox, fileParts, CHUNK } from "../src/exporter.js";
import { ZipWriter, crc32 } from "../src/zip.js";

const b64url = (s) => Buffer.from(s).toString("base64url");
const COUNT = CHUNK + 7; // two chunks
const message = (i) => ({
  id: `m${i}`,
  threadId: `t${i}`,
  labelIds: i % 2 ? ["INBOX", "UNREAD"] : [],
  snippet: `snippet ${i}`,
  internalDate: String(1_700_000_000_000 + i),
  payload: {
    mimeType: "multipart/mixed",
    headers: [{ name: "From", value: "Dana <dana@example.com>" }, { name: "Subject", value: `שלום ${i}` }],
    parts: [
      { mimeType: "text/plain", headers: [], body: { size: 5, data: b64url(`body ${i}`) } },
      { mimeType: "image/png", filename: "", headers: [{ name: "Content-ID", value: "<logo>" }], body: { size: 3, data: b64url("png") } },
      { mimeType: "application/pdf", filename: "big.pdf", headers: [], body: { size: 50 * 1024 * 1024, attachmentId: "big" } },
      { mimeType: "application/pdf", filename: "a.pdf", headers: [], body: { size: 4, attachmentId: `att${i}` } },
    ],
  },
});
const client = {
  profile: async () => ({ emailAddress: "dana@example.com" }),
  labels: async () => [{ id: "INBOX", name: "INBOX", type: "system" }],
  messageIds: async (q, spam) => {
    assert.equal(q, "newer_than:30d");
    assert.equal(spam, false);
    return Array.from({ length: COUNT }, (_, i) => `m${i}`);
  },
  message: async (id) => (id === "m3" ? null : message(Number(id.slice(1)))),
  attachment: async (_, attId) => b64url(`pdf-${attId}`),
};

assert.deepEqual(fileParts(message(0).payload).map((p) => p.filename || p.mimeType), ["image/png", "big.pdf", "a.pdf"]);
assert.equal(crc32(new TextEncoder().encode("123456789")), 0xcbf43926);

const chunks = [];
const zip = new ZipWriter(async (bytes) => chunks.push(Buffer.from(bytes)));
const result = await exportMailbox({
  client,
  zip,
  options: { days: 30, includeSpamTrash: false, attachmentLimit: 10 * 1024 * 1024 },
  generator: "test",
});
assert.equal(result.messages, COUNT - 1); // m3 was deleted meanwhile
assert.equal(result.files, (COUNT - 1) * 2); // the inline image and a.pdf; big.pdf is over the limit
writeFileSync(process.argv[2], Buffer.concat(chunks));
console.log("export ok", result);

// The same mailbox through batch requests: the same file.
const batched = { ...client, messages: async (ids) => Promise.all(ids.map((id) => client.message(id))) };
const chunks2 = [];
const result2 = await exportMailbox({
  client: batched,
  zip: new ZipWriter(async (bytes) => chunks2.push(Buffer.from(bytes))),
  options: { days: 30, includeSpamTrash: false, attachmentLimit: 10 * 1024 * 1024 },
  generator: "test",
});
assert.deepEqual(result2, result);
console.log("batched export ok");
