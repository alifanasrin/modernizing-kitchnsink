import 'server-only';
import { logger } from './logger';
import { ACCESS_COOKIE } from './oauth';
import { cookies } from 'next/headers';

export type Member = { firstName: string; lastName: string; phoneNo: string; email: string };
export type MembersPage = {
  data: Member[];
  pageNo: number;
  pageSize: number;
  totalRecords: number;
  totalPages: number;
};

export class UnauthorizedError extends Error {}
export class PageNotFoundError extends Error {}
export class BadGatewayError extends Error {}

const API_URL = process.env.MEMBERS_API_URL ?? 'http://localhost:8080/members';
export const FIRST_PAGE = Number(process.env.MEMBERS_FIRST_PAGE ?? 1); // set to 0 if backend pages are 0-based
export const PAGE_SIZES = [5, 10, 20, 50];
export const DEFAULT_PAGE_SIZE = 10;

export async function fetchMembers(pageNo: number, pageSize: number): Promise<MembersPage> {
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!token) throw new UnauthorizedError();

  const url = new URL(API_URL);
  url.searchParams.set('pageNo', String(pageNo));
  url.searchParams.set('pageSize', String(pageSize));

  let res: Response;
  try {
    res = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    });
  } catch (e) {
    logger.error('members: backend unreachable', { error: e });
    throw new BadGatewayError(); // backend not reachable
  }
  if (res.status === 401 || res.status === 403) throw new UnauthorizedError();
  if (res.status === 404) throw new PageNotFoundError();
  if (!res.ok) {
    logger.error('members: backend error', { status: res.status });
    throw new Error('Failed to load members');
  }
  logger.debug('members: fetched', { pageNo, pageSize });
  return res.json();
}
