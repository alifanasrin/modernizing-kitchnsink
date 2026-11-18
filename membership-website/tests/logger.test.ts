import { describe, it, expect, vi } from 'vitest';
import { logger } from '@/lib/logger';

describe('logger', () => {
  it('writes structured JSON to the console and redacts sensitive keys', () => {
    const spy = vi.spyOn(console, 'info').mockImplementation(() => {});
    logger.info('hello', { status: 200, accessToken: 'abc', code: '123456' });
    const line = JSON.parse(spy.mock.calls[0][0]);
    expect(line).toEqual({ level: 'info', message: 'hello', status: 200, accessToken: '[redacted]', code: '[redacted]' });
    spy.mockRestore();
  });

  it('serialises Error attributes to their message', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    logger.error('boom', { error: new Error('ECONNREFUSED') });
    expect(JSON.parse(spy.mock.calls[0][0]).error).toBe('ECONNREFUSED');
    spy.mockRestore();
  });
});

describe('logger trace context', () => {
  it('adds trace_id and span_id when a span is active', async () => {
    const { context, trace } = await import('@opentelemetry/api');
    const { AsyncLocalStorageContextManager } = await import('@opentelemetry/context-async-hooks');
    context.setGlobalContextManager(new AsyncLocalStorageContextManager().enable());
    const sc = { traceId: '0af7651916cd43dd8448eb211c80319c', spanId: 'b7ad6b7169203331', traceFlags: 1 };
    const spy = vi.spyOn(console, 'info').mockImplementation(() => {});
    context.with(trace.setSpanContext(context.active(), sc), () => logger.info('in span'));
    const line = JSON.parse(spy.mock.calls[0][0]);
    expect(line.trace_id).toBe(sc.traceId);
    expect(line.span_id).toBe(sc.spanId);
    spy.mockRestore();
    context.disable();
  });
});
