import { render, screen } from '@testing-library/react';

vi.mock('server-only', () => ({}));
const fetchMembers = vi.fn();
vi.mock('@/lib/members', async (orig) => ({ ...(await orig<typeof import('@/lib/members')>()), fetchMembers: (...a: unknown[]) => fetchMembers(...a) }));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`NEXT_REDIRECT ${to}`);
  },
  notFound: () => {
    throw new Error('NEXT_NOT_FOUND');
  },
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));
vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element
  default: ({ src, alt }: { src: string; alt: string }) => <img src={src} alt={alt} />,
}));

import Page from '@/app/members/page';
import { BadGatewayError, UnauthorizedError } from '@/lib/members';

const show = async () => render(await Page({ searchParams: Promise.resolve({}) }));

describe('members page', () => {
  it('uses the same shell as home (brand, nav, logout) and lists members', async () => {
    fetchMembers.mockResolvedValue({
      data: [{ firstName: 'Jane', lastName: 'Doe', phoneNo: '1234567890', email: 'jane@example.com' }],
      pageNo: 1, pageSize: 10, totalRecords: 1, totalPages: 1,
    });
    await show();
    expect(screen.getByRole('link', { name: 'Backpack' })).toHaveAttribute('href', '/home');
    expect(screen.getByRole('link', { name: 'Home' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Members' })).toBeInTheDocument();
    expect(screen.getByText('jane@example.com')).toBeInTheDocument();
  });

  it('shows the bad gateway notice inside the shell', async () => {
    fetchMembers.mockRejectedValue(new BadGatewayError());
    await show();
    expect(screen.getByRole('heading', { name: /502/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });

  it('re-authenticates when unauthorised', async () => {
    fetchMembers.mockRejectedValue(new UnauthorizedError());
    await expect(show()).rejects.toThrow('NEXT_REDIRECT /api/auth/refresh?next=%2Fmembers');
  });
});
