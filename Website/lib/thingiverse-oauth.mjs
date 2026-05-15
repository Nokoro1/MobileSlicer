const DEFAULT_ALLOWED_APP_REDIRECTS = "mobileslicer://thingiverse-auth";
const OAUTH_AUTHORIZE_URL = "https://www.thingiverse.com/login/oauth/authorize";
const OAUTH_ACCESS_TOKEN_URL = "https://www.thingiverse.com/login/oauth/access_token";
const THINGIVERSE_ME_URL = "https://api.thingiverse.com/users/me";
const COOKIE_NAME = "__Host-ms_tv_oauth";
const STATE_TTL_SECONDS = 10 * 60;
const HANDOFF_TTL_SECONDS = 5 * 60;
const MAX_STATE_LENGTH = 256;
const HANDOFF_CODE_BYTE_LENGTH = 32;
const RATE_LIMIT_WINDOW_SECONDS = 10 * 60;
const RATE_LIMIT_MAX_REQUESTS = 30;

export class OAuthConfigError extends Error {}
export class OAuthRequestError extends Error {}
export class OAuthRateLimitError extends Error {}

export function getOAuthConfig(env) {
  const clientId = stringValue(env.THINGIVERSE_CLIENT_ID);
  const clientSecret = stringValue(env.THINGIVERSE_CLIENT_SECRET);
  const signingSecret = stringValue(env.THINGIVERSE_OAUTH_STATE_SECRET || env.THINGIVERSE_CLIENT_SECRET);
  const allowedAppRedirects = parseCsv(env.THINGIVERSE_ALLOWED_APP_REDIRECTS || DEFAULT_ALLOWED_APP_REDIRECTS);

  if (!clientId) throw new OAuthConfigError("THINGIVERSE_CLIENT_ID is not configured.");
  if (!clientSecret) throw new OAuthConfigError("THINGIVERSE_CLIENT_SECRET is not configured.");
  if (!signingSecret || signingSecret.length < 16) {
    throw new OAuthConfigError("THINGIVERSE_OAUTH_STATE_SECRET must be at least 16 characters.");
  }
  if (allowedAppRedirects.length === 0) {
    throw new OAuthConfigError("THINGIVERSE_ALLOWED_APP_REDIRECTS must include at least one callback URI.");
  }

  return {
    clientId,
    clientSecret,
    signingSecret,
    allowedAppRedirects,
    rateLimitKv: env.THINGIVERSE_OAUTH_RATE_LIMIT_KV,
  };
}

export async function handleOAuthStart(request, env) {
  const config = getOAuthConfig(env);
  await enforceRateLimit(request, config, "start");
  const requestUrl = new URL(request.url);
  const clientId = requestUrl.searchParams.get("client_id") || "";
  const appRedirectUri = requestUrl.searchParams.get("redirect_uri") || "";
  const appState = requestUrl.searchParams.get("state") || "";

  if (clientId !== config.clientId) {
    throw new OAuthRequestError("Unknown Thingiverse client_id.");
  }
  assertAllowedAppRedirect(appRedirectUri, config.allowedAppRedirects);
  assertValidAppState(appState);

  const callbackUrl = oauthCallbackUrl(requestUrl);
  const signedState = await signState(
    {
      appRedirectUri,
      appState,
      iat: epochSeconds(),
      exp: epochSeconds() + STATE_TTL_SECONDS,
      nonce: cryptoRandomBase64Url(18),
    },
    config.signingSecret
  );

  const authorizeUrl = new URL(OAUTH_AUTHORIZE_URL);
  authorizeUrl.searchParams.set("client_id", config.clientId);
  authorizeUrl.searchParams.set("redirect_uri", callbackUrl);
  authorizeUrl.searchParams.set("response_type", "code");
  authorizeUrl.searchParams.set("state", signedState);

  return redirectResponse(authorizeUrl.toString(), 302, {
    "Set-Cookie": oauthCookie(signedState),
    "Cache-Control": "no-store",
  });
}

