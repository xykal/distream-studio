/**
 * Durable Object: ruang relay event realtime per room (pengganti WebSocketServer Node).
 *
 * Dipakai backend DiStream Studio di Cloudflare Workers supaya alert donasi
 * (Saweria / Sociabuzz / BagiBagi) tetap sampai ke aplikasi saat backend tidak lagi
 * berjalan di VPS/Vercel.
 *
 * Alur:
 *   POST /webhook/donation/:room  ->  Worker  ->  DO.publish(payload)
 *   GET  /ws?room=:room (Upgrade) ->  Worker  ->  DO (WebSocket hibernatable)
 *
 * Catatan: event chat/gift TikTok belum lewat sini karena `tiktok-live-connector`
 * membutuhkan TCP/TLS mentah yang tidak tersedia di runtime Workers.
 */

export class RoomRelay {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sockets = new Set();
  }

  async fetch(request) {
    const url = new URL(request.url);

    if (url.pathname === "/publish" && request.method === "POST") {
      const payload = await request.text();
      let delivered = 0;
      for (const ws of this.sockets) {
        try {
          ws.send(payload);
          delivered += 1;
        } catch {
          this.sockets.delete(ws);
        }
      }
      return new Response(JSON.stringify({ delivered }), {
        headers: { "Content-Type": "application/json" }
      });
    }

    if (url.pathname === "/status") {
      return new Response(JSON.stringify({ clients: this.sockets.size }), {
        headers: { "Content-Type": "application/json" }
      });
    }

    // Upgrade WebSocket
    const upgrade = request.headers.get("Upgrade");
    if (upgrade && upgrade.toLowerCase() === "websocket") {
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);

      // Hibernation API: Worker tidak perlu tetap hidup untuk menahan koneksi
      this.state.acceptWebSocket(server);
      this.sockets.add(server);

      try {
        server.send(
          JSON.stringify({
            event: "system",
            data: { nickname: "DiStream Studio", comment: "Relay realtime tersambung" }
          })
        );
      } catch {
        /* diabaikan */
      }

      return new Response(null, { status: 101, webSocket: client });
    }

    return new Response("RoomRelay aktif", { status: 200 });
  }

  async webSocketMessage(ws, message) {
    // Ping sederhana dari klien
    if (typeof message === "string" && message.trim() === "ping") {
      try {
        ws.send(JSON.stringify({ event: "pong", data: { at: Date.now() } }));
      } catch {
        /* diabaikan */
      }
    }
  }

  async webSocketClose(ws) {
    this.sockets.delete(ws);
    try {
      ws.close(1000, "closed");
    } catch {
      /* diabaikan */
    }
  }

  async webSocketError(ws) {
    this.sockets.delete(ws);
  }
}

export default { RoomRelay };
