'use client';

import MemberShell from '../components/MemberShell';

export default function MembersError({ reset }: { error: Error; reset: () => void }) {
  return (
    <MemberShell>
      <p role="alert" className="notice notice-error">Could not load members. Please try again.</p>
      <div>
        <button className="btn-primary hero-btn" onClick={reset}>Retry</button>
      </div>
    </MemberShell>
  );
}
