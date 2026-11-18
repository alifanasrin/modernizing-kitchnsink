import { NextResponse, type NextRequest } from 'next/server';
import { logger } from '@/lib/logger';
import { challengeFor, generateVerifier } from '@/lib/pkce';
import { REQUEST_COOKIE, STATE_COOKIE, VERIFIER_COOKIE, cleanOrigin, clearCookie, oauth, setCookie } from '@/lib/oauth';

// Step 1 of authorization code + PKCE. The server (not the browser) calls /oauth2/authorize,
// reads the request_id from the 302 Location, and sends the browser to our own /login page.
export async function GET(req: NextRequest) {
  const to = (path: string) => NextResponse.redirect(new URL(path, req.url));
  const verifier = generateVerifier();

  const url = new URL(oauth.authorizeUrl);
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('client_id', oauth.clientId);
  url.searchParams.set('redirect_uri', oauth.redirectUri);
  url.searchParams.set('code_challenge', await challengeFor(verifier));
  url.searchParams.set('code_challenge_method', 'S256');
  // Optional `state`: where the user came from. Not sent when they come from /login or /api/oauth/start themselves.
  const state = cleanOrigin(req.nextUrl.searchParams.get('state'));
  if (state) url.searchParams.set('state', state);

  let res: Response;
  try {
    res = await fetch(url, { redirect: 'manual', cache: 'no-store' });
  } catch (e) {
    logger.error('oauth: authorize endpoint unreachable', { error: e });
    return to('/login?error=unreachable');
  }

  // 400 from authorize means the backend rejected the client itself (unknown client_id / redirect_uri); retrying cannot help.
  if (res.status === 400) {
    logger.error('oauth: authorize rejected the client (400)', { clientId: oauth.clientId });
    return to('/invalid-client');
  }

  const location = res.headers.get('location');
  const requestId = location ? new URL(location, url).searchParams.get('request_id') : null;
  if (!requestId) {
    logger.error('oauth: authorize response had no request_id', { status: res.status });
    return to('/login?error=authorize_failed');
  }
  logger.info('oauth: authorize started', { hasState: !!state });

  // Everything that follows (login, callback) is only accepted while these cookies from THIS authorize call exist.
  const out = to(`/login?request_id=${encodeURIComponent(requestId)}`);
  setCookie(out, VERIFIER_COOKIE, verifier, 600);
  setCookie(out, REQUEST_COOKIE, requestId, 600);
  if (state) setCookie(out, STATE_COOKIE, state, 600);
  else clearCookie(out, STATE_COOKIE);
  return out;
}
