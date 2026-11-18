import { validate } from '@/lib/validation';

const ok = { firstName: 'Jane', lastName: 'Doe', email: 'jane@example.com', phoneNo: '1234567890', password: 'correct horse battery' };

describe('validate', () => {
  it('accepts valid input', () => {
    expect(validate(ok)).toEqual({});
  });

  it('requires first and last name', () => {
    expect(validate({ ...ok, firstName: '' }).firstName).toBeDefined();
  });

  it('firstName: 25 chars ok, 26 rejected', () => {
    expect(validate({ ...ok, firstName: 'a'.repeat(25) }).firstName).toBeUndefined();
    expect(validate({ ...ok, firstName: 'a'.repeat(26) }).firstName).toMatch(/25/);
  });

  it.each(['', 'abc', 'a@b', 'a@b.', '@b.com', 'a b@c.com', 'a@b.c'])('rejects email %j', (email) => {
    expect(validate({ ...ok, email }).email).toBeDefined();
  });

  it.each(['a@b.com', 'first.last@sub.domain.org'])('accepts email %s', (email) => {
    expect(validate({ ...ok, email }).email).toBeUndefined();
  });

  it.each(['', '123456789', '1234567890123', '12345abcde', '+1234567890', '123 456 7890'])(
    'rejects phone %j',
    (phoneNo) => {
      expect(validate({ ...ok, phoneNo }).phoneNo).toBeDefined();
    },
  );

  it.each(['1234567890', '12345678901', '123456789012'])('accepts phone %s', (phoneNo) => {
    expect(validate({ ...ok, phoneNo }).phoneNo).toBeUndefined();
  });

  it('rejects digits in the name', () => {
    expect(validate({ ...ok, lastName: 'Doe2' }).lastName).toMatch(/digits/);
  });

  it('password: required, 12-28 code points, not trimmed', () => {
    expect(validate({ ...ok, password: '' }).password).toMatch(/required/);
    expect(validate({ ...ok, password: 'a'.repeat(11) }).password).toBeDefined();
    expect(validate({ ...ok, password: 'a'.repeat(12) }).password).toBeUndefined();
    expect(validate({ ...ok, password: 'a'.repeat(28) }).password).toBeUndefined();
    expect(validate({ ...ok, password: 'a'.repeat(29) }).password).toBeDefined();
    expect(validate({ ...ok, password: ' '.repeat(12) }).password).toBeUndefined();
  });
});
