export type RegisterInput = { firstName: string; lastName: string; email: string; phoneNo: string; password: string };
export type FieldErrors = Partial<Record<keyof RegisterInput, string>>;

export const NAME_MAX = 25;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const PHONE_RE = /^\d{10,12}$/;
export const PASSWORD_MIN = 12;
export const PASSWORD_MAX = 28;

// Backend rule: after NFKC normalization, 12-28 code points. Never trimmed.
const passwordLength = (p: string) => [...p.normalize('NFKC')].length;

export function validate(input: RegisterInput): FieldErrors {
  const errors: FieldErrors = {};
  for (const [key, label] of [['firstName', 'First name'], ['lastName', 'Last name']] as const) {
    const v = input[key];
    if (!v) errors[key] = `${label} is required.`;
    else if (v.length > NAME_MAX) errors[key] = `${label} must be at most ${NAME_MAX} characters.`;
    else if (/\d/.test(v)) errors[key] = `${label} must not contain digits.`;
  }
  if (!EMAIL_RE.test(input.email)) errors.email = 'Enter a valid email address.';
  if (!PHONE_RE.test(input.phoneNo)) errors.phoneNo = 'Phone number must be 10-12 digits only.';
  const len = passwordLength(input.password);
  if (!input.password) errors.password = 'Password is required.';
  else if (len < PASSWORD_MIN || len > PASSWORD_MAX)
    errors.password = `Password must be ${PASSWORD_MIN}-${PASSWORD_MAX} characters.`;
  return errors;
}

export type LoginInput = { email: string; password: string };
export type LoginErrors = Partial<Record<keyof LoginInput, string>>;

export function validateLogin(input: LoginInput): LoginErrors {
  const errors: LoginErrors = {};
  if (!EMAIL_RE.test(input.email)) errors.email = 'Enter a valid email address.';
  if (!input.password) errors.password = 'Password is required.';
  return errors;
}
