'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';

export default function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  const onClick = async () => {
    setPending(true);
    try {
      await fetch('/api/auth/logout', { method: 'POST' });
    } catch {
      // Network error: the server may not have revoked the session, but leave the page regardless.
    } finally {
      router.push('/'); // back to the welcome page; the next sign-in starts fresh
      router.refresh();
    }
  };

  return (
    <button type="button" onClick={onClick} disabled={pending} className="btn-secondary">
      {pending ? 'Logging out…' : 'Log out'}
    </button>
  );
}
