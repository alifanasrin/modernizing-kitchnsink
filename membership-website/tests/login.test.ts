import { NextRequest } from 'next/server';
import { POST } from '@/app/api/login/route';
import { validateLogin } from '@/lib/validation';

describe('validateLogin', () => {
  it('accepts valid input', () => {
    expect(validateLogin({ email: 'a@b.com', password: 'x' })).toEqual({});
  });
  it('rejects bad email and empty password', () => {
    const e = validateLogin({ email: 'nope', password: '' });
    expect(e.email).toBeDefined();
    expect(e.password).toBeDefined();
  });
});

const valid = { email: 'a@b.com', password: 'secret' };
const FLOW = 'oauth-request=req-1; pkce-verifier=VER';
const req = (body: unknown, raw = false, cookie: string | null = FLOW) =>
  new NextRequest('http://localhost/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(cookie ? { cookie } : {}) },
    body: raw ? (body as string) : JSON.stringify(body),
  });
const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe('POST /api/login', () => {
  it('400 on non-JSON body', async () => {
    expect((await POST(req('x', true))).status).toBe(400);
  });
  it('400 on invalid input without calling backend', async () => {
    const res = await POST(req({ email: 'bad', password: '' }));
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each(['pkce-verifier=VER', 'oauth-request=req-1', ''])('401 and no backend call without an authorize call (cookies: %j)', async (cookie) => {
    const res = await POST(req(valid, false, cookie || null));
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('forwards creds + cookie request_id to /auth/login and returns the 302 Location for our callback', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'http://localhost:3000/callback?code=xyz' } }));
    const res = await POST(req(valid));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ redirectTo: 'http://localhost:3000/callback?code=xyz' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/auth/login');
    expect(init.redirect).toBe('manual');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ ...valid, requestId: 'req-1' }); // requestId from the cookie
    expect(res.cookies.get('access-token')).toBeUndefined();
  });
  it('refuses a Location on another origin', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'https://evil.example/cb?code=x' } }));
    expect((await POST(req(valid))).status).toBe(502);
  });
  it('502 when a 2xx has no redirect', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    expect((await POST(req(valid))).status).toBe(502);
  });
  it('401 with empty body gives friendly message', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 401 }));
    const data = await (await POST(req(valid))).json();
    expect(data.message).toBe('Invalid username or password.');
  });
  it('passes through a clean backend message, never raw JSON', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ status: 401, message: 'please validate your email', timestamp: 't' }), { status: 401 }));
    const data = await (await POST(req(valid))).json();
    expect(data.message).toBe('please validate your email');
    expect(JSON.stringify(data)).not.toContain('timestamp');
  });
  it('generic message on 5xx', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'trace' }), { status: 500 }));
    const data = await (await POST(req(valid))).json();
    expect(data.message).not.toContain('trace');
  });
  it('502 when backend unreachable', async () => {
    fetchMock.mockRejectedValue(new Error('down'));
    expect((await POST(req(valid))).status).toBe(502);
  });
});
