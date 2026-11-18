import type { Metadata } from 'next';
import Link from 'next/link';
import { cookies } from 'next/headers';
import { logger } from '@/lib/logger';
import { redirect } from 'next/navigation';
import { ACCESS_COOKIE } from '@/lib/oauth';
import RegisterForm from './RegisterForm';

export const metadata: Metadata = { title: 'Register', description: 'Create an account.' };

export default async function Page() {
  // Already signed in: nothing to do here.
  if ((await cookies()).get(ACCESS_COOKIE)?.value) {
    logger.debug('register page: already signed in, redirecting to /home');
    redirect('/home');
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <h1>Create account</h1>
        <p className="auth-subtitle">Join us in a few quick steps.</p>
        <RegisterForm />
        <div className="auth-footer">
          <p>
            Already a member? <Link href="/login">Login</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
