import { NextResponse } from 'next/server';

const attrs = (res: NextResponse, name: string) => {
  const line = res.headers.getSetCookie().find((c) => c.startsWith(`${name}=`)) ?? '';
  return { line, has: (re: RegExp) => re.test(line) };
};

async function load(env: 'test' | 'production') {
  vi.resetModules();
  vi.stubEnv('NODE_ENV', env);
  return import('@/lib/oauth');
}
afterEach(() => vi.unstubAllEnvs());

describe('cookie attributes (dev/test)', () => {
  it('every cookie is HttpOnly, SameSite=Lax, host-only, with a Max-Age and no-store', async () => {
    const o = await load('test');
    const res = NextResponse.json({});
    o.setSessionCookies(res, { accessToken: 'A', refreshToken: 'R', expiresIn: 900 });
    o.setCookie(res, o.VERIFIER_COOKIE, 'v', 600);
    o.setCookie(res, o.REQUEST_COOKIE, 'r', 600);
    o.setCookie(res, o.STATE_COOKIE, '/members', 600);
    for (const n of [o.ACCESS_COOKIE, o.REFRESH_COOKIE, o.VERIFIER_COOKIE, o.REQUEST_COOKIE, o.STATE_COOKIE]) {
      const c = attrs(res, n);
      expect(c.has(/HttpOnly/i), n).toBe(true);
      expect(c.has(/SameSite=lax/i), n).toBe(true);
      expect(c.has(/Max-Age=\d+/i), n).toBe(true);
      expect(c.has(/Domain=/i), n).toBe(false);
    }
    expect(res.headers.get('cache-control')).toBe('no-store');
  });

  it('lifetimes: access = expiresIn, refresh = 7 days, flow cookies = 10 min', async () => {
    const o = await load('test');
    const res = NextResponse.json({});
    o.setSessionCookies(res, { accessToken: 'A', refreshToken: 'R', expiresIn: 900 });
    expect(attrs(res, o.ACCESS_COOKIE).has(/Max-Age=900/)).toBe(true);
    expect(attrs(res, o.REFRESH_COOKIE).has(/Max-Age=604800/)).toBe(true);
  });

  it('refresh token is only sent to /api/auth; everything else Path=/', async () => {
    const o = await load('test');
    const res = NextResponse.json({});
    o.setSessionCookies(res, { accessToken: 'A', refreshToken: 'R' });
    expect(attrs(res, o.REFRESH_COOKIE).has(/Path=\/api\/auth(;|$)/)).toBe(true);
    expect(attrs(res, o.ACCESS_COOKIE).has(/Path=\/(;|$)/)).toBe(true);
  });

  it('clearing uses the same path and expires immediately', async () => {
    const o = await load('test');
    const res = NextResponse.json({});
    o.clearSessionCookies(res);
    expect(attrs(res, o.REFRESH_COOKIE).has(/Path=\/api\/auth/)).toBe(true);
    expect(attrs(res, o.REFRESH_COOKIE).has(/Max-Age=0/)).toBe(true);
  });
});

describe('cookie attributes (production)', () => {
  it('adds Secure and the __Host-/__Secure- prefixes the browser enforces', async () => {
    const o = await load('production');
    expect(o.ACCESS_COOKIE).toBe('__Host-access-token');
    expect(o.VERIFIER_COOKIE).toBe('__Host-pkce-verifier');
    expect(o.REFRESH_COOKIE).toBe('__Secure-refresh-token'); // __Host- needs Path=/, ours is /api/auth
    const res = NextResponse.json({});
    o.setSessionCookies(res, { accessToken: 'A', refreshToken: 'R' });
    for (const n of [o.ACCESS_COOKIE, o.REFRESH_COOKIE]) {
      expect(attrs(res, n).has(/;\s*Secure/i), n).toBe(true);
      expect(attrs(res, n).has(/HttpOnly/i), n).toBe(true);
    }
    expect(attrs(res, o.ACCESS_COOKIE).has(/Path=\/(;|$)/)).toBe(true); // __Host- requires Path=/
  });
});
