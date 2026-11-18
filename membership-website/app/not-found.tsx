import Link from 'next/link';

export default function NotFound() {
  return (
    <main style={{ padding: 32 }}>
      <h1>404 – Page not found</h1>
      <p>The page you requested does not exist.</p>
      <Link href="/members">Back to members</Link>
    </main>
  );
}