export async function handleOAuthCallback(request, env, fetchImpl = fetch) {
  const config = getOAuthConfig(env);
  await enforceRateLimit(request, config, "callback");
  const requestUrl = new URL(request.url);
  const signedState = requestUrl.searchParams.get("state") || readCookie(request.headers.get("Cookie"), COOKIE_NAME);

  const state = await verifyState(signedState, config.signingSecret);
  assertAllowedAppRedirect(state.appRedirectUri, config.allowedAppRedirects);

  const providerError = requestUrl.searchParams.get("error");
  if (providerError) {
    return appCallbackRedirect(state, {
      error: providerError,
      error_description: requestUrl.searchParams.get("error_description") || providerError,
    });
  }

  const code = requestUrl.searchParams.get("code") || "";
  if (!code) {
    return appCallbackRedirect(state, {
      error: "missing_code",
      error_description: "Thingiverse did not return an authorization code.",
    });
  }

  const callbackUrl = oauthCallbackUrl(requestUrl);
  let token;
  let user;
  try {
    token = await exchangeCodeForToken(fetchImpl, config, code, callbackUrl);
    user = await fetchThingiverseUser(fetchImpl, token.accessToken);
  } catch (error) {
    return appCallbackRedirect(state, {
      error: "thingiverse_exchange_failed",
      error_description: "Thingiverse sign-in could not be completed. Try again.",
    });
  }

  const handoffCode = await createHandoff(config, {
    access_token: token.accessToken,
    token_type: token.tokenType || "bearer",
    expires_in: token.expiresIn || "",
    display_name: user.displayName || "",
    user_id: user.id || "",
  });

  return appCallbackRedirect(state, {
    handoff_code: handoffCode,
  });
}

