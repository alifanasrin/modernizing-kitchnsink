import { render, screen } from '@testing-library/react';

vi.mock('server-only', () => ({}));
const get = vi.fn();
const firstNameFor = vi.fn();
vi.mock('next/headers', () => ({ cookies: async () => ({ get }) }));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`NEXT_REDIRECT ${to}`);
  },
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element
  default: ({ src, alt }: { src: string; alt: string }) => <img src={src} alt={alt} />,
}));
vi.mock('@/lib/profile', () => ({ firstNameFor: (t: string) => firstNameFor(t) }));

import Page from '@/app/home/page';

beforeEach(() => {
  get.mockReset();
  firstNameFor.mockReset();
});
const show = async (sp = {}) => render(await Page({ searchParams: Promise.resolve(sp) }));

describe('home page', () => {
  it('greets the member by first name', async () => {
    get.mockReturnValue({ value: 'TOKEN' });
    firstNameFor.mockResolvedValue('Jane');
    await show();
    expect(firstNameFor).toHaveBeenCalledWith('TOKEN');
    expect(screen.getByRole('heading', { name: 'Hello, Jane!' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /meet the members/i })).toHaveAttribute('href', '/members');
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });

  it('falls back to a generic greeting when no name is available', async () => {
    get.mockReturnValue({ value: 'TOKEN' });
    firstNameFor.mockResolvedValue(null);
    await show();
    expect(screen.getByRole('heading', { name: 'Hello, traveller!' })).toBeInTheDocument();
  });

  it('re-authenticates without a token', async () => {
    get.mockReturnValue(undefined);
    await expect(show()).rejects.toThrow('NEXT_REDIRECT /api/auth/refresh?next=%2Fhome');
  });
});
