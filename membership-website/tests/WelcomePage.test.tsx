import { render, screen } from '@testing-library/react';

const get = vi.fn();
const redirect = vi.fn((to: string) => {
  throw new Error(`NEXT_REDIRECT ${to}`);
});
vi.mock('next/headers', () => ({ cookies: async () => ({ get }) }));
vi.mock('next/navigation', () => ({ redirect: (to: string) => redirect(to) }));
vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element
  default: ({ src, alt }: { src: string; alt: string }) => <img src={src} alt={alt} />,
}));

import Page from '@/app/page';

beforeEach(() => {
  get.mockReset();
  redirect.mockClear();
});

const show = async (sp: { error?: string } = {}) => render(await Page({ searchParams: Promise.resolve(sp) }));

describe('welcome page', () => {
  it('shows the welcome copy, a picture, and Login / Register links - no forms', async () => {
    await show();
    expect(screen.getByRole('heading', { name: /welcome to the world of backpack/i })).toBeInTheDocument();
    expect(screen.getByText(/register with us for world-class experiences/i)).toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('src', '/hero.jpg');
    expect(document.querySelector('form, input')).toBeNull();
  });

  it('Login starts the authorize call', async () => {
    await show();
    expect(screen.getByRole('link', { name: 'Login' })).toHaveAttribute('href', '/api/oauth/start');
  });

  it('Register goes to the auth service register page', async () => {
    await show();
    expect(screen.getByRole('link', { name: 'Register' })).toHaveAttribute('href', 'http://localhost:8080/register');
  });

  it('shows a clean message for a known error and a generic one otherwise', async () => {
    await show({ error: 'unreachable' });
    expect(screen.getByRole('alert')).toHaveTextContent(/could not be reached/i);
  });

  it('generic message for an unknown error code (never echoes it)', async () => {
    await show({ error: '<b>x</b>' });
    expect(screen.getByRole('alert')).toHaveTextContent('Sign-in failed. Please try again.');
  });

  it('signed-in users go to /home', async () => {
    get.mockReturnValue({ value: 'AT' });
    await expect(show()).rejects.toThrow('NEXT_REDIRECT /home');
  });
});
