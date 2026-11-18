import { context, trace } from '@opentelemetry/api';
import { logs, SeverityNumber, type AnyValueMap } from '@opentelemetry/api-logs';

// Structured logging through the OpenTelemetry Logs API. Records are exported by the SDK started in
// instrumentation.node.ts and are automatically linked to the active trace/span. Without an SDK the
// API is a no-op, so this is always safe to call. Each record is also written to the console.
// NEVER pass tokens, passwords, codes or cookie values as attributes: sensitive keys are redacted below.
const otel = logs.getLogger('demo-fe');

const SENSITIVE = /token|password|secret|code|cookie|authorization|challenge|verifier/i;

function clean(attrs: Record<string, unknown> = {}): AnyValueMap {
  const out: AnyValueMap = {};
  for (const [k, v] of Object.entries(attrs)) {
    out[k] = SENSITIVE.test(k) ? '[redacted]' : v instanceof Error ? v.message : (v as AnyValueMap[string]);
  }
  return out;
}

function emit(level: 'debug' | 'info' | 'warn' | 'error', severity: SeverityNumber, message: string, attrs?: Record<string, unknown>) {
  const attributes = clean(attrs);
  // The OTel record picks up the active context itself; the console line needs the ids added by hand.
  const ctx = context.active();
  otel.emit({ severityNumber: severity, severityText: level.toUpperCase(), body: message, attributes, context: ctx });
  const sc = trace.getSpan(ctx)?.spanContext();
  const ids = sc && trace.isSpanContextValid(sc) ? { trace_id: sc.traceId, span_id: sc.spanId } : {};
  console[level](JSON.stringify({ level, message, ...ids, ...attributes }));
}

export const logger = {
  debug: (m: string, a?: Record<string, unknown>) => emit('debug', SeverityNumber.DEBUG, m, a),
  info: (m: string, a?: Record<string, unknown>) => emit('info', SeverityNumber.INFO, m, a),
  warn: (m: string, a?: Record<string, unknown>) => emit('warn', SeverityNumber.WARN, m, a),
  error: (m: string, a?: Record<string, unknown>) => emit('error', SeverityNumber.ERROR, m, a),
};