export async function handleOAuthRedeem(request, env) {
  const config = getOAuthConfig(env);
  await enforceRateLimit(request, config, "redeem");
  const requestUrl = new URL(request.url);
  const code = requestUrl.searchParams.get("code") || "";
  assertValidHandoffCode(code);

  const key = handoffKey(code);
  const payload = await config.rateLimitKv.get(key, { type: "json" });
  await config.rateLimitKv.delete(key);
  if (!payload || !payload.access_token) {
    throw new OAuthRequestError("Thingiverse sign-in handoff expired. Sign in again.");
  }

  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

export async function exchangeCodeForToken(fetchImpl, config, code, redirectUri) {
  const body = new URLSearchParams();
  body.set("client_id", config.clientId);
  body.set("client_secret", config.clientSecret);
  body.set("code", code);
  body.set("redirect_uri", redirectUri);

  const response = await fetchImpl(OAUTH_ACCESS_TOKEN_URL, {
    method: "POST",
    headers: {
      "Accept": "application/x-www-form-urlencoded, application/json",
      "Content-Type": "application/x-www-form-urlencoded",
      "User-Agent": "MobileSlicer Thingiverse OAuth",
    },
    body,
  });
  const text = await response.text();
  if (!response.ok) {
    throw new OAuthRequestError(`Thingiverse token exchange failed with HTTP ${response.status}.`);
  }

  const tokenPayload = parseTokenResponse(text);
  if (!tokenPayload.accessToken) {
    throw new OAuthRequestError("Thingiverse token exchange did not return an access token.");
  }
  return tokenPayload;
}

export async function fetchThingiverseUser(fetchImpl, accessToken) {
  const response = await fetchImpl(THINGIVERSE_ME_URL, {
    method: "GET",
    headers: {
      "Accept": "application/json",
      "Authorization": `Bearer ${accessToken}`,
      "User-Agent": "MobileSlicer Thingiverse OAuth",
    },
  });
  const text = await response.text();
  if (!response.ok) {
    throw new OAuthRequestError(`Thingiverse user validation failed with HTTP ${response.status}.`);
  }
  try {
    const user = JSON.parse(text);
    return {
      id: user.id ? String(user.id) : "",
      displayName: user.name || user.username || "",
    };
  } catch {
    return { id: "", displayName: "" };
  }
}

export function parseTokenResponse(text) {
  const trimmed = text.trim();
  if (!trimmed) return { accessToken: "", tokenType: "", expiresIn: "" };
  if (trimmed.startsWith("{")) {
    const json = JSON.parse(trimmed);
    return {
      accessToken: json.access_token || "",
      tokenType: json.token_type || "",
      expiresIn: json.expires_in ? String(json.expires_in) : "",
    };
  }
  const params = new URLSearchParams(trimmed);
  return {
    accessToken: params.get("access_token") || "",
    tokenType: params.get("token_type") || "",
    expiresIn: params.get("expires_in") || "",
  };
}

export function oauthCallbackUrl(currentUrl) {
  return `${currentUrl.origin}/v1/thingiverse/oauth/callback`;
}

export async function enforceRateLimit(request, config, routeName) {
  if (!config.rateLimitKv) {
    throw new OAuthConfigError("THINGIVERSE_OAUTH_RATE_LIMIT_KV is not configured.");
  }
  const clientIdentity = request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
    "unknown";
  const clientHash = await hmacSha256(clientIdentity, config.signingSecret);
  const key = `thingiverse-oauth:${routeName}:${clientHash}`;
  const now = epochSeconds();
  const existing = await config.rateLimitKv.get(key, { type: "json" });
  const bucket = existing && existing.resetAt > now
    ? existing
    : { count: 0, resetAt: now + RATE_LIMIT_WINDOW_SECONDS };
  if (bucket.count >= RATE_LIMIT_MAX_REQUESTS) {
    throw new OAuthRateLimitError("Too many Thingiverse sign-in requests. Try again later.");
  }
  await config.rateLimitKv.put(
    key,
    JSON.stringify({ count: bucket.count + 1, resetAt: bucket.resetAt }),
    { expirationTtl: Math.max(60, bucket.resetAt - now) }
  );
}

export async function createHandoff(config, payload) {
  if (!config.rateLimitKv) {
    throw new OAuthConfigError("THINGIVERSE_OAUTH_RATE_LIMIT_KV is not configured.");
  }
  const code = cryptoRandomBase64Url(HANDOFF_CODE_BYTE_LENGTH);
  await config.rateLimitKv.put(
    handoffKey(code),
    JSON.stringify({
      ...payload,
      created_at: epochSeconds(),
    }),
    { expirationTtl: HANDOFF_TTL_SECONDS }
  );
  return code;
}

export function assertValidHandoffCode(code) {
  if (!code || code.length < 32 || code.length > 128 || !/^[A-Za-z0-9_-]+$/.test(code)) {
    throw new OAuthRequestError("Thingiverse sign-in handoff code is invalid.");
  }
}

function handoffKey(code) {
  return `thingiverse-oauth:handoff:${code}`;
}

export function assertAllowedAppRedirect(appRedirectUri, allowedAppRedirects) {
  if (!appRedirectUri) throw new OAuthRequestError("redirect_uri is required.");
  if (!allowedAppRedirects.includes(appRedirectUri)) {
    throw new OAuthRequestError("redirect_uri is not allowed.");
  }
}

export function assertValidAppState(appState) {
  if (!appState || appState.length > MAX_STATE_LENGTH || /[\s#]/.test(appState)) {
    throw new OAuthRequestError("state is invalid.");
  }
}

export async function signState(payload, secret) {
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const signature = await hmacSha256(encodedPayload, secret);
  return `${encodedPayload}.${signature}`;
}

export async function verifyState(signedState, secret) {
  if (!signedState || !signedState.includes(".")) {
    throw new OAuthRequestError("OAuth state is missing.");
  }
  const [encodedPayload, signature] = signedState.split(".", 2);
  const expected = await hmacSha256(encodedPayload, secret);
  if (!constantTimeEqual(signature, expected)) {
    throw new OAuthRequestError("OAuth state signature is invalid.");
  }
  const payload = JSON.parse(base64UrlDecode(encodedPayload));
  if (!payload.exp || payload.exp < epochSeconds()) {
    throw new OAuthRequestError("OAuth state expired.");
  }
  if (!payload.appRedirectUri || !payload.appState) {
    throw new OAuthRequestError("OAuth state payload is invalid.");
  }
  return payload;
}

export function errorResponse(error) {
  const status = error instanceof OAuthConfigError ? 500 : error instanceof OAuthRateLimitError ? 429 : 400;
  return new Response(JSON.stringify({ error: error.message }), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function appCallbackRedirect(state, params) {
  const callback = new URL(state.appRedirectUri);
  callback.searchParams.set("state", state.appState);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && String(value).length > 0) {
      callback.searchParams.set(key, String(value));
    }
  }
  return redirectResponse(callback.toString(), 302, {
    "Set-Cookie": expireOauthCookie(),
    "Cache-Control": "no-store",
  });
}

function redirectResponse(url, status, headers = {}) {
  return new Response(null, {
    status,
    headers: {
      Location: url,
      ...headers,
    },
  });
}

function oauthCookie(value) {
  return `${COOKIE_NAME}=${value}; Path=/; Max-Age=${STATE_TTL_SECONDS}; HttpOnly; Secure; SameSite=Lax`;
}

function expireOauthCookie() {
  return `${COOKIE_NAME}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax`;
}

function readCookie(header, name) {
  if (!header) return "";
  const prefix = `${name}=`;
  return header
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

function parseCsv(value) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function stringValue(value) {
  return typeof value === "string" ? value.trim() : "";
}

function epochSeconds() {
  return Math.floor(Date.now() / 1000);
}

function cryptoRandomBase64Url(byteLength) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return base64UrlEncode(String.fromCharCode(...bytes));
}

async function hmacSha256(message, secret) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return base64UrlEncode(String.fromCharCode(...new Uint8Array(signature)));
}

function base64UrlEncode(value) {
  return btoa(value)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function base64UrlDecode(value) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return atob(padded);
}

function constantTimeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const maxLength = Math.max(a.length, b.length);
  let diff = a.length ^ b.length;
  for (let index = 0; index < maxLength; index += 1) {
    diff |= (a.charCodeAt(index) || 0) ^ (b.charCodeAt(index) || 0);
  }
  return diff === 0;
}
