import { NodeSDK } from '@opentelemetry/sdk-node';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPLogExporter } from '@opentelemetry/exporter-logs-otlp-http';
import { UndiciInstrumentation } from '@opentelemetry/instrumentation-undici';
import { BatchLogRecordProcessor } from '@opentelemetry/sdk-logs';

// Exporters read the standard env vars: OTEL_EXPORTER_OTLP_ENDPOINT (default http://localhost:4318),
// OTEL_EXPORTER_OTLP_HEADERS, OTEL_SERVICE_NAME, ... Next.js itself emits spans for requests, route
// handlers, rendering and fetch; the SDK exports them together with the logs from lib/logger.ts.
const sdk = new NodeSDK({
  resource: resourceFromAttributes({ [ATTR_SERVICE_NAME]: process.env.OTEL_SERVICE_NAME ?? 'demo-fe' }),
  traceExporter: new OTLPTraceExporter(),
  // Outgoing fetch() calls (to the backend on :8080): creates a client span and injects the W3C `traceparent` header.
  instrumentations: [new UndiciInstrumentation()],
  logRecordProcessors: [new BatchLogRecordProcessor({ exporter: new OTLPLogExporter() })],
});

sdk.start();
process.once('SIGTERM', () => void sdk.shutdown());
