import { logger } from '@/lib/logger';
import { NextResponse, type NextRequest } from 'next/server';
import { DEFAULT_LANDING, STATE_COOKIE, VERIFIER_COOKIE, cleanOrigin, clearFlowCookies, oauth, postJson, setSessionCookies, type TokenResponse } from '@/lib/oauth';

// Step 3: the backend sent the browser here with ?code= (and `state` unchanged, if we sent one).
// Swap code + codeVerifier for tokens, then land on the page the user came from (state, members list only), else /home.
export async function GET(req: NextRequest) {
  const code = req.nextUrl.searchParams.get('code');
  const verifier = req.cookies.get(VERIFIER_COOKIE)?.value;

  const fail = (reason: string) => {
    logger.warn('oauth: callback failed, redirecting to welcome page', { reason });
    const out = NextResponse.redirect(new URL(`/?error=${reason}`, req.url));
    clearFlowCookies(out);
    return out;
  };
  if (!code || !verifier) {
    logger.warn('oauth: callback without code or verifier cookie');
    return fail('token_failed');
  }

  // `state` must come back exactly as sent (absent if we sent none); anything else aborts the flow.
  const sent = req.cookies.get(STATE_COOKIE)?.value ?? null;
  if (req.nextUrl.searchParams.get('state') !== sent) {
    logger.warn('oauth: state mismatch');
    return fail('token_failed');
  }

  let tokens: TokenResponse;
  try {
    // JSON body with camelCase keys (not form-encoded snake_case query-style params).
    const res = await postJson(oauth.tokenUrl, {
      grantType: 'authorization_code',
      code,
      clientId: oauth.clientId,
      codeVerifier: verifier,
    });
    if (!res.ok) {
      logger.warn('oauth: token exchange failed', { status: res.status });
      return fail('token_failed');
    }
    tokens = await res.json();
    if (!tokens?.accessToken) {
      logger.error('oauth: token response had no access token');
      return fail('token_failed');
    }
  } catch (e) {
    logger.error('oauth: token endpoint unreachable', { error: e });
    return fail('unreachable');
  }

  logger.info('login: session established', { landing: cleanOrigin(sent) ?? DEFAULT_LANDING });
  const out = NextResponse.redirect(new URL(cleanOrigin(sent) ?? DEFAULT_LANDING, req.url));
  setSessionCookies(out, tokens);
  clearFlowCookies(out); // flow finished; code and verifier are single use
  return out;
}
