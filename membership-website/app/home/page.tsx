import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import Link from 'next/link';
import { logger } from '@/lib/logger';
import { redirect } from 'next/navigation';
import LogoutButton from '../members/LogoutButton';
import { ACCESS_COOKIE } from '@/lib/oauth';
import { reauthTarget } from '@/lib/session';

export const metadata: Metadata = { title: 'Home', description: 'Welcome home.' };

// Dummy landing page after sign-in.
export default async function HomePage({ searchParams }: { searchParams: Promise<{ _r?: string }> }) {
  const sp = await searchParams;
  if (!(await cookies()).get(ACCESS_COOKIE)?.value) {
    logger.info('home: no access token, re-authenticating');
    redirect(reauthTarget('/home', sp._r === '1'));
  }

  return (
    <main style={{ padding: 32 }}>
      <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h1>Home</h1>
        <LogoutButton />
      </header>
      <p>Welcome! You are signed in.</p>
      <p>
        <Link href="/members">View members</Link>
      </p>
    </main>
  );
}
