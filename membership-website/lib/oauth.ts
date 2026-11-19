import { NextResponse } from 'next/server';
import { logger } from './logger';
import { hosts } from './config';

// ---- Cookies --------------------------------------------------------------------------------------------
// Every cookie is HttpOnly, SameSite=Lax, host-only (no Domain), short-lived, and Secure in production.
// In production names get a prefix the browser enforces: `__Host-` (Secure, Path=/, no Domain) for site-wide
// cookies, `__Secure-` for the refresh token, whose Path is narrowed to /api/auth so it is only ever sent there.
// (Prefixes are skipped in dev because Secure cookies do not work over plain http://localhost in every browser.)
// SameSite=Lax (not Strict) on purpose: a link from another site into /members must still carry cookies.
const PROD = process.env.NODE_ENV === 'production';
const host = PROD ? '__Host-' : '';

export const ACCESS_COOKIE = `${host}access-token`;
export const REFRESH_COOKIE = `${PROD ? '__Secure-' : ''}refresh-token`;
export const VERIFIER_COOKIE = `${host}pkce-verifier`;
export const REQUEST_COOKIE = `${host}oauth-request`; // request_id returned by /oauth2/authorize
export const STATE_COOKIE = `${host}oauth-state`; // the `state` we sent to /oauth2/authorize (where the user came from)
export const REFRESH_MAX_AGE = 7 * 24 * 60 * 60; // backend refresh token lifetime (7 days)
export const REFRESH_PATH = '/api/auth';

const cookiePath = (name: string) => (name === REFRESH_COOKIE ? REFRESH_PATH : '/');


const BASE = hosts.auth;

// Public OAuth client registered on the backend (client id + exact redirect URI).
export const oauth = {
  clientId: process.env.OAUTH_CLIENT_ID ?? '5aeaf2312d8a55033ecdefcn',
  redirectUri: process.env.OAUTH_REDIRECT_URI ?? 'http://localhost:3000/callback',
  authorizeUrl: process.env.OAUTH_AUTHORIZE_URL ?? `${BASE}/oauth2/authorize`,
  tokenUrl: process.env.OAUTH_TOKEN_URL ?? `${BASE}/oauth2/token`,
  // Hosted by the auth service, not by this app. Used for the "Register" link on the welcome page.
  registerPageUrl: process.env.REGISTER_PAGE_URL ?? `${BASE}/register`,
  refreshUrl: process.env.REFRESH_API_URL ?? `${BASE}/auth/refresh`,
  logoutUrl: process.env.LOGOUT_API_URL ?? `${BASE}/auth/logout`,
};

export type TokenResponse = {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  refreshToken?: string;
  sessionId?: string;
};

/** The only way cookies are written: fixed attributes, path per cookie, never cacheable. */
export function setCookie(res: NextResponse, name: string, value: string, maxAge: number) {
  res.cookies.set({ name, value, httpOnly: true, secure: PROD, sameSite: 'lax', path: cookiePath(name), maxAge });
  res.headers.set('Cache-Control', 'no-store');
}

/** Deleting needs the same name + path as when it was set. */
export function clearCookie(res: NextResponse, name: string) {
  setCookie(res, name, '', 0);
}

export function setSessionCookies(res: NextResponse, t: TokenResponse) {
  setCookie(res, ACCESS_COOKIE, t.accessToken, t.expiresIn && t.expiresIn > 0 ? t.expiresIn : 900);
  if (t.refreshToken) setCookie(res, REFRESH_COOKIE, t.refreshToken, REFRESH_MAX_AGE);
}

export function clearSessionCookies(res: NextResponse) {
  clearCookie(res, ACCESS_COOKIE);
  clearCookie(res, REFRESH_COOKIE);
}

export const FLOW_EXPIRED = 'Your sign-in session expired. Please restart sign-in.';

export function clearFlowCookies(res: NextResponse) {
  clearCookie(res, VERIFIER_COOKIE);
  clearCookie(res, REQUEST_COOKIE);
  clearCookie(res, STATE_COOKIE);
}

export const badGateway = () => {
  logger.error('backend unreachable or returned an unusable response');
  return NextResponse.json(
    { message: 'Bad Gateway: the backend server could not be reached. Please try again later.' },
    { status: 502 },
  );
};

export function postJson(url: string, body: unknown, init: { redirect?: RequestRedirect } = {}): Promise<Response> {
  return fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    cache: 'no-store',
    ...init,
  });
}

// Never forward the raw backend body; extract a readable message only.
export async function friendlyMessage(res: Response, fallback4xx: string): Promise<string> {
  const fallback = res.status >= 500 ? 'Something went wrong on our side. Please try again later.' : fallback4xx;
  try {
    const data = await res.json();
    const raw = typeof data?.message === 'string' ? data.message : '';
    if (res.status >= 500 || !raw) return fallback;
    return raw.replace(/^\w+:\s*/, ''); // strip "field: " prefix
  } catch {
    return fallback;
  }
}

export async function failure(res: Response, fallback4xx: string) {
  (res.status >= 500 ? logger.error : logger.warn)('backend request failed', { status: res.status, url: res.url });
  return NextResponse.json({ message: await friendlyMessage(res, fallback4xx) }, { status: res.status });
}

/** Only same-site relative paths may be used as a post-login destination. */
export const safePath = (p: string | null | undefined, fallback = '/members') =>
  p && p.startsWith('/') && !p.startsWith('//') && !p.startsWith('/\\') ? p : fallback;

/** Where a signed-in user lands when the flow did not start from a page that needs to be returned to. */
export const DEFAULT_LANDING = '/home';

// Only these pages are worth returning to. The welcome page, register etc. are never sent as `state`.
const STATE_PAGES = ['/members'];

/**
 * `state` = the in-app page (path only, no query string) the user came from, but only for STATE_PAGES.
 * Returns null when no state should be sent; the user then lands on DEFAULT_LANDING.
 */
export function cleanOrigin(p: string | null | undefined): string | null {
  if (!p) return null;
  const path = p.split(/[?#]/)[0];
  return STATE_PAGES.includes(path) ? path : null;
}
