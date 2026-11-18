import { challengeFor, generateVerifier } from '@/lib/pkce';

describe('pkce', () => {
  it('matches the RFC 7636 appendix B vector', async () => {
    expect(await challengeFor('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });
  it('generates a valid, unique verifier', () => {
    const v = generateVerifier();
    expect(v).toMatch(/^[A-Za-z0-9\-._~]{43,128}$/);
    expect(generateVerifier()).not.toBe(v);
  });
});
