// node test/batch.test.mjs - the batch request path, against answers shaped like Gmail's.
import assert from "node:assert/strict";
import { GmailClient, QuotaLimiter, parseBatch } from "../src/gmail.js";
import { bufferedWrite } from "../src/zip.js";

const CRLF = "\r\n";
function batchAnswer(boundary, parts) {
  return parts.map(({ id, status, body }) =>
    `--${boundary}${CRLF}Content-Type: application/http${CRLF}Content-ID: <response-${id}>${CRLF}${CRLF}` +
    `HTTP/1.1 ${status} ${status === 200 ? "OK" : "Error"}${CRLF}Content-Type: application/json; charset=UTF-8${CRLF}Vary: Origin${CRLF}${CRLF}` +
    `${body}${CRLF}`).join("") + `--${boundary}--${CRLF}`;
}

// The parser: ids, statuses and bodies, with CRLF line ends.
{
  const text = batchAnswer("batch_x1", [
    { id: "m0", status: 200, body: JSON.stringify({ id: "a", snippet: "x\ny" }) },
    { id: "m1", status: 404, body: '{"error":{"code":404}}' },
  ]);
  const parts = parseBatch(text, "batch_x1");
  assert.equal(parts.get("m0").status, 200);
  assert.equal(JSON.parse(parts.get("m0").body).snippet, "x\ny");
  assert.equal(parts.get("m1").status, 404);
}

// The client: one batch request for many ids, a 404 as null, a 429 fetched again on its own.
{
  const calls = { batch: 0, single: [] };
  globalThis.fetch = async (url, init = {}) => {
    if (url.startsWith("https://gmail.googleapis.com/batch/")) {
      calls.batch++;
      const ids = [...init.body.matchAll(/messages\/([^?]+)\?format=full/g)].map((m) => m[1]);
      const parts = ids.map((id, i) => ({
        id: `m${i}`,
        status: id === "gone" ? 404 : id === "busy" ? 429 : 200,
        body: id === "gone" || id === "busy" ? '{"error":{}}' : JSON.stringify({ id, labelIds: ["INBOX"] }),
      }));
      return new Response(batchAnswer("batch_srv", parts), { headers: { "Content-Type": "multipart/mixed; boundary=batch_srv" } });
    }
    const single = url.match(/messages\/([^?]+)\?/);
    calls.single.push(single[1]);
    return new Response(JSON.stringify({ id: single[1] }), { headers: { "Content-Type": "application/json" } });
  };
  const auth = { token: async () => "t" };
  const client = new GmailClient(auth, new QuotaLimiter(100000));
  const ids = ["a", "gone", "busy", ...Array.from({ length: 40 }, (_, i) => `x${i}`)];
  const got = await client.messages(ids);
  assert.equal(calls.batch, 1);
  assert.deepEqual(calls.single, ["busy"]);
  assert.equal(got.length, ids.length);
  assert.equal(got[0].id, "a");
  assert.equal(got[1], null);
  assert.equal(got[2].id, "busy");
  assert.equal(got[42].id, "x39");
}

// The limiter keeps to its rate: 3 takes of 5 units at 10 units/second take about a second.
{
  const limiter = new QuotaLimiter(10);
  const start = Date.now();
  for (let i = 0; i < 3; i++) await limiter.take(5);
  const took = Date.now() - start;
  assert.ok(took >= 400 && took < 2500, `took ${took} ms`);
}

// Google's real numbers: batches of 250 units on a 230-units-a-second budget must keep going, at
// about one batch a second (this once waited forever).
{
  const limiter = new QuotaLimiter(230);
  const start = Date.now();
  for (let i = 0; i < 3; i++) await limiter.take(250);
  const took = Date.now() - start;
  assert.ok(took < 4000, `took ${took} ms`);
  assert.ok(took >= 1500, `too fast: ${took} ms`);
}

// Google's per-minute quota refusal is waited out, not the end of the export.
{
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    if (calls === 1) {
      return new Response(JSON.stringify({ error: { code: 403, message: "Quota exceeded for quota metric 'Total Query Cost' and limit 'Units per minute per user'" } }), { status: 403 });
    }
    return new Response(JSON.stringify({ id: "q1" }), { headers: { "Content-Type": "application/json" } });
  };
  const client = new GmailClient({ token: async () => "t" }, new QuotaLimiter(100000));
  const got = await client.message("q1");
  assert.equal(got.id, "q1");
  assert.equal(calls, 2);
}

// Buffered writes: the same bytes, in far fewer writes.
{
  const writes = [];
  const out = bufferedWrite(async (b) => writes.push(b), 10);
  for (let i = 0; i < 25; i++) await out.write(new Uint8Array([i]));
  await out.write(new Uint8Array(12).fill(7));
  await out.flush();
  const all = writes.flatMap((w) => [...w]);
  assert.deepEqual(all.slice(0, 25), Array.from({ length: 25 }, (_, i) => i));
  assert.equal(all.length, 37);
  assert.ok(writes.length <= 5, `${writes.length} writes`);
}

console.log("batch ok");
