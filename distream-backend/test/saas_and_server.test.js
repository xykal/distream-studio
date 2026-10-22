const { describe, it, before, after } = require("node:test");
const assert = require("node:assert/strict");
const WebSocket = require("ws");
const { createDiStreamServer, sanitizeMessage } = require("../server");

describe("DiStream Studio v3.0 — Backend SaaS, Security & Event Bus QA Suite", () => {
  let server;
  let baseUrl;
  let wsUrl;

  before(async () => {
    const app = createDiStreamServer({
      signingSecret: "qa-test-hmac-secret-2026",
      webhookSecret: "qa-webhook-token",
      sandboxMode: true,
      enableExternalGatewayCalls: false,
      connectTikTok: false
    });
    server = app.server;
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    const port = server.address().port;
    baseUrl = `http://127.0.0.1:${port}`;
    wsUrl = `ws://127.0.0.1:${port}`;
  });

  after(async () => {
    await new Promise((resolve) => server.close(resolve));
  });

  it("1. Health check endpoint returns healthy status without exposing secrets", async () => {
    const res = await fetch(`${baseUrl}/api/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.status, "ok");
    assert.equal("pakasirApiKey" in body, false);
  });

  it("2. SaaS catalog exposes FREE, DAILY (Rp 5.000), WEEKLY (Rp 19.000), and MONTHLY (Rp 59.000)", async () => {
    const res = await fetch(`${baseUrl}/api/saas/plans`);
    assert.equal(res.status, 200);
    const { plans } = await res.json();
    assert.equal(plans.length, 4);
    assert.deepEqual(
      plans.map((p) => [p.id, p.priceIdr]),
      [
        ["FREE", 0],
        ["DAILY", 5000],
        ["WEEKLY", 19000],
        ["MONTHLY", 59000]
      ]
    );
  });

  it("3. Daily Pass QRIS checkout applies XYKAL50 promo discount and settles idempotently into valid HMAC token", async () => {
    const checkoutRes = await fetch(`${baseUrl}/api/saas/checkout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        planId: "DAILY",
        deviceId: "pixel-9-pro-test",
        promoCode: "XYKAL50"
      })
    });
    assert.equal(checkoutRes.status, 201);
    const { order } = await checkoutRes.json();
    assert.equal(order.baseAmount, 5000);
    assert.equal(order.amount, 2500);
    assert.equal(order.status, "pending");
    assert.match(order.paymentUrl, /app\.pakasir\.com\/pay\//);

    // Settle via sandbox completion twice to verify strict idempotency
    const settle1 = await (
      await fetch(`${baseUrl}/api/saas/sandbox/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orderId: order.orderId })
      })
    ).json();
    const settle2 = await (
      await fetch(`${baseUrl}/api/saas/sandbox/complete`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orderId: order.orderId })
      })
    ).json();

    assert.equal(settle1.order.status, "completed");
    assert.equal(settle1.order.licenseToken, settle2.order.licenseToken);
    assert.equal(settle1.order.expiresAt, settle2.order.expiresAt);

    // Verify license cryptographically on matching device
    const verifyOk = await fetch(`${baseUrl}/api/saas/license/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        licenseToken: settle1.order.licenseToken,
        deviceId: "pixel-9-pro-test"
      })
    });
    assert.equal(verifyOk.status, 200);
    const verifyBody = await verifyOk.json();
    assert.equal(verifyBody.valid, true);
    assert.equal(verifyBody.tier, "DAILY");
    assert.equal(verifyBody.plan.entitlements.removeWatermark, true);
    assert.equal(verifyBody.plan.entitlements.multiStreamSimulcast, false);

    // Reject stolen token on another device (Anti-Sharing Device Binding)
    const stolenCheck = await fetch(`${baseUrl}/api/saas/license/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        licenseToken: settle1.order.licenseToken,
        deviceId: "other-emulator-device"
      })
    });
    assert.equal(stolenCheck.status, 403);

    // Reject forged/tampered token signature
    const tampered = settle1.order.licenseToken.slice(0, -3) + "XYZ";
    const tamperCheck = await fetch(`${baseUrl}/api/saas/license/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ licenseToken: tampered, deviceId: "pixel-9-pro-test" })
    });
    assert.equal(tamperCheck.status, 401);
  });

  it("4. TRIALVIP grants 24-hour VIP access once per device and blocks duplicate abuse", async () => {
    const first = await fetch(`${baseUrl}/api/saas/checkout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ planId: "MONTHLY", deviceId: "rog-phone-8", promoCode: "TRIALVIP" })
    });
    assert.equal(first.status, 201);
    const firstData = await first.json();
    assert.equal(firstData.order.status, "completed");
    assert.ok(firstData.order.licenseToken.startsWith("DST1."));

    const duplicate = await fetch(`${baseUrl}/api/saas/checkout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ planId: "MONTHLY", deviceId: "rog-phone-8", promoCode: "TRIALVIP" })
    });
    assert.equal(duplicate.status, 409);

    // VIP token unlocks BOTH Cloud Multi-Stream Simulcast AND Cloud Anti-Disconnect Shield
    const simRes = await fetch(`${baseUrl}/api/saas/simulcast/configure`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        licenseToken: firstData.order.licenseToken,
        deviceId: "rog-phone-8",
        targets: [
          { platform: "TikTok LIVE", url: "rtmps://push.rtmp.tiktok.com/live/k1" },
          { platform: "YouTube Live", url: "rtmps://a.rtmps.youtube.com/live2/k2" }
        ]
      })
    });
    assert.equal(simRes.status, 200);
    const simJson = await simRes.json();
    assert.equal(simJson.relay.bandwidthSavedPercent, 50);

    const shieldRes = await fetch(`${baseUrl}/api/saas/failover/arm`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        licenseToken: firstData.order.licenseToken,
        deviceId: "rog-phone-8",
        fallbackHeadline: "Tunggu sebentar jaringan sedang reconn"
      })
    });
    assert.equal(shieldRes.status, 200);
  });

  it("5. Authenticated Saweria Webhook sanitizes banned words and broadcasts live over WebSocket", async () => {
    const received = new Promise((resolve) => {
      const ws = new WebSocket(`${wsUrl}/?room=mabar99`);
      ws.on("open", async () => {
        // Unauthorized request must fail with 401
        const unauth = await fetch(`${baseUrl}/webhook/donation/mabar99`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ supporter: "Anon", amount: 50000 })
        });
        assert.equal(unauth.status, 401);

        // Authorized request with banned words gets sanitized and delivered
        const ok = await fetch(`${baseUrl}/webhook/donation/mabar99`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Stream-Token": "qa-webhook-token"
          },
          body: JSON.stringify({
            donator_name: "Sultan",
            amount: 50000,
            message: "Gas mabar tanpa judol dan slot gacor!"
          })
        });
        assert.equal(ok.status, 200);
      });
      ws.on("message", (raw) => {
        const msg = JSON.parse(raw.toString());
        ws.close();
        resolve(msg);
      });
    });

    const eventPayload = await received;
    assert.equal(eventPayload.event, "donation");
    assert.equal(eventPayload.data.message, "Gas mabar tanpa *** dan ***!");
    assert.equal(eventPayload.data.subathonAddSeconds, 300);
    assert.equal(sanitizeMessage("zeus88 anjing"), "*** ***");
  });
});
