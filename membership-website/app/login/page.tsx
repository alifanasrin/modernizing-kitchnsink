import type { Metadata } from 'next';
import Link from 'next/link';
import { cookies, headers } from 'next/headers';
import { logger } from '@/lib/logger';
import { redirect } from 'next/navigation';
import { ACCESS_COOKIE, REQUEST_COOKIE, VERIFIER_COOKIE, cleanOrigin } from '@/lib/oauth';
import LoginForm from '../LoginForm';

export const metadata: Metadata = {
  title: 'Login',
  description: 'Log in with your email and password.',
};

const ERRORS: Record<string, string> = {
  unreachable: 'The backend server could not be reached. Please try again later.',
  authorize_failed: 'Sign-in could not be started. Please try again.',
  token_failed: 'Sign-in could not be completed. Please try again.',
};

const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ request_id?: string; error?: string; state?: string }>;
}) {
  const sp = await searchParams;
  const jar = await cookies();
  // Already signed in: skip the sign-in flow entirely.
  if (jar.get(ACCESS_COOKIE)?.value) {
    logger.debug('login page: already signed in, redirecting to /home');
    redirect('/home');
  }
  const requestId = first(sp.request_id);
  const error = first(sp.error);

  // The form is only shown for an authorize call made by this browser (request_id + verifier cookies match).
  // Anything else - no params, a hand-typed or stale request_id - begins a fresh authorize call.
  const inFlow = !!requestId && requestId === jar.get(REQUEST_COOKIE)?.value && !!jar.get(VERIFIER_COOKIE)?.value;
  if (!inFlow && !error) {
    // /login is where every sign-in starts. `state` = where the user came from: an explicit ?state= (set by
    // pages that redirect here) or else the same-site Referer; never /login or /api/oauth/start themselves.
    // An explicit ?state= (even empty, e.g. after logout) wins; the Referer is only used when it is absent.
    const state = sp.state !== undefined ? cleanOrigin(first(sp.state)) : cleanOrigin(await refererPath());
    redirect(state ? `/api/oauth/start?state=${encodeURIComponent(state)}` : '/api/oauth/start');
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <h1>Login</h1>
        <p className="auth-subtitle">Welcome back. Sign in to continue.</p>
        {inFlow ? (
          <LoginForm />
        ) : (
          <p role="alert" className="notice notice-error">
            {ERRORS[error ?? ''] ?? 'Sign-in failed. Please try again.'}
          </p>
        )}
        <div className="auth-footer">
          {/* Plain anchor: this is a route handler that starts a new flow, not a page to prefetch. */}
          <p>
            <a href="/api/oauth/start">{inFlow ? 'Restart sign-in' : 'Try again'}</a>
          </p>
          <p>
            No account? <Link href="/">Register</Link>
          </p>
        </div>
      </div>
    </main>
  );
}

async function refererPath(): Promise<string | null> {
  const h = await headers();
  try {
    const u = new URL(h.get('referer') ?? '');
    return u.host === h.get('host') ? u.pathname + u.search : null;
  } catch {
    return null;
  }
}
