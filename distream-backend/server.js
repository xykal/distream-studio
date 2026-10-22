/**
 * DiStream Studio Backend Server v4.0
 * Real Pakasir API v2 + Stateless Cloud Licensing + Resend Email + Live WebSocket Event Bus
 */

const fs = require("fs");
const path = require("path");
const http = require("http");
const { URL } = require("url");
const { SaasBillingEngine } = require("./saasEngine");

const BLOCKED_TERMS = [
  "judol", "slot gacor", "zeus88", "kasino", "bunuh", "anjing", "bangsat", "kontol", "memek"
];

function sanitizeMessage(input) {
  if (!input || typeof input !== "string") return "";
  let cleaned = input.slice(0, 220);
  for (const term of BLOCKED_TERMS) {
    const regex = new RegExp(term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "gi");
    cleaned = cleaned.replace(regex, "***");
  }
  return cleaned;
}

function readJsonBody(req) {
  if (req.body && typeof req.body === "object") {
    return Promise.resolve(req.body);
  }
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", (chunk) => {
      if (raw.length < 65_536) raw += chunk;
    });
    req.on("end", () => {
      try {
        resolve(JSON.parse(raw || "{}"));
      } catch {
        reject(new Error("Malformed JSON payload"));
      }
    });
    req.on("error", reject);
  });
}

function createDiStreamHttpHandler(options = {}) {
  const webhookSecret = options.webhookSecret ?? process.env.DISTREAM_SECRET ?? "";
  const saas = options.saasEngine || new SaasBillingEngine(options);
  const rooms = options.rooms || new Map();

  function broadcastToRoom(roomToken, payload) {
    const clients = rooms.get(roomToken);
    if (!clients) return;
    const serialized = JSON.stringify(payload);
    for (const client of clients) {
      if (client.readyState === client.OPEN) {
        client.send(serialized);
      }
    }
  }

  async function handler(req, res) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Stream-Token, X-Secret");

    if (req.method === "OPTIONS") {
      res.writeHead(204);
      res.end();
      return;
    }

    const parsedUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    const pathname = parsedUrl.pathname;

    // Serve Live Interactive Web Studio at root /
    if (req.method === "GET" && (pathname === "/" || pathname === "/index.html")) {
      const candidates = [
        path.resolve(__dirname, "public", "index.html"),
        path.resolve(__dirname, "..", "docs", "index.html"),
        path.resolve("/home/user/DiStream_Studio_Preview.html")
      ];
      for (const htmlPath of candidates) {
        if (fs.existsSync(htmlPath)) {
          res.setHeader("Content-Type", "text/html; charset=utf-8");
          res.writeHead(200);
          res.end(fs.readFileSync(htmlPath));
          return;
        }
      }
    }

    res.setHeader("Content-Type", "application/json; charset=utf-8");

    try {
      if (req.method === "GET" && pathname === "/api/health") {
        res.writeHead(200);
        res.end(
          JSON.stringify({
            status: "ok",
            service: "DiStream Studio Cloud v4.0",
            pakasirApiVersion: "v2",
            activeRooms: rooms.size,
            totalOrders: saas.orders.size,
            pakasirConfigured: Boolean(saas.pakasirProject && saas.pakasirApiKey),
            resendConfigured: Boolean(saas.resendApiKey)
          })
        );
        return;
      }

      if (req.method === "GET" && pathname === "/api/saas/plans") {
        res.writeHead(200);
        res.end(JSON.stringify({ plans: saas.getPlansCatalog() }));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/checkout") {
        const body = await readJsonBody(req);
        const order = await saas.createCheckoutOrder({
          planId: body.planId,
          deviceId: body.deviceId,
          promoCode: body.promoCode,
          email: body.email
        });
        res.writeHead(201);
        res.end(JSON.stringify({ order }));
        return;
      }

      if (req.method === "GET" && pathname.startsWith("/api/saas/order/")) {
        const orderId = decodeURIComponent(pathname.replace("/api/saas/order/", ""));
        const orderToken = parsedUrl.searchParams.get("orderToken") || null;
        const order = await saas.syncLiveStatusWithPakasir(orderId, orderToken);
        res.writeHead(200);
        res.end(JSON.stringify({ order }));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/pakasir/pay-sandbox") {
        const body = await readJsonBody(req);
        const settled = await saas.settleOrder(body.orderId, {
          verifyWithPakasir: Boolean(saas.pakasirProject && saas.pakasirApiKey && body.txnId),
          orderToken: body.orderToken || null,
          triggerPakasirSandboxApi: true
        });
        res.writeHead(200);
        res.end(JSON.stringify({ settled: true, order: settled }));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/webhook/pakasir") {
        const body = await readJsonBody(req);
        const orderId = body.order_id || body.orderId;
        const settled = await saas.settleOrder(orderId, {
          verifyWithPakasir: true,
          orderToken: body.orderToken || null
        });
        res.writeHead(200);
        res.end(JSON.stringify({ settled: true, order: settled }));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/sandbox/complete") {
        if (!saas.sandboxMode) {
          res.writeHead(403);
          res.end(JSON.stringify({ error: "Endpoint simulasi nonaktif di mode produksi" }));
          return;
        }
        const body = await readJsonBody(req);
        const settled = await saas.settleOrder(body.orderId, {
          verifyWithPakasir: false,
          orderToken: body.orderToken || null
        });
        res.writeHead(200);
        res.end(JSON.stringify({ settled: true, order: settled }));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/license/verify") {
        const body = await readJsonBody(req);
        const result = saas.verifyLicenseToken(body.licenseToken, body.deviceId);
        res.writeHead(result.valid ? 200 : result.code === "DEVICE_MISMATCH" ? 403 : 401);
        res.end(JSON.stringify(result));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/simulcast/configure") {
        const body = await readJsonBody(req);
        const relay = saas.configureSimulcastRelay(body.licenseToken, body.deviceId, body.targets);
        res.writeHead(200);
        res.end(JSON.stringify({ relay }));
        return;
      }

      if (req.method === "POST" && pathname === "/api/saas/failover/arm") {
        const body = await readJsonBody(req);
        const shield = saas.armCloudFailoverShield(body.licenseToken, body.deviceId, body.fallbackHeadline);
        res.writeHead(200);
        res.end(JSON.stringify({ shield }));
        return;
      }

      if (req.method === "POST" && pathname.startsWith("/webhook/donation/")) {
        const roomToken = pathname.split("/webhook/donation/")[1] || "default";
        if (webhookSecret) {
          const signature = req.headers["x-stream-token"] || "";
          if (signature !== webhookSecret) {
            res.writeHead(401);
            res.end(JSON.stringify({ error: "Invalid webhook token" }));
            return;
          }
        }
        const body = await readJsonBody(req);
        const donor = sanitizeMessage(body.donator_name || body.supporter || body.name || "Supporter");
        const amount = Number(body.amount_raw || body.amount || 10000);
        const message = sanitizeMessage(body.message || "Terima kasih atas dukungannya!");

        broadcastToRoom(roomToken, {
          event: "donation",
          data: {
            nickname: donor,
            amount,
            message,
            subathonAddSeconds: Math.max(30, Math.floor(amount / 10000) * 60)
          }
        });
        res.writeHead(200);
        res.end(JSON.stringify({ delivered: true }));
        return;
      }

      res.writeHead(404);
      res.end(JSON.stringify({ error: "Not found" }));
    } catch (err) {
      const code = err.statusCode || (err.message === "Malformed JSON payload" ? 400 : 500);
      res.writeHead(code);
      res.end(JSON.stringify({ error: err.message }));
    }
  }

  return { handler, saas, rooms, broadcastToRoom };
}

