import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RegisterForm from '@/app/RegisterForm';

const fetchMock = vi.fn();
beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const PW = 'correct horse battery';
const fill = async (first: string, last: string, email: string, phone: string, pw = PW) => {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/first name/i), first);
  await user.type(screen.getByLabelText(/last name/i), last);
  await user.type(screen.getByLabelText(/email/i), email);
  await user.type(screen.getByLabelText(/phone/i), phone);
  await user.type(screen.getByLabelText(/password/i), pw);
  return user;
};

describe('RegisterForm', () => {
  it('shows no errors initially and disables submit', () => {
    render(<RegisterForm />);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('button', { name: /register/i })).toBeDisabled();
  });

  it('shows email error while typing until valid', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);
    const email = screen.getByLabelText(/email/i);
    await user.type(email, 'abc');
    expect(screen.getByText(/valid email/i)).toBeInTheDocument();
    await user.type(email, '@x.com');
    expect(screen.queryByText(/valid email/i)).toBeNull();
  });

  it('shows phone error until 10-12 digits', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);
    const phone = screen.getByLabelText(/phone/i);
    await user.type(phone, '12345');
    expect(screen.getByText(/10-12 digits/i)).toBeInTheDocument();
    await user.type(phone, '67890');
    expect(screen.queryByText(/10-12 digits/i)).toBeNull();
    await user.type(phone, '123');
    expect(screen.getByText(/10-12 digits/i)).toBeInTheDocument();
  });

  it('shows password error until 12+ characters', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);
    const pw = screen.getByLabelText(/password/i);
    await user.type(pw, 'short');
    expect(screen.getByText(/12-28/i)).toBeInTheDocument();
    await user.type(pw, 'long enough now');
    expect(screen.queryByText(/12-28/i)).toBeNull();
  });

  it('shows first name error past 25 chars until corrected', async () => {
    const user = userEvent.setup();
    render(<RegisterForm />);
    const name = screen.getByLabelText(/first name/i);
    await user.type(name, 'a'.repeat(26));
    expect(screen.getByText(/at most 25/i)).toBeInTheDocument();
    await user.type(name, '{Backspace}');
    expect(screen.queryByText(/at most 25/i)).toBeNull();
  });

  it('submits JSON and shows success message', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'Registration successful!' }), { status: 200 }));
    render(<RegisterForm />);
    const user = await fill('Jane', 'Doe', 'jane@example.com', '1234567890');
    await user.click(screen.getByRole('button', { name: /register/i }));
    await waitFor(() => expect(screen.getByText('Registration successful!')).toBeInTheDocument());
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/register');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ firstName: 'Jane', lastName: 'Doe', email: 'jane@example.com', phoneNo: '1234567890', password: PW });
  });

  it('clears the form after success without showing validation errors', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'Registration successful!' }), { status: 200 }));
    render(<RegisterForm />);
    const user = await fill('Jane', 'Doe', 'jane@example.com', '1234567890');
    await user.click(screen.getByRole('button', { name: /register/i }));
    expect(await screen.findByText('Registration successful!')).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toHaveValue('');
    expect(screen.getByLabelText(/email/i)).toHaveValue('');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('keeps the entered values when registration fails', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'Email already registered' }), { status: 409 }));
    render(<RegisterForm />);
    const user = await fill('Jane', 'Doe', 'jane@example.com', '1234567890');
    await user.click(screen.getByRole('button', { name: /register/i }));
    await screen.findByText('Email already registered');
    expect(screen.getByLabelText(/password/i)).toHaveValue(PW);
  });

  it('shows the server error message, not raw JSON', async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ message: 'Email already registered' }), { status: 409 }));
    render(<RegisterForm />);
    const user = await fill('Jane', 'Doe', 'jane@example.com', '1234567890');
    await user.click(screen.getByRole('button', { name: /register/i }));
    expect(await screen.findByText('Email already registered')).toBeInTheDocument();
  });

  it('shows network error when fetch rejects', async () => {
    fetchMock.mockRejectedValue(new Error('down'));
    render(<RegisterForm />);
    const user = await fill('Jane', 'Doe', 'jane@example.com', '1234567890');
    await user.click(screen.getByRole('button', { name: /register/i }));
    expect(await screen.findByText(/network error/i)).toBeInTheDocument();
  });
});
