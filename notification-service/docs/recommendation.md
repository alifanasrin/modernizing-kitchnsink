# Production Recommendations – notification-service

Companion to `event-payload-design.md`. Decisions already made: MongoDB for tracking, consumer group `notification-service`, Mailpit for local dev, JSON events.

## 1. Tracking data

- Store the recipient address **masked** (`u***@example.com`) plus `recipientUserId`.
- Add `recipientHash` = HMAC-SHA256(lowercased address, secret key from a secrets manager). Allows "did we mail this address?" searches without readable PII. Use HMAC, not plain SHA-256 (plain hashes of emails are guessable).
- TTL index on the tracking collection (e.g. 90 days).
- Unique index on `eventId` (idempotency).
- Never store the MFA code or any `data` value.

## 2. Retry and failure handling

- 3 retries after the first failed delivery (4 attempts in total), exponential backoff (1s, 2s, 4s); then the event goes to the DLT.
- Retry only transient errors (timeouts, connection errors, 4xx). Fail fast on permanent ones (5xx, invalid address, auth).
- Check `expiresAt` before every attempt; keep total retry time well under the code's validity.
- Use non-blocking retries (Spring Kafka `@RetryableTopic`) so one failing send doesn't stall a partition and delay other users' codes.
- Dead-letter events must have `data` redacted before publishing.
- Circuit breaker around the send call (add once a provider is chosen).
- Metrics: sent / failed / expired counts, send latency. Alert on failure rate and DLT depth.

## 3. Email sending provider

### Design: put the provider behind an interface

```java
public interface EmailSender {
    SendResult send(EmailMessage message) throws NotificationSendException;
}
```

- `EmailMessage`: to, subject, html body, text body, `eventId` (for provider-side tracing).
- `NotificationSendException` carries a `retryable` flag. Each implementation maps its provider's errors to it, so the retry logic never knows which provider is in use.
- Implementations selected by config (`email.provider=smtp|ses|sendgrid|postmark`) via `@ConditionalOnProperty`.
- Always send both HTML and plain-text parts.

### Options

| Option | Use for | Pros | Cons |
|---|---|---|---|
| **SMTP via `JavaMailSender`** (Spring mail starter, already in pom) | Local dev (Mailpit) and as the first prod implementation | No extra dependency; works with any provider that offers SMTP relay; swap by config only | Connection per send unless pooled; coarse error codes; no native bounce/delivery data |
| **Amazon SES** (API via AWS SDK v2, or its SMTP endpoint) | Production if already on AWS | Cheap at volume; IAM auth instead of passwords; SNS bounce/complaint notifications | Account starts in sandbox (needs production-access request); AWS SDK dependency |
| **Postmark** | Production where deliverability of transactional mail is the priority | Transactional-only streams; fast delivery; good bounce and message-activity data | Cost per volume; separate stream setup |
| **SendGrid** | Production if already used in the org | Mature API, webhooks | Shared-IP reputation varies; heavier product |

### Recommendation

1. **Now:** implement `SmtpEmailSender` on `JavaMailSender`. Mailpit locally; the same code works against SES/Postmark/SendGrid SMTP relays in staging.
2. **Production:** use a transactional provider rather than a self-run mail server. If on AWS, **SES** (add an `SesEmailSender` using the API for better error codes). Otherwise **Postmark**.
3. Use a **dedicated transactional stream/sender** (not marketing) so MFA mail isn't affected by bulk-mail reputation.

### Provider-independent requirements

- **Domain authentication:** SPF, DKIM, DMARC on the sending domain. MFA mail landing in spam is an outage.
- **Credentials:** from a secrets manager / environment, never in `application.yaml`.
- **SMTP tuning:** set connect/read/write timeouts (e.g. 5s) so a hung provider fails fast and gets retried; enable STARTTLS.
- **Error mapping:** timeouts, connection errors, 4xx → retryable. 5xx, invalid recipient, auth failure → permanent.
- **Bounces/complaints:** a successful hand-off to the provider is `SENT`, not "delivered". Later, consume provider bounce webhooks (SES→SNS, Postmark webhooks) to add a `BOUNCED` status. Not required for the first version.
- **Rate limits:** respect provider send-rate caps; the Kafka consumer concurrency is the throttle.
- **Config:** `From` address and display name configurable; one `Reply-To` of `no-reply@`.

## 4. Kafka

- TLS + SASL; ACLs: only `auth-service` produces, only `notification-service` consumes.
- Short topic retention (~1h) since events hold live codes.
- Topics `notification.high-priority` and its DLT created by the service (partitions/replication configurable).

## 5. Phasing

| Phase | Includes |
|---|---|
| Build now | Event DTOs/validation, Kafka listener, Mongo tracking, `EmailSender` + SMTP impl, non-blocking retry + DLT, masked address + HMAC, MFA template, tests |
| Needs infra decisions | Circuit breaker, SES/Postmark implementation, HMAC key management, Kafka TLS/SASL, bounce webhooks |
