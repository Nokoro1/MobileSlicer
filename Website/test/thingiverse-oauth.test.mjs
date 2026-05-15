import assert from "node:assert/strict";
import test from "node:test";
import {
  assertAllowedAppRedirect,
  assertValidAppState,
  handleOAuthCallback,
  handleOAuthRedeem,
  handleOAuthStart,
  parseTokenResponse,
  signState,
  verifyState,
} from "../lib/thingiverse-oauth.mjs";

class MemoryKv {
  values = new Map();

  async get(key, options) {
    const value = this.values.get(key);
    if (!value) return null;
    return options?.type === "json" ? JSON.parse(value) : value;
  }

  async put(key, value) {
    this.values.set(key, value);
  }

  async delete(key) {
    this.values.delete(key);
  }
}

const env = {
  THINGIVERSE_CLIENT_ID: "client_123",
  THINGIVERSE_CLIENT_SECRET: "secret_1234567890",
  THINGIVERSE_OAUTH_STATE_SECRET: "state_secret_1234567890",
  THINGIVERSE_ALLOWED_APP_REDIRECTS: "mobileslicer://thingiverse-auth",
  THINGIVERSE_OAUTH_RATE_LIMIT_KV: new MemoryKv(),
};

function testEnv() {
  return {
    ...env,
    THINGIVERSE_OAUTH_RATE_LIMIT_KV: new MemoryKv(),
  };
}

test("start redirects only for the configured client and app callback", async () => {
  const request = new Request(
    "https://mobileslicer.com/v1/thingiverse/oauth/start?" +
      new URLSearchParams({
        client_id: env.THINGIVERSE_CLIENT_ID,
        redirect_uri: "mobileslicer://thingiverse-auth",
        state: "android-state-1",
      })
  );

  const response = await handleOAuthStart(request, testEnv());
  assert.equal(response.status, 302);
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  assert.match(response.headers.get("Set-Cookie"), /__Host-ms_tv_oauth=/);

  const location = new URL(response.headers.get("Location"));
  assert.equal(location.origin + location.pathname, "https://www.thingiverse.com/login/oauth/authorize");
  assert.equal(location.searchParams.get("client_id"), env.THINGIVERSE_CLIENT_ID);
  assert.equal(location.searchParams.get("redirect_uri"), "https://mobileslicer.com/v1/thingiverse/oauth/callback");
  assert.equal(location.searchParams.get("response_type"), "code");
  assert.ok(location.searchParams.get("state"));
});

test("start rejects unapproved app callbacks", async () => {
  const request = new Request(
    "https://mobileslicer.com/v1/thingiverse/oauth/start?" +
      new URLSearchParams({
        client_id: env.THINGIVERSE_CLIENT_ID,
        redirect_uri: "https://attacker.example/callback",
        state: "android-state-1",
      })
  );

  await assert.rejects(() => handleOAuthStart(request, testEnv()), /redirect_uri is not allowed/);
});

test("start fails closed when the rate-limit binding is missing", async () => {
  const request = new Request(
    "https://mobileslicer.com/v1/thingiverse/oauth/start?" +
      new URLSearchParams({
        client_id: env.THINGIVERSE_CLIENT_ID,
        redirect_uri: "mobileslicer://thingiverse-auth",
        state: "android-state-1",
      })
  );

  await assert.rejects(
    () => handleOAuthStart(request, { ...env, THINGIVERSE_OAUTH_RATE_LIMIT_KV: undefined }),
    /THINGIVERSE_OAUTH_RATE_LIMIT_KV is not configured/
  );
});

test("app state validation rejects unsafe values", () => {
  assert.doesNotThrow(() => assertValidAppState("normal-state_123"));
  assert.throws(() => assertValidAppState("bad state"), /state is invalid/);
  assert.throws(() => assertValidAppState("bad#state"), /state is invalid/);
});

test("signed state survives verification and rejects tampering", async () => {
  const signed = await signState(
    {
      appRedirectUri: "mobileslicer://thingiverse-auth",
      appState: "android-state-1",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60,
      nonce: "nonce",
    },
    env.THINGIVERSE_OAUTH_STATE_SECRET
  );

  const payload = await verifyState(signed, env.THINGIVERSE_OAUTH_STATE_SECRET);
  assert.equal(payload.appState, "android-state-1");
  await assert.rejects(
    () => verifyState(`${signed.slice(0, -1)}x`, env.THINGIVERSE_OAUTH_STATE_SECRET),
    /OAuth state signature is invalid/
  );
});

test("token parser supports Thingiverse form and JSON responses", () => {
  assert.deepEqual(parseTokenResponse("access_token=abc123&token_type=bearer"), {
    accessToken: "abc123",
    tokenType: "bearer",
    expiresIn: "",
  });
  assert.deepEqual(parseTokenResponse('{"access_token":"def456","token_type":"bearer","expires_in":3600}'), {
    accessToken: "def456",
    tokenType: "bearer",
    expiresIn: "3600",
  });
});

test("callback exchanges code and redirects a one-time handoff back to the app state", async () => {
  const currentEnv = testEnv();
  const signed = await signState(
    {
      appRedirectUri: "mobileslicer://thingiverse-auth",
      appState: "android-state-1",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60,
      nonce: "nonce",
    },
    env.THINGIVERSE_OAUTH_STATE_SECRET
  );
  const request = new Request(
    "https://mobileslicer.com/v1/thingiverse/oauth/callback?" +
      new URLSearchParams({ code: "thingiverse-code", state: signed })
  );
  const calls = [];
  const mockFetch = async (url, options) => {
    calls.push({ url, options });
    if (String(url).includes("/access_token")) {
      assert.equal(options.method, "POST");
      assert.match(String(options.body), /client_secret=secret_1234567890/);
      return new Response("access_token=user_token&token_type=bearer", { status: 200 });
    }
    if (String(url).includes("/users/me")) {
      assert.equal(options.headers.Authorization, "Bearer user_token");
      return new Response(JSON.stringify({ id: 42, name: "Thing User" }), { status: 200 });
    }
    throw new Error(`Unexpected fetch ${url}`);
  };

  const response = await handleOAuthCallback(request, currentEnv, mockFetch);
  assert.equal(response.status, 302);
  assert.equal(calls.length, 2);

  const location = new URL(response.headers.get("Location"));
  assert.equal(location.protocol, "mobileslicer:");
  assert.equal(location.hostname, "thingiverse-auth");
  assert.equal(location.searchParams.get("state"), "android-state-1");
  assert.equal(location.searchParams.has("access_token"), false);
  const handoffCode = location.searchParams.get("handoff_code");
  assert.match(handoffCode, /^[A-Za-z0-9_-]{32,128}$/);

  const redeem = await handleOAuthRedeem(
    new Request(`https://mobileslicer.com/v1/thingiverse/oauth/redeem?code=${handoffCode}`),
    currentEnv
  );
  assert.equal(redeem.status, 200);
  const token = await redeem.json();
  assert.equal(token.access_token, "user_token");
  assert.equal(token.display_name, "Thing User");

  await assert.rejects(
    () => handleOAuthRedeem(new Request(`https://mobileslicer.com/v1/thingiverse/oauth/redeem?code=${handoffCode}`), currentEnv),
    /handoff expired/
  );
});

test("redirect allowlist is exact", () => {
  assert.doesNotThrow(() => assertAllowedAppRedirect("mobileslicer://thingiverse-auth", [
    "mobileslicer://thingiverse-auth",
  ]));
  assert.throws(() => assertAllowedAppRedirect("mobileslicer://thingiverse-auth.evil", [
    "mobileslicer://thingiverse-auth",
  ]));
});
