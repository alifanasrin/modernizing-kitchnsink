import 'server-only';

/**
 * Where to send the user when the access token is missing/rejected: one silent refresh attempt, otherwise login.
 * The refresh-token cookie is scoped to /api/auth, so pages cannot see it; the refresh route checks it and falls
 * back to /login itself. `justRefreshed` (the `_r` flag) prevents a refresh loop. Both carry `state` = this page.
 */
export function reauthTarget(currentPath: string, justRefreshed: boolean): string {
  const state = encodeURIComponent(currentPath);
  return justRefreshed ? `/login?state=${state}` : `/api/auth/refresh?next=${state}`;
}
