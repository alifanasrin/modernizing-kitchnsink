import { logger } from '@/lib/logger';
import { NextResponse, type NextRequest } from 'next/server';
import { REFRESH_COOKIE, clearSessionCookies, oauth, postJson, safePath, setSessionCookies, type TokenResponse } from '@/lib/oauth';

// Refresh tokens are single use and rotated, so cookies are replaced on every call.
// Server components cannot set cookies, so they redirect here: /api/auth/refresh?next=/members
export async function GET(req: NextRequest) {
  const next = safePath(req.nextUrl.searchParams.get('next'));
  const toLogin = (why: string) => {
    logger.info('refresh: sending user to login', { why });
    const out = NextResponse.redirect(new URL(`/login?state=${encodeURIComponent(next)}`, req.url));
    clearSessionCookies(out);
    return out;
  };

  const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) return toLogin('no refresh token');

  try {
    const res = await postJson(oauth.refreshUrl, { refreshToken });
    if (!res.ok) return toLogin(`backend status ${res.status}`);
    const tokens: TokenResponse = await res.json();
    if (!tokens?.accessToken) return toLogin('no access token in response');

    // `_r=1` tells the page this token is fresh, so a 401 now means "log in", not "refresh again".
    logger.info('refresh: session refreshed');
    const dest = new URL(next, req.url);
    dest.searchParams.set('_r', '1');
    const out = NextResponse.redirect(dest);
    setSessionCookies(out, tokens);
    return out;
  } catch (e) {
    logger.error('refresh: backend unreachable', { error: e });
    return toLogin('backend unreachable');
  }
}
