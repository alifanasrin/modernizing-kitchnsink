import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import PageSizeSelect from './PageSizeSelect';
import MemberShell from '../components/MemberShell';
import { logger } from '@/lib/logger';
import { reauthTarget } from '@/lib/session';
import { BadGatewayError, DEFAULT_PAGE_SIZE, FIRST_PAGE, PAGE_SIZES, PageNotFoundError, UnauthorizedError, fetchMembers } from '@/lib/members';

export const metadata: Metadata = { title: 'Members', description: 'List of members.' };

const toInt = (v: string | string[] | undefined, fallback: number, min: number, max = Infinity) => {
  const n = Number(Array.isArray(v) ? v[0] : v);
  return Number.isInteger(n) && n >= min && n <= max ? n : fallback;
};

export default async function MembersPage({
  searchParams,
}: {
  searchParams: Promise<{ pageNo?: string; pageSize?: string; _r?: string }>;
}) {
  const sp = await searchParams;
  const reqPageNo = toInt(sp.pageNo, FIRST_PAGE, FIRST_PAGE);
  const reqPageSize = toInt(sp.pageSize, DEFAULT_PAGE_SIZE, 1, 100);

  let result;
  try {
    result = await fetchMembers(reqPageNo, reqPageSize);
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      logger.info('members: not authorised, re-authenticating', { justRefreshed: sp._r === '1' });
      redirect(reauthTarget('/members', sp._r === '1'));
    }
    if (e instanceof PageNotFoundError) {
      logger.warn('members: page not found', { pageNo: reqPageNo, pageSize: reqPageSize });
      notFound();
    }
    if (e instanceof BadGatewayError) {
      return (
        <MemberShell>
          <h1>502 – Bad Gateway</h1>
          <p className="hero-sub">The backend server could not be reached. Please try again later.</p>
          <p>
            <Link href="/members" className="btn-secondary hero-btn">
              Retry
            </Link>
          </p>
        </MemberShell>
      );
    }
    throw e;
  }

  const { data, pageNo, pageSize, totalRecords, totalPages } = result;
  const lastPage = FIRST_PAGE + Math.max(totalPages, 1) - 1;
  if (reqPageNo > lastPage) notFound();
  const href = (p: number, size = pageSize) => `/members?pageNo=${p}&pageSize=${size}`;
  const hasPrev = pageNo > FIRST_PAGE;
  const hasNext = pageNo < lastPage;

  return (
    <MemberShell wide>
      <p className="hero-kicker">Community</p>
      <h1>Members</h1>
      <div className="members-card">
        {data.length === 0 ? (
          <p className="empty">No members found.</p>
        ) : (
          <div className="table-wrap">
            <table className="members-table">
              <thead>
                <tr>
                  {['First name', 'Last name', 'Phone', 'Email'].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.map((m) => (
                  <tr key={m.email}>
                    <td>{m.firstName}</td>
                    <td>{m.lastName}</td>
                    <td>{m.phoneNo}</td>
                    <td>{m.email}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <nav aria-label="Pagination" className="pagination">
        {hasPrev ? <Link href={href(FIRST_PAGE)}>« First</Link> : <span className="dim">« First</span>}
        {hasPrev ? <Link href={href(pageNo - 1)}>‹ Previous</Link> : <span className="dim">‹ Previous</span>}
        <span className="page-info">
          Page {pageNo - FIRST_PAGE + 1} of {Math.max(totalPages, 1)}
        </span>
        {hasNext ? <Link href={href(pageNo + 1)}>Next ›</Link> : <span className="dim">Next ›</span>}
        {hasNext ? <Link href={href(lastPage)}>Last »</Link> : <span className="dim">Last »</span>}
        <span className="push">
          <PageSizeSelect sizes={PAGE_SIZES} current={pageSize} firstPage={FIRST_PAGE} />
        </span>
      </nav>
    </MemberShell>
  );
}

