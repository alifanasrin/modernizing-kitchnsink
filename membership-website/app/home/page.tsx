import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import Link from 'next/link';
import { logger } from '@/lib/logger';
import { redirect } from 'next/navigation';
import MemberShell from '../components/MemberShell';
import { ACCESS_COOKIE } from '@/lib/oauth';
import { firstNameFor } from '@/lib/profile';
import { reauthTarget } from '@/lib/session';

export const metadata: Metadata = { title: 'Home', description: 'Your Backpack home.' };

// Landing page after sign-in.
export default async function HomePage({ searchParams }: { searchParams: Promise<{ _r?: string }> }) {
  const sp = await searchParams;
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) {
    logger.info('home: no access token, re-authenticating');
    redirect(reauthTarget('/home', sp._r === '1'));
  }

  const firstName = await firstNameFor(token);

  return (
    <MemberShell>
      <p className="hero-kicker">Member lounge</p>
      <h1>{firstName ? `Hello, ${firstName}!` : 'Hello, traveller!'}</h1>
      <p className="hero-sub">Welcome back to Backpack. Your next adventure starts here.</p>
      <div className="home-grid">
        <Link href="/members" className="home-tile">
          <span className="home-tile-title">Meet the members</span>
          <span className="home-tile-text">See who else is part of the Backpack community.</span>
          <span className="home-tile-go" aria-hidden="true">→</span>
        </Link>
      </div>
    </MemberShell>
  );
}
