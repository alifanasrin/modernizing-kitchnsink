// PKCE (RFC 7636) helpers using Web Crypto; work in Node route handlers and the browser.
const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

/** 32 random bytes -> 43-char verifier, matching the backend's `^[A-Za-z0-9\-._~]{43,128}$`. */
export function generateVerifier(): string {
  return b64url(crypto.getRandomValues(new Uint8Array(32)));
}

/** code_challenge = BASE64URL(SHA256(verifier)) (method S256). */
export async function challengeFor(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return b64url(new Uint8Array(digest));
}
