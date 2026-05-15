# MobileSlicer Website

Cloudflare Pages site for `mobileslicer.com`.

## Local Preview

From this folder:

```sh
python3 -m http.server 8788
```

Open `http://localhost:8788`.

## Cloudflare Pages

Use `Website` as the project root. No build command is required, and the output directory is `.`.

Most of the site is static. `functions/v1/thingiverse/oauth` contains the
release OAuth bridge for Thingiverse sign-in. It keeps the Thingiverse Client
Secret out of the Android APK and redirects only a one-time handoff code through
the Android custom-scheme callback.

Deploy with:

```sh
wrangler pages deploy . --project-name mobileslicer --branch main
```

## Thingiverse OAuth Bridge

Thingiverse developer app callback URL:

```text
https://mobileslicer.com/v1/thingiverse/oauth/callback
```

Public OAuth config lives in `wrangler.toml`:

```toml
THINGIVERSE_CLIENT_ID = "..."
THINGIVERSE_ALLOWED_APP_REDIRECTS = "mobileslicer://thingiverse-auth"
```

Required Cloudflare Pages secrets:

```sh
wrangler pages secret put THINGIVERSE_CLIENT_SECRET --project-name mobileslicer
wrangler pages secret put THINGIVERSE_OAUTH_STATE_SECRET --project-name mobileslicer
```

Required KV binding:

```sh
wrangler kv namespace create THINGIVERSE_OAUTH_RATE_LIMIT_KV
```

Bind the created namespace to the Pages project as
`THINGIVERSE_OAUTH_RATE_LIMIT_KV`. The OAuth route fails closed if this binding
is missing.

Backend tests:

```sh
node --test test/thingiverse-oauth.test.mjs
```

## Domain Polish

`_redirects` includes:

* `www.mobileslicer.com` to `mobileslicer.com`
* short `/download`, `/google-play`, and `/agpl` routes
* redirects from retired `/features`, `/roadmap`, and `/releases` pages to current pages
