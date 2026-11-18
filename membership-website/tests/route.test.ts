import { POST } from '@/app/api/register/route';

const valid = { firstName: 'Jane', lastName: 'Doe', email: 'jane@example.com', phoneNo: '1234567890', password: 'correct horse battery' };
const req = (body: unknown, raw = false) =>
  new Request('http://localhost/api/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: raw ? (body as string) : JSON.stringify(body),
  });

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe('POST /api/register', () => {
  it('returns 400 for non-JSON body', async () => {
    const res = await POST(req('not json', true));
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('returns 400 for a short password and does not call backend', async () => {
    const res = await POST(req({ ...valid, password: 'short' }));
    expect((await res.json()).errors.password).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('does not trim the password', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    await POST(req({ ...valid, password: '  spaced out pass  ' }));
    expect(JSON.parse(fetchMock.mock.calls[0][1].body).password).toBe('  spaced out pass  ');
  });

  it('returns 400 with field errors and does not call backend', async () => {
    const res = await POST(req({ ...valid, phoneNo: '123' }));
    const data = await res.json();
    expect(res.status).toBe(400);
    expect(data.errors.phoneNo).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('forwards JSON to the backend and returns success', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    const res = await POST(req({ ...valid, firstName: '  Jane  ' }));
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/register');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual(valid);
  });

  it('extracts a clean message from backend 4xx JSON', async () => {
    const body = { status: 400, message: 'phoneNo: Size of phone no must be between 10 to 12', timestamp: 'x' };
    fetchMock.mockResolvedValue(new Response(JSON.stringify(body), { status: 400 }));
    const res = await POST(req(valid));
    const data = await res.json();
    expect(res.status).toBe(400);
    expect(data.message).toBe('Size of phone no must be between 10 to 12');
    expect(JSON.stringify(data)).not.toContain('timestamp');
  });

  it('success message is plain', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    const data = await (await POST(req(valid))).json();
    expect(data.message).toBe('Registration successful!');
  });

  it('409 without a body falls back to a duplicate-email message', async () => {
    fetchMock.mockResolvedValue(new Response('', { status: 409 }));
    expect((await (await POST(req(valid))).json()).message).toMatch(/already exists/i);
  });

  it('uses a generic message for backend 5xx', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'stack trace' }), { status: 500 }));
    const data = await (await POST(req(valid))).json();
    expect(data.message).not.toContain('stack trace');
  });

  it('uses a fallback when backend body is not JSON', async () => {
    fetchMock.mockResolvedValue(new Response('<html>', { status: 400 }));
    const data = await (await POST(req(valid))).json();
    expect(data.message).toMatch(/failed/i);
  });

  it('returns 502 when backend is unreachable', async () => {
    fetchMock.mockRejectedValue(new Error('ECONNREFUSED'));
    const res = await POST(req(valid));
    expect(res.status).toBe(502);
  });
});
