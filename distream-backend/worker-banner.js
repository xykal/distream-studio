import * as __path from "node:path";
import * as __crypto from "node:crypto";
import * as __url from "node:url";
import * as __util from "node:util";
import * as __stream from "node:stream";
import * as __zlib from "node:zlib";
import * as __buffer from "node:buffer";
import * as __assert from "node:assert";
import * as __events from "node:events";
import * as __stringDecoder from "node:string_decoder";

// Modul yang TIDAK ada di Cloudflare Workers: diberi stub agar bundle tetap bisa dimuat.
// Kode yang memakainya (penyajian file statis, http server, createDiStreamServer) memang
// tidak pernah dipanggil di jalur Worker.
const __fsStub = {
  existsSync: () => false,
  readFileSync: () => { throw new Error("fs.readFileSync tidak tersedia di Workers"); },
  writeFileSync: () => { throw new Error("fs.writeFileSync tidak tersedia di Workers"); },
  promises: {}
};
const __httpStub = { createServer: () => { throw new Error("http.createServer tidak tersedia di Workers"); } };
const __osStub = { platform: () => "workers", cpus: () => [], tmpdir: () => "/tmp" };

function __cjsInterop(ns) {
  if (!ns) return ns;
  const d = ns.default;
  if (typeof d === "function") {
    return new Proxy(d, {
      get(target, prop) { return prop in target ? target[prop] : ns[prop]; },
      has(target, prop) { return prop in target || prop in ns; }
    });
  }
  if (d && typeof d === "object" && Object.keys(ns).length === 1) return d;
  return ns;
}

const __nodeModules = {
  path: __path, crypto: __crypto, url: __url, util: __util, stream: __stream, zlib: __zlib,
  buffer: __buffer, assert: __assert, events: __events, string_decoder: __stringDecoder,
  fs: __fsStub, "fs/promises": __fsStub, http: __httpStub, https: __httpStub, os: __osStub
};
const __interop = {};
for (const k of Object.keys(__nodeModules)) __interop[k] = __cjsInterop(__nodeModules[k]);

globalThis.require = function (id) {
  const key = String(id).replace(/^node:/, "");
  if (__interop[key]) return __interop[key];
  throw new Error("Module not found (worker): " + id);
};
globalThis.__dirname = "/";
globalThis.__filename = "/worker.js";
