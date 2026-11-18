import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginForm from '@/app/LoginForm';

const navigate = vi.fn();

const fetchMock = vi.fn();
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });

beforeEach(() => {
  fetchMock.mockReset();
  navigate.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email/i), 'a@b.com');
  await user.type(screen.getByLabelText(/password/i), 'secret');
  await user.click(screen.getByRole('button', { name: /login/i }));
}

describe('LoginForm', () => {
  it('starts with no errors and disabled submit', () => {
    render(<LoginForm navigate={navigate} />);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('button', { name: /login/i })).toBeDisabled();
  });

  it('shows email error until valid', async () => {
    const user = userEvent.setup();
    render(<LoginForm navigate={navigate} />);
    const email = screen.getByLabelText(/email/i);
    await user.type(email, 'abc');
    expect(screen.getByText(/valid email/i)).toBeInTheDocument();
    await user.type(email, '@x.com');
    expect(screen.queryByText(/valid email/i)).toBeNull();
  });

  it('shows password required after clearing', async () => {
    const user = userEvent.setup();
    render(<LoginForm navigate={navigate} />);
    const pw = screen.getByLabelText(/password/i);
    await user.type(pw, 'a');
    await user.clear(pw);
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
  });

  it('posts JSON and navigates to the returned callback URL', async () => {
    fetchMock.mockResolvedValue(json({ redirectTo: 'http://localhost:3000/callback?code=xyz' }));
    const user = userEvent.setup();
    render(<LoginForm navigate={navigate} />);
    await fillAndSubmit(user);
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('http://localhost:3000/callback?code=xyz'));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/login');
    expect(JSON.parse(init.body)).toEqual({ email: 'a@b.com', password: 'secret' });
  });

  it('shows server error message and stays on the page', async () => {
    fetchMock.mockResolvedValue(json({ message: 'Invalid username or password.' }, 401));
    const user = userEvent.setup();
    render(<LoginForm navigate={navigate} />);
    await fillAndSubmit(user);
    expect(await screen.findByText('Invalid username or password.')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });
});
