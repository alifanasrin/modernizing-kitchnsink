import Image from 'next/image';
import Link from 'next/link';
import LogoutButton from '../members/LogoutButton';

/** Shared look for every signed-in page: travel photo, dark gradient, top bar and a frosted-glass card. */
export default function MemberShell({ children, wide = false }: { children: React.ReactNode; wide?: boolean }) {
  return (
    <main className="home">
      <Image src="/home-bg.jpg" alt="" fill priority sizes="100vw" className="hero-img" />
      <div className="home-shade" />
      <header className="home-bar">
        <Link href="/home" className="home-brand">
          Backpack
        </Link>
        <nav aria-label="Main" className="home-nav">
          <Link href="/home">Home</Link>
          <Link href="/members">Members</Link>
          <LogoutButton />
        </nav>
      </header>
      <section className={wide ? 'home-card home-card-wide' : 'home-card'}>{children}</section>
    </main>
  );
}
