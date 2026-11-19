import 'server-only';

/**
 * Where to send the user when the access token is missing/rejected: one silent refresh attempt, otherwise a new authorize call (the auth service hosts the login page).
 * The refresh-token cookie is scoped to /api/auth, so pages cannot see it; the refresh route checks it and falls
 * back to the sign-in start (/api/oauth/start) itself. `justRefreshed` (the `_r` flag) prevents a refresh loop. Both carry `state` = this page.
 */
export function reauthTarget(currentPath: string, justRefreshed: boolean): string {
  const state = encodeURIComponent(currentPath);
  return justRefreshed ? `/api/oauth/start?state=${state}` : `/api/auth/refresh?next=${state}`;
}