function createDiStreamServer(options = {}) {
  const { WebSocketServer } = require("ws");
  const { TikTokLiveConnection, WebcastPushConnection } = require("tiktok-live-connector");
  const TikTokConnector = TikTokLiveConnection || WebcastPushConnection;
  const { handler, saas, rooms } = createDiStreamHttpHandler(options);
  const server = http.createServer(handler);
  const wss = new WebSocketServer({ server });

  wss.on("connection", (ws, req) => {
    const params = new URL(req.url, `http://${req.headers.host}`).searchParams;
    const uniqueId = (params.get("uniqueId") || "").replace(/^@/, "").trim();
    const roomToken = (params.get("room") || uniqueId || "default").trim();
    const filterBadWords = params.get("safeChat") !== "false";

    if (!rooms.has(roomToken)) rooms.set(roomToken, new Set());
    rooms.get(roomToken).add(ws);

    const sendSafe = (event, data) => {
      if (ws.readyState === ws.OPEN) ws.send(JSON.stringify({ event, data }));
    };

    let tiktokLive = null;
    const supporterTotals = new Map();

    if (uniqueId && options.connectTikTok !== false) {
      tiktokLive = new TikTokConnector(uniqueId);
      tiktokLive
        .connect()
        .then((state) => {
          sendSafe("system", {
            nickname: "DiStream",
            comment: `Terhubung ke room live @${uniqueId} (ID ${state.roomId})`
          });
        })
        .catch((err) => {
          sendSafe("system", {
            nickname: "DiStream",
            comment: `Menunggu @${uniqueId} mulai live (${err.message})`
          });
        });

      tiktokLive.on("chat", (data) => {
        const cleanComment = filterBadWords ? sanitizeMessage(data.comment) : data.comment;
        sendSafe("chat", { nickname: data.nickname || data.uniqueId, comment: cleanComment });
      });

      tiktokLive.on("gift", (data) => {
        if (data.giftType === 1 && !data.repeatEnd) return;
        const sender = data.nickname || data.uniqueId;
        const diamonds = Number(data.diamondCount || 1) * Number(data.repeatCount || 1);
        const updatedTotal = (supporterTotals.get(sender) || 0) + diamonds;
        supporterTotals.set(sender, updatedTotal);

        let topName = sender;
        let topScore = updatedTotal;
        for (const [k, v] of supporterTotals.entries()) {
          if (v > topScore) {
            topName = k;
            topScore = v;
          }
        }
        sendSafe("gift", {
          nickname: sender,
          giftName: data.giftName || "Gift",
          repeatCount: data.repeatCount || 1,
          diamondCount: diamonds,
          topSupporter: topName,
          topDiamonds: topScore
        });
      });

      tiktokLive.on("like", (data) => {
        sendSafe("like", {
          nickname: data.nickname || data.uniqueId,
          likeCount: data.likeCount || 1,
          totalLikeCount: data.totalLikeCount || 0
        });
      });

      tiktokLive.on("follow", (data) => {
        sendSafe("follow", { nickname: data.nickname || data.uniqueId });
      });
    }

    ws.on("close", () => {
      const set = rooms.get(roomToken);
      if (set) {
        set.delete(ws);
        if (set.size === 0) rooms.delete(roomToken);
      }
      if (tiktokLive) tiktokLive.disconnect();
    });
  });

  return { server, wss, saas, sanitizeMessage };
}

if (require.main === module) {
  const port = Number(process.env.PORT || 21213);
  const { server } = createDiStreamServer({ enableExternalGatewayCalls: true });
  server.listen(port, "0.0.0.0", () => {
    console.log(`[DiStream Backend v4.0] Listening on http://0.0.0.0:${port}`);
  });
}

module.exports = { createDiStreamServer, createDiStreamHttpHandler, sanitizeMessage };
