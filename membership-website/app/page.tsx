import type { Metadata } from 'next';
import Image from 'next/image';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { ACCESS_COOKIE, oauth } from '@/lib/oauth';

export const metadata: Metadata = {
  title: 'Welcome to the world of Backpack',
  description: 'Register with us for world-class travel experiences.',
};

const ERRORS: Record<string, string> = {
  unreachable: 'The sign-in service could not be reached. Please try again later.',
  authorize_failed: 'Sign-in could not be started. Please try again.',
  token_failed: 'Sign-in could not be completed. Please try again.',
};

// Welcome page. Login and registration are hosted by the auth service: "Login" starts an authorize call
// (/api/oauth/start), "Register" links to the auth service's register page.
export default async function WelcomePage({ searchParams }: { searchParams: Promise<{ error?: string | string[] }> }) {
  if ((await cookies()).get(ACCESS_COOKIE)?.value) redirect('/home');

  const { error } = await searchParams;
  const code = Array.isArray(error) ? error[0] : error;

  return (
    <main className="hero">
      <Image src="/hero.jpg" alt="A traveller with a backpack looking out over mountains" fill priority sizes="100vw" className="hero-img" />
      <div className="hero-shade" />
      <div className="hero-content">
        <p className="hero-kicker">Backpack Membership</p>
        <h1>Welcome to the world of Backpack</h1>
        <p className="hero-sub">Register with us for world-class experiences.</p>
        {code && (
          <p role="alert" className="notice notice-error">
            {ERRORS[code] ?? 'Sign-in failed. Please try again.'}
          </p>
        )}
        <div className="hero-actions">
          {/* Plain anchors: both are navigations away from this app (a route handler / the auth service). */}
          <a href="/api/oauth/start" className="btn-primary hero-btn">
            Login
          </a>
          <a href={oauth.registerPageUrl} className="btn-secondary hero-btn">
            Register
          </a>
        </div>
      </div>
    </main>
  );
}
