import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LogoutButton from '@/app/members/LogoutButton';

const push = vi.fn();
const refresh = vi.fn();
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, refresh }) }));

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  push.mockReset();
  refresh.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

describe('LogoutButton', () => {
  it('POSTs /api/auth/logout then goes to /login without a return state', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    const user = userEvent.setup();
    render(<LogoutButton />);
    await user.click(screen.getByRole('button', { name: /log out/i }));
    await waitFor(() => expect(push).toHaveBeenCalledWith('/login?state='));
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/logout', { method: 'POST' });
    expect(refresh).toHaveBeenCalled();
  });

  it('still leaves to /login if the request fails', async () => {
    fetchMock.mockRejectedValue(new Error('down'));
    const user = userEvent.setup();
    render(<LogoutButton />);
    await user.click(screen.getByRole('button', { name: /log out/i }));
    await waitFor(() => expect(push).toHaveBeenCalledWith('/login?state='));
  });
});
