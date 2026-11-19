import { NextRequest } from 'next/server';
import { GET as start } from '@/app/api/oauth/start/route';
import { GET as callback } from '@/app/callback/route';
import { GET as refresh } from '@/app/api/auth/refresh/route';
import { POST as logout } from '@/app/api/auth/logout/route';
import { challengeFor } from '@/lib/pkce';
import { cleanOrigin, safePath } from '@/lib/oauth';

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const nreq = (path: string, cookie?: string) =>
  new NextRequest(`http://localhost:3000${path}`, cookie ? { headers: { cookie } } : undefined);
const jreq = (path: string, body: unknown) =>
  new Request(`http://localhost:3000${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const tokens = { accessToken: 'AT', tokenType: 'Bearer', expiresIn: 900, refreshToken: 'sid.RT', sessionId: 'sid' };

describe('GET /api/oauth/start', () => {
  it('calls authorize with S256 challenge, stores verifier, redirects to the auth service login page', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'http://localhost:8080/login?request_id=abc%20123' } }));
    const res = await start(nreq('/api/oauth/start'));
    const [url, init] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.origin + u.pathname).toBe('http://localhost:8080/oauth2/authorize');
    expect(u.searchParams.get('response_type')).toBe('code');
    expect(u.searchParams.get('client_id')).toBe('5aeaf2312d8a55033ecdefcn');
    expect(u.searchParams.get('redirect_uri')).toBe('http://localhost:3000/callback');
    expect(u.searchParams.get('code_challenge_method')).toBe('S256');
    expect(init.redirect).toBe('manual');

    const verifier = res.cookies.get('pkce-verifier')?.value as string;
    expect(verifier).toMatch(/^[A-Za-z0-9\-._~]{43,128}$/);
    expect(u.searchParams.get('code_challenge')).toBe(await challengeFor(verifier));
    expect(res.headers.get('set-cookie')).toMatch(/HttpOnly/i);
    expect(res.cookies.get('oauth-request')?.value).toBe('abc 123');
    expect(res.headers.get('location')).toBe('http://localhost:8080/login?request_id=abc%20123');
  });

  it('sends no state when the user did not come from another page', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'http://localhost:8080/login?request_id=r1' } }));
    const res = await start(nreq('/api/oauth/start'));
    expect(new URL(String(fetchMock.mock.calls[0][0])).searchParams.has('state')).toBe(false);
    expect(res.cookies.get('oauth-state')?.value).toBe('');
  });

  it('sends state (the origin page) and remembers it', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'http://localhost:8080/login?request_id=r1' } }));
    const res = await start(nreq('/api/oauth/start?state=' + encodeURIComponent('/members')));
    expect(new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('state')).toBe('/members');
    expect(res.cookies.get('oauth-state')?.value).toBe('/members');
  });

  it.each(['/login', '/', '/api/oauth/start', 'https://evil.example', '//evil.example'])('does not send state %s', async (st) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'http://localhost:8080/login?request_id=r1' } }));
    await start(nreq('/api/oauth/start?state=' + encodeURIComponent(st)));
    expect(new URL(String(fetchMock.mock.calls[0][0])).searchParams.has('state')).toBe(false);
  });

  it('redirects to /invalid-client when authorize returns 400', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 400 }));
    const res = await start(nreq('/api/oauth/start'));
    expect(new URL(res.headers.get('location') as string).pathname).toBe('/invalid-client');
    expect(res.cookies.get('pkce-verifier')).toBeUndefined();
  });

  it('redirects with an error when authorize gives no request_id', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'http://localhost:8080/login' } }));
    const res = await start(nreq('/api/oauth/start'));
    expect(res.headers.get('location')).toBe('http://localhost:3000/?error=authorize_failed');
    expect(res.cookies.get('pkce-verifier')).toBeUndefined();
  });

  it('redirects with an error when backend is down', async () => {
    fetchMock.mockRejectedValue(new Error('down'));
    const res = await start(nreq('/api/oauth/start'));
    expect(res.headers.get('location')).toContain('error=unreachable');
  });
});

describe('GET /api/oauth/callback', () => {
  it('exchanges code + verifier (JSON body) and sets session cookies', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(tokens)));
    const res = await callback(nreq('/callback?code=CODE', 'pkce-verifier=VER'));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/oauth2/token');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(String(init.body))).toEqual({
      grantType: 'authorization_code',
      code: 'CODE',
      clientId: '5aeaf2312d8a55033ecdefcn',
      codeVerifier: 'VER',
    });
    expect(res.headers.get('location')).toBe('http://localhost:3000/home');
    expect(res.cookies.get('access-token')?.value).toBe('AT');
    expect(res.cookies.get('refresh-token')?.value).toBe('sid.RT');
    expect(res.headers.get('set-cookie')).toMatch(/HttpOnly/i);
    expect(res.cookies.get('pkce-verifier')?.value).toBe('');
    expect(res.cookies.get('oauth-request')?.value).toBe('');
  });
  it('returns the user to the page in state, once state matches what we sent', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(tokens)));
    const st = '/members';
    const res = await callback(nreq(`/callback?code=CODE&state=${encodeURIComponent(st)}`, `pkce-verifier=VER; oauth-state=${encodeURIComponent(st)}`));
    expect(res.headers.get('location')).toBe('http://localhost:3000' + st);
    expect(res.cookies.get('oauth-state')?.value).toBe('');
  });
  it.each([
    ['state returned but none sent', '/callback?code=CODE&state=%2Fmembers', 'pkce-verifier=VER'],
    ['state sent but not returned', '/callback?code=CODE', 'pkce-verifier=VER; oauth-state=%2Fmembers'],
    ['state differs', '/callback?code=CODE&state=%2Fother', 'pkce-verifier=VER; oauth-state=%2Fmembers'],
  ])('aborts without exchanging the code when %s', async (_n, url, cookie) => {
    const res = await callback(nreq(url, cookie));
    expect(res.headers.get('location')).toContain('error=token_failed');
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('goes back to login without calling the backend when code or verifier is missing', async () => {
    const res = await callback(nreq('/callback?code=CODE'));
    expect(res.headers.get('location')).toBe('http://localhost:3000/?error=token_failed');
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('goes back to login when the token endpoint refuses', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 400 }));
    const res = await callback(nreq('/callback?code=CODE', 'pkce-verifier=VER'));
    expect(res.headers.get('location')).toContain('error=token_failed');
    expect(res.cookies.get('access-token')).toBeUndefined();
  });
});

describe('GET /api/auth/refresh', () => {
  it('rotates tokens and redirects to next with _r=1', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ ...tokens, accessToken: 'AT2', refreshToken: 'sid.RT2' })));
    const res = await refresh(nreq('/api/auth/refresh?next=/members%3FpageNo%3D2', 'refresh-token=sid.RT'));
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ refreshToken: 'sid.RT' });
    expect(res.headers.get('location')).toBe('http://localhost:3000/members?pageNo=2&_r=1');
    expect(res.cookies.get('access-token')?.value).toBe('AT2');
    expect(res.cookies.get('refresh-token')?.value).toBe('sid.RT2');
  });
  it('ignores an off-site next', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify(tokens)));
    const res = await refresh(nreq('/api/auth/refresh?next=//evil.example', 'refresh-token=sid.RT'));
    expect(new URL(res.headers.get('location')!).host).toBe('localhost:3000');
  });
  it('clears cookies and goes to login when refresh fails', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 401 }));
    const res = await refresh(nreq('/api/auth/refresh?next=/members', 'refresh-token=old'));
    expect(res.headers.get('location')).toBe('http://localhost:3000/api/oauth/start?state=%2Fmembers');
    expect(res.cookies.get('access-token')?.value).toBe('');
    expect(res.cookies.get('refresh-token')?.value).toBe('');
  });
  it('goes to login without a refresh cookie', async () => {
    const res = await refresh(nreq('/api/auth/refresh?next=/members'));
    expect(res.headers.get('location')).toBe('http://localhost:3000/api/oauth/start?state=%2Fmembers');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/auth/logout', () => {
  const lreq = (cookie: string) => new NextRequest('http://localhost:3000/api/auth/logout', { method: 'POST', headers: { cookie } });

  it('revokes the session with the Bearer token and clears all cookies', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const res = await logout(lreq('access-token=AT; refresh-token=R; pkce-verifier=V'));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/auth/logout');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer AT');
    for (const n of ['access-token', 'refresh-token', 'pkce-verifier', 'oauth-request', 'oauth-state']) {
      expect(res.cookies.get(n)?.value, n).toBe('');
    }
  });

  it('with only a refresh token, gets a fresh access token first so the session is really revoked', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...tokens, accessToken: 'AT2' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const res = await logout(lreq('refresh-token=sid.RT'));
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/auth/refresh');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ refreshToken: 'sid.RT' });
    expect(fetchMock.mock.calls[1][0]).toBe('http://localhost:8080/auth/logout');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer AT2');
    expect(res.cookies.get('refresh-token')?.value).toBe('');
  });

  it('retries via refresh when the backend rejects the access token (401)', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...tokens, accessToken: 'AT3' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    await logout(lreq('access-token=OLD; refresh-token=sid.RT'));
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer AT3');
  });

  it('makes no backend call when there is no session at all', async () => {
    const res = await logout(lreq('x=1'));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(res.status).toBe(200);
  });

  it('still clears cookies if the backend is down', async () => {
    fetchMock.mockRejectedValue(new Error('down'));
    const res = await logout(lreq('access-token=AT'));
    expect(res.status).toBe(200);
    expect(res.cookies.get('access-token')?.value).toBe('');
  });
});

describe('safePath', () => {
  it.each(['//evil.com', 'https://evil.com', '/\\evil', '', null])('rejects %s', (p) => expect(safePath(p as string)).toBe('/members'));
  it('accepts relative paths', () => expect(safePath('/members?x=1')).toBe('/members?x=1'));
});

describe('cleanOrigin', () => {
  it('only the members list is a valid state; query string is dropped', () => {
    expect(cleanOrigin('/members')).toBe('/members');
    expect(cleanOrigin('/members?pageNo=2&pageSize=10#x')).toBe('/members');
  });
  it.each(['/', '/login', '/mfa', '/callback', '/home', '/api/oauth/start', '//evil.com', 'https://x.y', '', null, '/membersx'])(
    'rejects %s',
    (p) => expect(cleanOrigin(p as string)).toBeNull(),
  );
});
