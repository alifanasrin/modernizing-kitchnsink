import '@testing-library/jest-dom/vitest';
import { beforeEach } from 'vitest';
import { resetRateLimits } from '@/lib/security';

beforeEach(resetRateLimits); // route tests all share one "unknown" client IP
