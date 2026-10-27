/**
 * Cloudflare Worker entry untuk backend DiStream Studio.
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
import { RoomRelay } from "./worker-room-do.js";
// Overlay HTML diimpor sebagai teks oleh esbuild (--loader:.html=text), jadi tidak butuh fs.
import overlayTemplate from "./overlay.html";

export { RoomRelay };

const jsonHeaders = { "Content-Type": "application/json; charset=utf-8" };

const STATUS_PAGE = `<!DOCTYPE html>
<html lang="id"><head><meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>DiStream Studio — Cloud API</title>
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
  <h1>DiStream Studio — Cloud API</h1>
  <p>Layanan backend aktif di Cloudflare Workers.</p>
  <ul>
    <li><code>GET /api/health</code></li>
    <li><code>GET /api/saas/plans</code></li>
    <li><code>POST /api/saas/checkout</code></li>
    <li><code>POST /api/saas/license/verify</code></li>
    <li><code>POST /webhook/donation/:roomToken</code> (Saweria, Sociabuzz, Trakteer, Tako, Sibagi, TipTap, BagiBagi)</li>
    <li><code>GET /ws?room=nama-room</code> (WebSocket relay donasi)</li>
    <li><code>GET /overlay?room=nama-room&amp;goal=500000&amp;ticker=...</code> (Browser Source OBS)</li>
  </ul>
  <p>Relay donasi realtime aktif via Durable Object. Event chat/gift TikTok belum tersedia di node ini.</p>
</main></body></html>`;

/**
 * server.js mem-broadcast event ke Map<room, Set<client>> lalu memanggil client.send().
 * Di Workers tidak ada state proses, jadi kita sediakan klien palsu yang meneruskan
 * payload ke Durable Object RoomRelay — logika auth/validasi tetap milik server.js.
 */
function createRelayRooms(env) {
  const rooms = new Map();
  // Push ke Durable Object bersifat async. Tanpa ditahan, Workers memotong promise
  // tersebut begitu response dikirim -> event donasi hilang (bug yang tertangkap di tes).
  const pending = [];
  const makeForwarder = (room) => ({
    readyState: 1,
    OPEN: 1,
    send(payload) {
      const ns = env?.ROOMS;
      if (!ns) return;
      const stub = ns.get(ns.idFromName(room));
      pending.push(
        stub
          .fetch("https://room-relay/publish", { method: "POST", body: payload })
          .then((r) => r.text())
          .catch(() => null)
      );
    }
  });
  const original = rooms.get.bind(rooms);
  rooms.get = (key) => {
    if (!original(key)) rooms.set(key, new Set([makeForwarder(key)]));
    return original(key);
  };
  rooms.set = (key, _value) => {
    Map.prototype.set.call(rooms, key, new Set([makeForwarder(key)]));
    return rooms;
  };
  const flush = async () => {
    if (pending.length === 0) return;
    await Promise.allSettled(pending.splice(0, pending.length));
  };
  return { rooms, flush };
}

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

/**
 * Merakit halaman overlay dari template HTML.
 * Semua nilai berasal dari query string agar streamer bisa punya beberapa overlay URL
 * dengan tema berbeda tanpa deploy ulang.
 */
function buildOverlayPage(url) {
  const q = url.searchParams;
  const room = (q.get("room") || q.get("uniqueId") || "default")
    .replace(/^@/, "")
    .trim()
    .slice(0, 64) || "default";
  // Preset tema supaya streamer tidak perlu hafal kode hex.
  const THEMES = {
    neon: "#22D3EE",
    sunset: "#FF5A2C",
    mint: "#34D399",
    gold: "#FBBF24",
    pink: "#FB7185",
    mono: "#E5E7EB"
  };
  const theme = (q.get("theme") || "").toLowerCase();
  const accentRaw = (q.get("accent") || THEMES[theme] || THEMES.neon).trim();
  const accent = /^#?[0-9a-fA-F]{3,8}$/.test(accentRaw)
    ? (accentRaw.startsWith("#") ? accentRaw : `#${accentRaw}`)
    : "#22D3EE";
  const position = (q.get("pos") || "top").toLowerCase();
  const showConn = q.get("conn") === "1" ? "1" : "0";
  const tickerSpeed = `${Math.max(6, Math.min(40, Number(q.get("speed")) || 18))}s`;

  return overlayTemplate
    .replaceAll("__ROOM__", room)
    .replaceAll("__ACCENT__", accent)
    .replaceAll("__ALERT_TOP__", position === "bottom" ? "62%" : "14%")
    .replaceAll("__RECENT_TOP__", position === "bottom" ? "6%" : "22%")
    .replaceAll("__GOAL_BOTTOM__", position === "bottom" ? "20%" : "6%")
    .replaceAll("__TICKER_SPEED__", tickerSpeed)
    .replaceAll("__SHOW_CONN__", showConn);
}

export default {
  async fetch(request, env) {
    // Workers: environment variable masuk ke env, sedangkan server.js membacanya dari process.env
    if (typeof globalThis.process === "undefined") {
      globalThis.process = { env: {} };
    }
    globalThis.process.env = { ...(globalThis.process.env || {}), ...env };

    const url = new URL(request.url);

    // Relay realtime (pengganti WebSocketServer Node)
    if (url.pathname === "/ws") {
      const room = (url.searchParams.get("room") || url.searchParams.get("uniqueId") || "default")
        .replace(/^@/, "")
        .trim()
        .slice(0, 64);
      if (!env.ROOMS) {
        return new Response(JSON.stringify({ error: "Durable Object ROOMS belum di-bind" }), {
          status: 503,
          headers: jsonHeaders
        });
      }
      const stub = env.ROOMS.get(env.ROOMS.idFromName(room));
      const doUrl = new URL("https://room-relay/socket");
      doUrl.searchParams.set("room", room);
      return stub.fetch(new Request(doUrl.toString(), request));
    }

    // Overlay URL untuk OBS / TikTok Live Studio (Browser Source). Tanpa dependency eksternal:
    // satu halaman HTML yang menyambung ke relay WebSocket di atas.
    if (url.pathname === "/overlay" || url.pathname === "/overlay.html") {
      return new Response(buildOverlayPage(url), {
        status: 200,
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store"
        }
      });
    }

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

    const relay = createRelayRooms(env);
    const { handler } = createDiStreamHttpHandler({
      enableExternalGatewayCalls: true,
      rooms: relay.rooms
    });
    const req = makeNodeStyleRequest(request, url, bodyBuffer);
    const res = makeNodeStyleResponse();

    try {
      await handler(req, res);
      await relay.flush(); // pastikan event realtime benar-benar terkirim ke Durable Object
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
