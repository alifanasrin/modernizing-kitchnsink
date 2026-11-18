import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = { title: 'Invalid client', description: 'This application is not allowed to sign you in.' };

export default function InvalidClientPage() {
  return (
    <main style={{ padding: 32 }}>
      <h1>Invalid client</h1>
      <p role="alert" style={{ color: 'crimson' }}>
        This application is not registered or is misconfigured, so sign-in cannot continue.
      </p>
      <p>Please contact support if this keeps happening.</p>
      <Link href="/">Back to home</Link>
    </main>
  );
}
