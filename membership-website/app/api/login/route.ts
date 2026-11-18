import { NextResponse, type NextRequest } from 'next/server';
import { logger } from '@/lib/logger';
import { rateLimit, rejectUnsafeRequest } from '@/lib/security';
import { validateLogin, type LoginInput } from '@/lib/validation';
import { FLOW_EXPIRED, REQUEST_COOKIE, VERIFIER_COOKIE, badGateway, failure, oauth, postJson, setCookie } from '@/lib/oauth';

const INVALID_CREDENTIALS = 'Invalid username or password.';

// Step 2: email + password (+ the request_id from /oauth2/authorize). On a correct password the backend answers 302 with
// Location = our callback + one-time authorization code. We do not follow it server-side; the validated Location is handed to the browser.
// Anything else is a failure.
export async function POST(req: NextRequest) {
  const blocked = rejectUnsafeRequest(req) ?? rateLimit(req, 'login', 10);
  if (blocked) return blocked;

  // No credentials are checked unless this browser made an /oauth2/authorize call (via /api/oauth/start).
  // The request_id comes from that call's cookie, never from the client.
  const requestId = req.cookies.get(REQUEST_COOKIE)?.value;
  if (!requestId || !req.cookies.get(VERIFIER_COOKIE)?.value) {
    logger.warn('login: no active authorize flow');
    return NextResponse.json({ message: FLOW_EXPIRED }, { status: 401 });
  }

  let body: Partial<LoginInput>;
  try {
    body = await req.json();
  } catch {
    logger.warn('login: body is not JSON');
    return NextResponse.json({ message: 'Request body must be JSON.' }, { status: 400 });
  }

  const input: LoginInput = {
    email: String(body.email ?? '').trim(),
    password: String(body.password ?? ''),
  };
  const errors = validateLogin(input);
  if (Object.keys(errors).length > 0) {
    logger.warn('login: validation failed', { fields: Object.keys(errors).join(',') });
    return NextResponse.json({ message: 'Validation failed.', errors }, { status: 400 });
  }

  try {
    const res = await postJson(oauth.loginUrl, { ...input, requestId }, { redirect: 'manual' });
    const location = res.status >= 300 && res.status < 400 ? res.headers.get('location') : null;
    if (!location) {
      if (res.ok) {
        logger.error('login: backend success had no redirect');
        return badGateway();
      }
      if (res.status === 401 || res.status === 403) {
        // Wrong credentials always get one fixed message. Only account-state hints (e.g. "please validate your email") pass through.
        const r = await failure(res, INVALID_CREDENTIALS);
        const { message } = await r.clone().json();
        return /verif|validat|confirm|activat|lock|disabled/i.test(String(message))
          ? r
          : NextResponse.json({ message: INVALID_CREDENTIALS }, { status: 401 });
      }
      return failure(res, 'Login failed. Please check your details.');
    }
    const redirectTo = absolute(location);
    if (!redirectTo || !isOurCallback(redirectTo)) {
      logger.error('login: backend redirect is not our callback');
      return badGateway();
    } // never redirect the browser elsewhere
    logger.info('login: password accepted');
    return NextResponse.json({ redirectTo });
  } catch {
    return badGateway();
  }
}

function absolute(to: string): string | null {
  try {
    return new URL(to).toString();
  } catch {
    return null;
  }
}

function isOurCallback(to: string): boolean {
  try {
    const u = new URL(to);
    const ours = new URL(oauth.redirectUri);
    return u.origin === ours.origin && u.pathname === ours.pathname;
  } catch {
    return false;
  }
}
