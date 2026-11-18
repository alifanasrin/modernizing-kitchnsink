import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Register',
  description: 'Create an account with your first and last name, email and phone number.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
