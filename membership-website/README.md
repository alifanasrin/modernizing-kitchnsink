# demo-fe

Next.js 15 (App Router, TypeScript) frontend with a welcome page and a paginated members list (login and registration are hosted by the auth service). It talks to a backend running on `localhost:8080`.

## Prerequisites

- Node.js 18.18+ (20+ recommended) and npm
- Backend running on `http://localhost:8080` (see its `openapi.yaml`). Sign-in is **OAuth 2.1 authorization code + PKCE (S256)** for a registered public client:
  `GET /oauth2/authorize`, `POST /auth/login`, `POST /oauth2/token`, `POST /auth/refresh`, `POST /auth/logout`, then `GET /members` with `Authorization: Bearer <accessToken>`.
- The backend must have a client registered with id `5aeaf2312d8a55033ecdefcn` (or your `OAUTH_CLIENT_ID`) and the exact redirect URI `http://localhost:3000/callback`, and its login page is hosted by the auth service itself (`OAUTH_LOGIN_URL`, default `http://localhost:8080/login`); this app has no login or register page.

## Getting started

```bash
npm install
npm run dev      # http://localhost:3000
npm test         # run the test suite
npm run build    # production build
npm start        # serve the production build
```

## Pages

| Route      | Description                                                        |
|------------|--------------------------------------------------------------------|
| `/`        | Registration form (first name, last name, email, phone, password)                             |
| `/`        | Welcome page: hero picture, **Login** (starts the authorize call) and **Register** (link to the auth service). Signed-in users go to `/home` |
| `/home`    | Landing page after sign-in: "Hello, <first name>!" over a travel background (requires login). First name comes from the access token's name claim, else `GET /members/{email}` for the token's `sub`, else "traveller" |
| `/members` | Paginated members list (requires login, else starts sign-in) |

## Sign-in flow (PKCE)

```
Login button -> /api/oauth/start            makes verifier + S256 challenge (verifier kept in an HttpOnly cookie),
                                             server calls /oauth2/authorize, takes request_id from the 302 Location,
                                             redirects the browser to that Location (the auth service's own /login page)
auth service /login (email + password)  ->  302 to http://localhost:3000/callback?code=...
browser -> /callback?code=...      code + verifier (JSON body) -> /oauth2/token -> tokens
                                             access-token + refresh-token saved as HttpOnly cookies, redirect /home or /members
```

- **Nothing logs in without an authorize call.** `/api/oauth/start` sets HttpOnly `pkce-verifier` and `oauth-request` cookies; `/callback` needs the verifier to get tokens and clears all flow cookies when finished.
- The browser never calls the backend directly; only route handlers and server components do. `redirectTo` is only honoured if it points at our callback.
- Access token lives 15 min. When `/members` gets a missing/401 token it redirects once to `/api/auth/refresh` (single-use refresh token, rotated each time), else starts a new sign-in.
- `POST /api/auth/logout` (the Log out button on `/home` and `/members`) calls the backend `POST /auth/logout` with the Bearer token, which revokes the session and its refresh token. If the access cookie has already expired it first trades the refresh token for a new access token so the session is still revoked. Cookies are cleared either way, then the user goes to the welcome page `/`.
- `state` (optional) is sent to `/oauth2/authorize` **only when the journey started from the members list** (`/members`, path only, no pageNo/pageSize). The welcome page and every other page are never sent as `state`. `/callback` aborts unless `state` comes back exactly as sent, then lands the user on `/members` if state was sent, otherwise on `/home`.
- Every sign-in begins at `/api/oauth/start`. Flow errors (backend down, token exchange failed) land on `/?error=...` as a clean notice.

Backend error bodies are never shown raw; only a clean message is passed to the UI.

## Cookies

All cookies are written through `setCookie` in `lib/oauth.ts`: **HttpOnly**, **SameSite=Lax**, host-only (no `Domain`), short `Max-Age`, **Secure** in production, and responses that set them are `Cache-Control: no-store`.

| Cookie | Lifetime | Path | Purpose |
|--------|----------|------|---------|
| `access-token` | token `expiresIn` (15 min) | `/` | Bearer token for `/members` |
| `refresh-token` | 7 days | `/api/auth` | Only sent to refresh/logout; single use, rotated |
| `pkce-verifier`, `oauth-request`, `oauth-state` | 10 min | `/` | One in-progress authorize call; cleared when `/callback` finishes |

In production the names get browser-enforced prefixes: `__Host-` (Secure, Path=/, no Domain) on all but the refresh token, which is `__Secure-` because its path is narrower. Lax rather than Strict so links from other sites into the app still carry the session.

## Configuration (optional env vars)

| Variable               | Default                                   | Purpose                                      |
|------------------------|-------------------------------------------|----------------------------------------------|
| `AUTH_BASE_URL`        | `http://localhost:8080`                   | Base for the auth URLs below                 |
| `OAUTH_CLIENT_ID`      | `5aeaf2312d8a55033ecdefcn`                                 | Registered public client id                  |
| `OAUTH_REDIRECT_URI`   | `http://localhost:3000/callback`| Registered redirect URI (exact match)        |
| `OAUTH_AUTHORIZE_URL`  | `$AUTH_BASE_URL/oauth2/authorize`         | Authorization endpoint                       |
| `OAUTH_TOKEN_URL`      | `$AUTH_BASE_URL/oauth2/token`             | Token endpoint                               |
| `REFRESH_API_URL` / `LOGOUT_API_URL` | `$AUTH_BASE_URL/auth/...`    | Session endpoints                            |
| `REGISTER_PAGE_URL`    | `$AUTH_BASE_URL/register`                 | Auth service register page (the Register link) |
| `MEMBER_BASE_URL`      | `http://localhost:8081`                   | Member service host (`lib/config.ts`)       |
| `MEMBERS_API_URL`      | `${MEMBER_BASE_URL}/members`              | Members endpoint                             |
| `MEMBERS_FIRST_PAGE`   | `1`                                       | Set to `0` if the backend uses 0-based pages |

## Project structure

```
app/
  page.tsx                        welcome page (Login / Register)
  members/                        members list (page, loading, error, PageSizeSelect)
  api/oauth/start, callback/      PKCE start (authorize); /callback does code->token exchange
  api/auth/refresh, logout        token rotation and session revoke
  not-found.tsx, error.tsx, loading.tsx
lib/
  pkce.ts, oauth.ts, session.ts   PKCE helpers, auth config/cookies, re-auth redirect
  members.ts                      server-side members fetch (Bearer auth)
tests/                            Vitest + React Testing Library
```