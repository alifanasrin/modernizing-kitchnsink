import MemberShell from '../components/MemberShell';

export default function Loading() {
  return (
    <MemberShell wide>
      <p className="hero-kicker">Community</p>
      <h1>Members</h1>
      <p className="hero-sub" role="status">Loading members…</p>
    </MemberShell>
  );
}
