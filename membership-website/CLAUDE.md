# CLAUDE.md

Next.js 15 App Router + TypeScript frontend (welcome page, members list; login and register are hosted by the auth service, not here). Auth backend is a separate service on `localhost:8080`; members are read from the member service on `localhost:8081`. See `README.md` for endpoints and setup.

## Commands

- `npm run dev` — dev server on :3000
- `npm test` — Vitest (run after every change; all tests must pass)
- `npm run build` — must compile with zero type errors before finishing a task

## Conventions

- App Router only (`app/`), never Pages Router. Server Components by default; `'use client'` only on leaf components that need interactivity (forms, page-size select).
- **This app hosts no login or register page/form.** `/` is the welcome page: **Login** is a link to `/api/oauth/start` (calls `/oauth2/authorize`, then redirects to the login page the auth service names), **Register** links to the auth service's `/register` (`REGISTER_PAGE_URL`). Any redirect that used to go to `/login` goes to `/api/oauth/start`; flow errors go to `/?error=...`.
- **All request payloads are JSON, never FormData.** No server actions for forms. Any `fetch` from the browser goes to `app/api/*/route.ts` handlers, which proxy to the backend.
- The browser never calls `localhost:8080` directly; always go through a route handler or server component.
- **Never show raw backend error JSON in the UI.** Route handlers extract a clean message (strip `field: ` prefix); 5xx and unparseable bodies get a generic message.
- `/callback` exchanges the code for tokens and saves them as HttpOnly cookies. `lib/members.ts` reads it server-side and sends `Authorization: Bearer <token>` to `GET /members`.
- Missing cookie or 401/403 from members → re-authenticate (refresh, else `/api/oauth/start`). Out-of-range page or backend 404 → `notFound()`.
- Every async route segment needs `loading.tsx` and `error.tsx`.
- Use the `metadata` export for titles/SEO, not hardcoded `<title>`.
- Backend URLs and the members first-page number are env-configurable (see README); defaults point to localhost:8080 and 1-based pages.

## Testing

Tests are in `tests/` (Vitest, jsdom, React Testing Library). Mock `fetch` with `vi.stubGlobal`; mock `next/navigation`, `next/headers` and `server-only` where needed. Add tests alongside any new route handler, form or lib function.

## Gotchas

- Don't add token logging anywhere.
- Backend response/auth assumptions (cookie name `access-token`, Bearer scheme, 1-based paging) were assumed, not confirmed. Check with the user if something behaves unexpectedly.
