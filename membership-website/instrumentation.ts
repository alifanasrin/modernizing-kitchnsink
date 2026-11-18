// Next.js calls register() once per server start. The Node SDK only runs in the Node.js runtime.
export async function register() {
  if (process.env.NEXT_RUNTIME === 'nodejs' && process.env.OTEL_SDK_DISABLED !== 'true') {
    await import('./instrumentation.node');
  }
}
