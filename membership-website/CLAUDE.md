# CLAUDE.md

Next.js 15 App Router + TypeScript frontend (registration, login, members list). Backend is a separate service on `localhost:8080`. See `README.md` for endpoints and setup.

## Commands

- `npm run dev` — dev server on :3000
- `npm test` — Vitest (run after every change; all tests must pass)
- `npm run build` — must compile with zero type errors before finishing a task

## Conventions

- App Router only (`app/`), never Pages Router. Server Components by default; `'use client'` only on leaf components that need interactivity (forms, page-size select).
- **All request payloads are JSON, never FormData.** No server actions for forms. Forms `fetch` JSON to `app/api/*/route.ts` handlers, which validate and proxy to the backend.
- The browser never calls `localhost:8080` directly; always go through a route handler or server component.
- Validation lives in `lib/validation.ts` and is shared by client forms and route handlers. Rules: name ≤ 25 chars; email valid format; phone 10–12 digits only; password required. Client errors show live while typing and persist until fixed; submit is disabled while invalid.
- **Never show raw backend error JSON in the UI.** Route handlers extract a clean message (strip `field: ` prefix); 5xx and unparseable bodies get a generic message.
- Login saves the backend's `access-token` Set-Cookie as an HttpOnly cookie via the login route handler. `lib/members.ts` reads it server-side and sends `Authorization: Bearer <token>` to `GET /members`.
- Missing cookie or 401/403 from members → redirect to `/login`. Out-of-range page or backend 404 → `notFound()`.
- Every async route segment needs `loading.tsx` and `error.tsx`.
- Use the `metadata` export for titles/SEO, not hardcoded `<title>`.
- Backend URLs and the members first-page number are env-configurable (see README); defaults point to localhost:8080 and 1-based pages.

## Testing

Tests are in `tests/` (Vitest, jsdom, React Testing Library). Mock `fetch` with `vi.stubGlobal`; mock `next/navigation`, `next/headers` and `server-only` where needed. Add tests alongside any new route handler, form or lib function.

## Gotchas

- A temporary `console.log('[TEMP] access-token ...')` is in `app/api/login/route.ts` (marked `TODO: remove`). Don't add more token logging, and remove this one when the user says so.
- Backend response/auth assumptions (cookie name `access-token`, Bearer scheme, 1-based paging) were assumed, not confirmed. Check with the user if something behaves unexpectedly.
