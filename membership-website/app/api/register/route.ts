import { logger } from '@/lib/logger';
import { rateLimit, rejectUnsafeRequest } from '@/lib/security';
import { NextResponse } from 'next/server';
import { validate, type RegisterInput } from '@/lib/validation';

const API_URL = process.env.REGISTER_API_URL ?? 'http://localhost:8080/register';

export async function POST(req: Request) {
  const blocked = rejectUnsafeRequest(req) ?? rateLimit(req, 'register', 5);
  if (blocked) return blocked;

  let body: Partial<RegisterInput>;
  try {
    body = await req.json();
  } catch {
    logger.warn('register: body is not JSON');
    return NextResponse.json({ message: 'Request body must be JSON.' }, { status: 400 });
  }

  const input: RegisterInput = {
    firstName: String(body.firstName ?? '').trim(),
    lastName: String(body.lastName ?? '').trim(),
    email: String(body.email ?? '').trim(),
    phoneNo: String(body.phoneNo ?? '').trim(),
    password: String(body.password ?? ''), // never trimmed
  };
  const errors = validate(input);
  if (Object.keys(errors).length > 0) {
    logger.warn('register: validation failed', { fields: Object.keys(errors).join(',') });
    return NextResponse.json({ message: 'Validation failed.', errors }, { status: 400 });
  }

  try {
    const res = await fetch(API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
      cache: 'no-store',
    });
    if (!res.ok) {
      (res.status >= 500 ? logger.error : logger.warn)('register: backend rejected registration', { status: res.status });
      return NextResponse.json({ message: await friendlyMessage(res) }, { status: res.status });
    }
    logger.info('register: member registered');
    return NextResponse.json({ message: 'Registration successful!' });
  } catch (e) {
    logger.error('register: backend unreachable', { error: e });
    return NextResponse.json(
      { message: 'Bad Gateway: the backend server could not be reached. Please try again later.' },
      { status: 502 },
    );
  }
}

// Never forward the raw backend body; extract a readable message only.
async function friendlyMessage(res: Response): Promise<string> {
  const fallback =
    res.status >= 500
      ? 'Something went wrong on our side. Please try again later.'
      : res.status === 409
        ? 'A member with this email already exists.'
        : 'Registration failed. Please check your details.';
  try {
    const data = await res.json();
    const raw = typeof data?.message === 'string' ? data.message : '';
    if (res.status >= 500 || !raw) return fallback;
    // Strip a leading "field: " prefix, e.g. "phoneNo: Size must be..."
    return raw.replace(/^\w+:\s*/, '');
  } catch {
    return fallback;
  }
}
