import 'server-only';
import { logger } from './logger';
import { hosts } from './config';

const MEMBERS_URL = process.env.MEMBERS_API_URL ?? `${hosts.member}/members`;
const NAME_CLAIMS = ['given_name', 'firstName', 'first_name'];

type Claims = Record<string, unknown>;

/**
 * Reads the payload of a JWT WITHOUT verifying it. Only for display (a greeting): the backend verifies the token
 * on every API call, and nothing here grants access.
 */
export function tokenClaims(token: string): Claims {
  try {
    const part = token.split('.')[1];
    if (!part) return {};
    const json = Buffer.from(part.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8');
    const claims = JSON.parse(json);
    return claims && typeof claims === 'object' ? claims : {};
  } catch {
    return {};
  }
}

const clean = (v: unknown) => (typeof v === 'string' && v.trim() ? v.trim().slice(0, 50) : null);

/**
 * First name of the signed-in member: the name claim in the access token if it has one; otherwise (the auth service's
 * token currently carries only the email as `sub`) the member record for that email. null when neither works.
 */
export async function firstNameFor(token: string): Promise<string | null> {
  const claims = tokenClaims(token);
  for (const c of NAME_CLAIMS) {
    const v = clean(claims[c]);
    if (v) return v;
  }
  const email = clean(claims.sub) ?? clean(claims.email);
  if (!email) return null;
  try {
    const res = await fetch(`${MEMBERS_URL}/${encodeURIComponent(email)}`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    });
    if (!res.ok) {
      logger.warn('profile: member lookup failed', { status: res.status });
      return null;
    }
    return clean((await res.json())?.firstName);
  } catch (e) {
    logger.warn('profile: member lookup unreachable', { error: e });
    return null;
  }
}
