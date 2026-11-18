import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { rateLimit, rejectUnsafeRequest, resetRateLimits } from '@/lib/security';
import { POST as login } from '@/app/api/login/route';
import { POST as logout } from '@/app/api/auth/logout/route';
import { NextRequest } from 'next/server';

const req = (headers: Record<string, string> = {}, url = 'http://localhost:3000/api/x') =>
  new Request(url, { method: 'POST', headers: { 'Content-Type': 'application/json', host: 'localhost:3000', ...headers }, body: '{}' });

beforeEach(resetRateLimits);

describe('rejectUnsafeRequest', () => {
  it('allows same-origin JSON', () => {
    expect(rejectUnsafeRequest(req({ origin: 'http://localhost:3000' }))).toBeNull();
  });
  it('allows requests with no Origin unless Sec-Fetch-Site is cross-site', () => {
    expect(rejectUnsafeRequest(req())).toBeNull();
    expect(rejectUnsafeRequest(req({ 'sec-fetch-site': 'cross-site' }))?.status).toBe(403);
  });
  it('blocks a foreign Origin', () => {
    expect(rejectUnsafeRequest(req({ origin: 'https://evil.example' }))?.status).toBe(403);
  });
  it('blocks non-JSON content types', () => {
    expect(rejectUnsafeRequest(req({ 'Content-Type': 'application/x-www-form-urlencoded' }))?.status).toBe(415);
  });
});

describe('rateLimit', () => {
  it('returns 429 with Retry-After after the limit, per IP and bucket', () => {
    const a = req({ 'x-forwarded-for': '1.1.1.1' });
    expect(rateLimit(a, 'b', 2)).toBeNull();
    expect(rateLimit(a, 'b', 2)).toBeNull();
    const blocked = rateLimit(a, 'b', 2);
    expect(blocked?.status).toBe(429);
    expect(blocked?.headers.get('retry-after')).toBeTruthy();
    expect(rateLimit(req({ 'x-forwarded-for': '2.2.2.2' }), 'b', 2)).toBeNull();
    expect(rateLimit(a, 'other', 2)).toBeNull();
  });
});

describe('route wiring', () => {
  it('login rejects cross-origin and throttles', async () => {
    const bad = new NextRequest('http://localhost:3000/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json', origin: 'https://evil.example' }, body: '{}' });
    expect((await login(bad)).status).toBe(403);
    let last = 0;
    for (let i = 0; i < 11; i++) {
      last = (await login(new NextRequest('http://localhost:3000/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }))).status;
    }
    expect(last).toBe(429);
  });
  it('logout rejects cross-origin', async () => {
    const bad = new NextRequest('http://localhost:3000/api/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json', origin: 'https://evil.example' }, body: '{}' });
    expect((await logout(bad)).status).toBe(403);
  });
});
