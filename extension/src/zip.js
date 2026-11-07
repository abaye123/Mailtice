// A streaming zip writer: entries go to the output one after another, so an archive of any size
// is written without holding it in memory. The app reads it with java.util.zip.ZipInputStream,
// which needs every entry's sizes and checksum in its local header - so each entry is buffered on
// its own (they are small: a chunk of messages or one attachment), never with a data descriptor.
// ZIP64 records are added once the archive passes 4 GB or 65,535 entries.

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

export function crc32(bytes) {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

const MAX32 = 0xffffffff;
const MAX16 = 0xffff;

async function deflateRaw(bytes) {
  const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function dosDateTime(date) {
  const time = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
  const day = ((date.getFullYear() - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
  return { time, day };
}

/** Little-endian byte builder. */
class Bytes {
  constructor(size) {
    this.buffer = new Uint8Array(size);
    this.view = new DataView(this.buffer.buffer);
    this.at = 0;
  }
  u16(v) { this.view.setUint16(this.at, v, true); this.at += 2; return this; }
  u32(v) { this.view.setUint32(this.at, v >>> 0, true); this.at += 4; return this; }
  u64(v) {
    this.view.setUint32(this.at, v % 0x100000000, true);
    this.view.setUint32(this.at + 4, Math.floor(v / 0x100000000), true);
    this.at += 8;
    return this;
  }
  raw(bytes) { this.buffer.set(bytes, this.at); this.at += bytes.length; return this; }
}

/**
 * Writes a zip to `write`, an async function taking a Uint8Array (a file stream, or a collector).
 * put() must be awaited before the next one; close() writes the central directory.
 */
export class ZipWriter {
  constructor(write, now = new Date()) {
    this.write = write;
    this.offset = 0;
    this.entries = [];
    this.stamp = dosDateTime(now);
    this.encoder = new TextEncoder();
  }

  async emit(bytes) {
    await this.write(bytes);
    this.offset += bytes.length;
  }

  /** `data` is a Uint8Array or a string; `compress` false for content that is compressed already. */
  async put(name, data, compress = true) {
    const bytes = typeof data === "string" ? this.encoder.encode(data) : data;
    if (bytes.length >= MAX32) throw new Error("Entry too large");
    const nameBytes = this.encoder.encode(name);
    const crc = crc32(bytes);
    let method = 0;
    let stored = bytes;
    if (compress && bytes.length > 128 && typeof CompressionStream !== "undefined") {
      const deflated = await deflateRaw(bytes);
      if (deflated.length < bytes.length) {
        method = 8;
        stored = deflated;
      }
    }
    const header = new Bytes(30 + nameBytes.length)
      .u32(0x04034b50)
      .u16(20) // version needed
      .u16(0x0800) // names are UTF-8
      .u16(method)
      .u16(this.stamp.time)
      .u16(this.stamp.day)
      .u32(crc)
      .u32(stored.length)
      .u32(bytes.length)
      .u16(nameBytes.length)
      .u16(0)
      .raw(nameBytes);
    const offset = this.offset;
    await this.emit(header.buffer);
    await this.emit(stored);
    this.entries.push({ nameBytes, crc, method, compressed: stored.length, size: bytes.length, offset });
  }

  async close() {
    const start = this.offset;
    for (const e of this.entries) {
      const zip64 = e.offset >= MAX32;
      const extra = zip64 ? new Bytes(12).u16(0x0001).u16(8).u64(e.offset).buffer : new Uint8Array(0);
      const record = new Bytes(46 + e.nameBytes.length + extra.length)
        .u32(0x02014b50)
        .u16(zip64 ? 45 : 20) // made by
        .u16(zip64 ? 45 : 20) // needed
        .u16(0x0800)
        .u16(e.method)
        .u16(this.stamp.time)
        .u16(this.stamp.day)
        .u32(e.crc)
        .u32(e.compressed)
        .u32(e.size)
        .u16(e.nameBytes.length)
        .u16(extra.length)
        .u16(0) // comment
        .u16(0) // disk
        .u16(0) // internal attributes
        .u32(0) // external attributes
        .u32(zip64 ? MAX32 : e.offset)
        .raw(e.nameBytes)
        .raw(extra);
      await this.emit(record.buffer);
    }
    const size = this.offset - start;
    const count = this.entries.length;
    const needs64 = count >= MAX16 || start >= MAX32 || size >= MAX32;
    if (needs64) {
      const end64 = this.offset;
      await this.emit(
        new Bytes(56)
          .u32(0x06064b50)
          .u64(44)
          .u16(45)
          .u16(45)
          .u32(0)
          .u32(0)
          .u64(count)
          .u64(count)
          .u64(size)
          .u64(start).buffer,
      );
      await this.emit(new Bytes(20).u32(0x07064b50).u32(0).u64(end64).u32(1).buffer);
    }
    await this.emit(
      new Bytes(22)
        .u32(0x06054b50)
        .u16(0)
        .u16(0)
        .u16(needs64 ? MAX16 : count)
        .u16(needs64 ? MAX16 : count)
        .u32(needs64 ? MAX32 : size)
        .u32(needs64 ? MAX32 : start)
        .u16(0).buffer,
    );
  }
}

/**
 * Gathers small writes into large ones: a zip is many small pieces (headers, chunks of messages),
 * and every separate write to a file on disk costs far more than its bytes.
 */
export function bufferedWrite(write, size = 8 * 1024 * 1024) {
  let parts = [];
  let length = 0;
  const flush = async () => {
    if (!length) return;
    const joined = new Uint8Array(length);
    let at = 0;
    for (const p of parts) {
      joined.set(p, at);
      at += p.length;
    }
    parts = [];
    length = 0;
    await write(joined);
  };
  return {
    write: async (bytes) => {
      if (bytes.length >= size) {
        await flush();
        await write(bytes);
        return;
      }
      parts.push(bytes);
      length += bytes.length;
      if (length >= size) await flush();
    },
    flush,
  };
}
