const cookieGet = vi.fn();
vi.mock('next/headers', () => ({ cookies: async () => ({ get: cookieGet }) }));
vi.mock('server-only', () => ({}));

import { fetchMembers, UnauthorizedError } from '@/lib/members';

const fetchMock = vi.fn();
const page = { data: [{ firstName: 'A', lastName: 'B', phoneNo: '1234567890', email: 'a@b.com' }], pageNo: 1, pageSize: 10, totalRecords: 1, totalPages: 1 };

beforeEach(() => {
  fetchMock.mockReset();
  cookieGet.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe('fetchMembers', () => {
  it('throws Unauthorized without a cookie, without calling backend', async () => {
    cookieGet.mockReturnValue(undefined);
    await expect(fetchMembers(1, 10)).rejects.toBeInstanceOf(UnauthorizedError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('calls backend with query params and the Authorization header', async () => {
    cookieGet.mockReturnValue({ value: 'tok' });
    fetchMock.mockResolvedValue(new Response(JSON.stringify(page), { status: 200 }));
    const result = await fetchMembers(2, 20);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://localhost:8080/members?pageNo=2&pageSize=20');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(init.headers.Cookie).toBeUndefined();
    expect(result).toEqual(page);
  });

  it.each([401, 403])('throws Unauthorized on %i', async (status) => {
    cookieGet.mockReturnValue({ value: 'tok' });
    fetchMock.mockResolvedValue(new Response('', { status }));
    await expect(fetchMembers(1, 10)).rejects.toBeInstanceOf(UnauthorizedError);
  });

  it('throws a generic error on other failures', async () => {
    cookieGet.mockReturnValue({ value: 'tok' });
    fetchMock.mockResolvedValue(new Response('boom', { status: 500 }));
    await expect(fetchMembers(1, 10)).rejects.toThrow('Failed to load members');
  });
});

describe('fetchMembers 404', () => {
  it('throws PageNotFoundError when backend returns 404', async () => {
    const { PageNotFoundError } = await import('@/lib/members');
    cookieGet.mockReturnValue({ value: 'tok' });
    fetchMock.mockResolvedValue(new Response('', { status: 404 }));
    await expect(fetchMembers(99, 10)).rejects.toBeInstanceOf(PageNotFoundError);
  });
});

describe('fetchMembers bad gateway', () => {
  it('throws BadGatewayError when backend is unreachable', async () => {
    const { BadGatewayError } = await import('@/lib/members');
    cookieGet.mockReturnValue({ value: 'tok' });
    fetchMock.mockRejectedValue(new Error('ECONNREFUSED'));
    await expect(fetchMembers(1, 10)).rejects.toBeInstanceOf(BadGatewayError);
  });
});
