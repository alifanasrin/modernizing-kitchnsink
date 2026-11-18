import { NextResponse } from 'next/server';
import { logger } from './logger';

// ---- CSRF defence for cookie-authenticated JSON endpoints -------------------------------------------------
// SameSite=Lax already stops cross-site POSTs from carrying cookies; this is the second layer.
// 1. Content-Type must (unless requireJson is false, for body-less endpoints like logout) be application/json (a cross-site <form> can only send urlencoded/multipart/text).
// 2. A browser-sent Origin must match this host; without Origin, Sec-Fetch-Site must not say cross-site.
export function rejectUnsafeRequest(req: Request, { requireJson = true } = {}): NextResponse | null {
  if (requireJson && !(req.headers.get('content-type') ?? '').toLowerCase().startsWith('application/json')) {
    return NextResponse.json({ message: 'Content-Type must be application/json.' }, { status: 415 });
  }
  const origin = req.headers.get('origin');
  const host = req.headers.get('x-forwarded-host') ?? req.headers.get('host') ?? new URL(req.url).host;
  let sameOrigin: boolean;
  if (origin) {
    try {
      sameOrigin = new URL(origin).host === host;
    } catch {
      sameOrigin = false;
    }
  } else {
    sameOrigin = req.headers.get('sec-fetch-site') !== 'cross-site';
  }
  if (!sameOrigin) {
    logger.warn('security: cross-origin request blocked', { origin: origin ?? 'none' });
    return NextResponse.json({ message: 'Cross-origin requests are not allowed.' }, { status: 403 });
  }
  return null;
}

// ---- Rate limiting ----------------------------------------------------------------------------------------
// Fixed-window counter per client IP + bucket. In-memory: per server instance and reset on restart, so behind
// several instances put a shared limiter (Redis / CDN / API gateway) in front instead. Trusts X-Forwarded-For,
// so only deploy behind a proxy that overwrites it.
const hits = new Map<string, { count: number; resetAt: number }>();

export function resetRateLimits() {
  hits.clear();
}

export function rateLimit(req: Request, bucket: string, limit: number, windowMs = 60_000): NextResponse | null {
  const ip = req.headers.get('x-forwarded-for')?.split(',')[0].trim() || req.headers.get('x-real-ip') || 'unknown';
  const key = `${bucket}:${ip}`;
  const now = Date.now();
  if (hits.size > 10_000) for (const [k, v] of hits) if (v.resetAt <= now) hits.delete(k);
  const entry = hits.get(key);
  if (!entry || entry.resetAt <= now) {
    hits.set(key, { count: 1, resetAt: now + windowMs });
    return null;
  }
  if (++entry.count <= limit) return null;
  logger.warn('security: rate limit exceeded', { bucket });
  const res = NextResponse.json({ message: 'Too many attempts. Please wait a moment and try again.' }, { status: 429 });
  res.headers.set('Retry-After', String(Math.ceil((entry.resetAt - now) / 1000)));
  return res;
}
