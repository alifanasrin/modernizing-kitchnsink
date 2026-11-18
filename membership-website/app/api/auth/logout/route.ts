import { logger } from '@/lib/logger';
import { rejectUnsafeRequest } from '@/lib/security';
import { NextResponse, type NextRequest } from 'next/server';
import { ACCESS_COOKIE, REFRESH_COOKIE, clearFlowCookies, clearSessionCookies, oauth, postJson } from '@/lib/oauth';

const logoutWith = (token: string) =>
  fetch(oauth.logoutUrl, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' });

// POST /auth/logout revokes the session of the Bearer access token (and its refresh token).
// The access cookie lives only 15 min, so when it is gone/rejected we first trade the refresh token for a
// fresh access token, otherwise the server-side session would stay alive. Local cookies are always cleared.
export async function POST(req: NextRequest) {
  const blocked = rejectUnsafeRequest(req, { requireJson: false }); // no body, so only the Origin check applies
  if (blocked) return blocked;

  try {
    let res = await tryLogout(req.cookies.get(ACCESS_COOKIE)?.value);
    if (!res || res.status === 401) {
      const fresh = await freshAccessToken(req.cookies.get(REFRESH_COOKIE)?.value);
      if (fresh) res = await logoutWith(fresh);
      else logger.warn('logout: no usable token, only local cookies cleared');
    }
    if (res) logger.info('logout: backend session revoked', { status: res.status });
  } catch (e) {
    logger.warn('logout: backend unreachable, only local cookies cleared', { error: e });
    // Best effort: backend unreachable; the browser session is still ended below.
  }

  logger.info('logout: local session cleared');
  const out = NextResponse.json({ message: 'Logged out.' });
  clearSessionCookies(out);
  clearFlowCookies(out);
  return out;
}

async function tryLogout(token?: string) {
  return token ? logoutWith(token) : null;
}

async function freshAccessToken(refreshToken?: string): Promise<string | null> {
  if (!refreshToken) return null;
  const res = await postJson(oauth.refreshUrl, { refreshToken });
  if (!res.ok) return null;
  const data = await res.json().catch(() => null);
  return typeof data?.accessToken === 'string' ? data.accessToken : null;
}
