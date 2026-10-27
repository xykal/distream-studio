/**
 * Cloudflare Worker entry untuk DiStream / Streaming In backend.
 *
 * Backend aslinya ditulis sebagai handler gaya Node (req, res) untuk Vercel Serverless.
 * File ini menjembatani Fetch API milik Workers ke handler tersebut, sehingga SATU
 * sumber kode (server.js + saasEngine.js) dipakai di dua platform: Vercel dan Workers.
 *
 * Catatan:
 * - WebSocket relay (chat/gift TikTok) TIDAK tersedia di sini: butuh Durable Object.
 *   Semua endpoint HTTP (checkout Pakasir, lisensi, webhook donasi) tetap berfungsi.
 * - Route "/" tidak membaca file dari disk (Workers tidak punya fs), jadi dilayani
 *   halaman status ringkas di bawah.
 */

import { createDiStreamHttpHandler } from "./server.js";

const jsonHeaders = { "Content-Type": "application/json; charset=utf-8" };

const STATUS_PAGE = `<!DOCTYPE html>
<html lang="id"><head><meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Streaming In — Cloud API</title>
<style>
  body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0B0D12;color:#E5E7EB;
       font-family:system-ui,-apple-system,sans-serif}
  main{max-width:520px;padding:32px;border:1px solid #1F2430;border-radius:16px;background:#11141B}
  h1{font-size:20px;margin:0 0 4px}
  p{font-size:13.5px;line-height:1.6;color:#9CA3AF;margin:8px 0 0}
  code{background:#1A1F29;padding:2px 6px;border-radius:6px;font-size:12.5px;color:#93C5FD}
  ul{font-size:13px;color:#9CA3AF;line-height:1.9;padding-left:18px;margin:12px 0 0}
</style></head>
<body><main>
  <h1>Streaming In — Cloud API</h1>
  <p>Layanan backend aktif di Cloudflare Workers.</p>
  <ul>
    <li><code>GET /api/health</code></li>
    <li><code>GET /api/saas/plans</code></li>
    <li><code>POST /api/saas/checkout</code></li>
    <li><code>POST /api/saas/license/verify</code></li>
    <li><code>POST /webhook/donation/:roomToken</code></li>
  </ul>
  <p>WebSocket relay chat/gift TikTok belum tersedia di node ini (butuh Durable Object).</p>
</main></body></html>`;

function makeNodeStyleRequest(request, url, bodyBuffer) {
  const headers = {};
  for (const [k, v] of request.headers.entries()) headers[k.toLowerCase()] = v;
  headers["host"] = headers["host"] || url.host;

  const listeners = { data: [], end: [], error: [] };
  const req = {
    method: request.method,
    url: url.pathname + url.search,
    headers,
    // body sudah dibaca di depan: readJsonBody() di server.js memakai jalur ini
    body: undefined,
    on(event, fn) {
      (listeners[event] || (listeners[event] = [])).push(fn);
      return req;
    }
  };

  if (bodyBuffer && bodyBuffer.byteLength > 0) {
    const text = new TextDecoder().decode(bodyBuffer);
    try {
      req.body = JSON.parse(text);
    } catch {
      req.body = undefined;
      // biarkan jalur stream di bawah yang menangani JSON rusak
      setTimeout(() => {
        for (const fn of listeners.data) fn(text);
        for (const fn of listeners.end) fn();
      }, 0);
      return req;
    }
  } else {
    setTimeout(() => {
      for (const fn of listeners.end) fn();
    }, 0);
  }
  return req;
}

function makeNodeStyleResponse() {
  const state = { status: 200, headers: {}, body: "" };
  const res = {
    setHeader(name, value) {
      state.headers[name] = value;
    },
    getHeader(name) {
      return state.headers[name];
    },
    writeHead(status, maybeHeaders) {
      state.status = status;
      if (maybeHeaders && typeof maybeHeaders === "object") {
        for (const [k, v] of Object.entries(maybeHeaders)) state.headers[k] = v;
      }
      return res;
    },
    write(chunk) {
      if (chunk) state.body += typeof chunk === "string" ? chunk : new TextDecoder().decode(chunk);
      return true;
    },
    end(chunk) {
      if (chunk) res.write(chunk);
    },
    state
  };
  return res;
}

export default {
  async fetch(request, env) {
    // Workers: environment variable masuk ke env, sedangkan server.js membacanya dari process.env
    if (typeof globalThis.process === "undefined") {
      globalThis.process = { env: {} };
    }
    globalThis.process.env = { ...(globalThis.process.env || {}), ...env };

    const url = new URL(request.url);

    if (url.pathname === "/" || url.pathname === "/index.html") {
      return new Response(STATUS_PAGE, {
        status: 200,
        headers: { "Content-Type": "text/html; charset=utf-8" }
      });
    }

    let bodyBuffer = null;
    if (request.method !== "GET" && request.method !== "HEAD") {
      bodyBuffer = await request.arrayBuffer();
    }

    const { handler } = createDiStreamHttpHandler({ enableExternalGatewayCalls: true });
    const req = makeNodeStyleRequest(request, url, bodyBuffer);
    const res = makeNodeStyleResponse();

    try {
      await handler(req, res);
    } catch (err) {
      return new Response(
        JSON.stringify({ error: err?.message || "Worker handler error" }),
        { status: 500, headers: jsonHeaders }
      );
    }

    return new Response(res.state.body || null, {
      status: res.state.status,
      headers: res.state.headers
    });
  }
};
