vi.mock('server-only', () => ({}));
import { firstNameFor, tokenClaims } from '@/lib/profile';

const jwt = (claims: object) => `h.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.s`;
const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe('tokenClaims', () => {
  it('decodes the payload', () => expect(tokenClaims(jwt({ sub: 'a@b.co' }))).toEqual({ sub: 'a@b.co' }));
  it.each(['', 'abc', 'a.!!!.c', 'a.bnVsbA.c'])('returns {} for garbage %j', (t) => expect(tokenClaims(t)).toEqual({}));
});

describe('firstNameFor', () => {
  it('uses a name claim from the token without calling the backend', async () => {
    expect(await firstNameFor(jwt({ sub: 'a@b.co', given_name: ' Jane ' }))).toBe('Jane');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('falls back to the member record for the token email, sending the Bearer token', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ firstName: 'Asha' })));
    const token = jwt({ sub: 'a+b@b.co' });
    expect(await firstNameFor(token)).toBe('Asha');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8081/members/a%2Bb%40b.co');
    expect(init.headers.Authorization).toBe(`Bearer ${token}`);
  });

  it('returns null when the lookup fails, is unreachable, or the token has no identity', async () => {
    fetchMock.mockResolvedValueOnce(new Response('{}', { status: 404 }));
    expect(await firstNameFor(jwt({ sub: 'a@b.co' }))).toBeNull();
    fetchMock.mockRejectedValueOnce(new Error('down'));
    expect(await firstNameFor(jwt({ sub: 'a@b.co' }))).toBeNull();
    expect(await firstNameFor('garbage')).toBeNull();
  });
});
