'use client';

import { useRouter } from 'next/navigation';

export default function PageSizeSelect({
  sizes,
  current,
  firstPage,
}: {
  sizes: number[];
  current: number;
  firstPage: number;
}) {
  const router = useRouter();
  return (
    <label>
      Page size:{' '}
      <select
        value={current}
        onChange={(e) => router.push(`/members?pageNo=${firstPage}&pageSize=${e.target.value}`)}
      >
        {sizes.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>
    </label>
  );
}
