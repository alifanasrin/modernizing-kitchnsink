'use client';

export default function Error({ reset }: { error: Error; reset: () => void }) {
  return (
    <main style={{ padding: 32 }}>
      <p>Something went wrong.</p>
      <button onClick={reset}>Try again</button>
    </main>
  );
}
