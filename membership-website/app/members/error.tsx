'use client';

export default function MembersError({ reset }: { error: Error; reset: () => void }) {
  return (
    <main className="auth-page">
      <div className="auth-card">
        <p role="alert" className="notice notice-error">Could not load members. Please try again.</p>
        <div className="auth-form" style={{ marginTop: 16 }}>
          <button className="btn-primary" onClick={reset}>Retry</button>
        </div>
      </div>
    </main>
  );
}
