# notification-service

Consumes notification requests from Kafka, delivers them, and keeps a record of every attempt. There is **no REST API for sending**: Kafka is the only way in.

It sends **email notifications from templates**: the producer (the auth-service) names a template in `notification.notificationType` and supplies its data. Templates today: `MFA_CODE`, `OTP`, `FORGOT_PASSWORD`, `VERIFY_EMAIL`, `ACCOUNT_BLOCKED`.

> **Email delivery is mocked.** `MockEmailSender` only logs the full email, including the MFA code. Nothing is sent. This service is designed to be **extended to integrate with an email service/provider that actually sends the emails** (see [Extending](#extending)). Do not use the mock outside local development.

## How it works

```
auth-service (demo) ──► Kafka: notification.high-priority ──► notification-service ──► EmailSender (mock: logs the email)
                                                              │                       
                                                              ├─► MongoDB: notification_log (status, sent time, type)
                                                              └─► Kafka: notification.high-priority.DLT (after 3 retries)
```

1. The listener (`NotificationListener`) reads a JSON event, consumer group `notification-service`.
2. `NotificationService` validates it (envelope, schema major version, channel, per-type `data`).
3. A duplicate `eventId` is skipped; an event past `expiresAt` is recorded `EXPIRED` and not sent.
4. The template is rendered (`TemplateRenderer`) and handed to the `EmailSender`.
5. The outcome is saved in `notification_log`: `PENDING` → `SENT` | `FAILED` | `EXPIRED`, with attempts, sent time (UTC), notification type, correlation id and the masked recipient address. The MFA code is never stored or logged by the service itself.

### Failures and the dead-letter topic

A transient send failure is retried **3 times** after the first attempt (waits 1s, 2s, 4s). After that the record is marked `FAILED` and the original event is published to `notification.high-priority.DLT`. Invalid events (malformed, unsupported type/channel/version) and permanent send failures skip the retries and go straight to the DLT. Config: `app.retry.*`.

Note: DLT messages still contain the MFA code. Keep that topic's retention short.

## Templates

| Template | Required `data` | Optional `data` |
|---|---|---|
| `MFA_CODE` | `code`, `validForSeconds` | |
| `OTP` | `code`, `validForSeconds` | `purpose` |
| `FORGOT_PASSWORD` | `resetLink` (http/https), `validForSeconds` | |
| `VERIFY_EMAIL` (EMAIL only) | `verificationLink` (http/https), `validForSeconds` | |
| `ACCOUNT_BLOCKED` (EMAIL only) | `validForSeconds` (how long the warning is still worth sending) | |

A template is chosen by `notificationType` **and `channel`**, and a channel can have its own text. Only EMAIL exists today: subject, greeting and HTML body, in `src/main/resources/templates/<TYPE>/EMAIL/{subject.txt,body.txt,body.html}` with `{{placeholders}}`. The auth-service requests them through typed methods (`NotificationSender.sendMfaCode / sendOtp / sendForgotPassword`, optionally with a `NotificationChannel`). `MFA_CODE` (login) and `VERIFY_EMAIL` (registration) are wired into flows; `OTP` and `FORGOT_PASSWORD` are ready for the flows that need them.

## Event contract

See [`docs/event-payload-design.md`](docs/event-payload-design.md) for the payload, field reference and the reasoning behind it, and [`docs/recommendation.md`](docs/recommendation.md) for production recommendations (providers, retries, security).

## Extending

- **Real email sending:** add an implementation of `EmailSender` (SMTP via `JavaMailSender`, SES, Postmark, SendGrid, or a separate email service) and select it with `app.mail.provider`. Map the provider's errors to `NotificationSendException(retryable = true|false)`; the retry and DLT behaviour then works unchanged. `MockEmailSender` is used when `app.mail.provider=mock` (the default).
- **New template:** add its files under `templates/<TYPE>/EMAIL`, register it with its `data` fields in `TemplateRenderer`, and add a method to the auth-service's `NotificationSender`. The event envelope does not change.
- **New channel (e.g. SMS, push):** add its templates, a sender interface and mock, and a `DeliveryChannel` implementation in `service/impl/`; `NotificationService` does not change.

## Run locally

Requires Java 21, Kafka at `localhost:9092` and MongoDB at `localhost:27017` (`docker compose up -d` starts both).

```bash
./mvnw spring-boot:run
./mvnw test                # includes an embedded-Kafka test for the retry + DLT path
```

| Setting | Default |
|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `MONGODB_URI` | `mongodb://localhost:27017` (database `notification_service`) |
| `app.kafka.topic.requests` | `notification.high-priority` (created at startup, with `.DLT`) |
| `app.retry.max-retries` / `initial-interval` / `multiplier` | `3` / `1s` / `2.0` |
| `app.mail.provider` | `mock` |

The mock email shows up in the application log as `[MOCK EMAIL]`.


## Tracing an event (why was it rejected?)

Every event ends up as one document in `notification_log` (Mongo `notification_service`). Rejected and failed events are recorded too, with the reason.

- **Idempotency key:** the producer's `eventId` is stored as a normal field, not as `_id`. A unique index on (`source`, `eventId`) makes a redelivery skip the event, and two services may reuse the same `eventId` value because `source` is part of the key. Producers should still use UUIDs.
- **Retention:** records are deleted 24 hours after creation (TTL index, `app.log.retention`). That is also the de-duplication window; events themselves expire within minutes.
- `correlationId` / trace ids are not stored, since traces age out.

```javascript
db.notification_log.find({ source: "auth-service", eventId: "<eventId>" })
db.notification_log.find({ status: "REJECTED" }).sort({ createdAt: -1 })
db.notification_log.find({ recipientUserId: "<memberId>" }).sort({ createdAt: -1 })
```

| status | meaning |
|---|---|
| `PENDING` | accepted, delivery in progress |
| `SENT` | delivered |
| `EXPIRED` | past `expiresAt`, not sent |
| `FAILED` | accepted but delivery failed after retries; `failureReason` is the error class |
| `REJECTED` | never accepted: malformed, unsupported type/channel/version, type not allowed on the topic, or invalid `data`; `failureReason` says which |

`sourceTopic`/`sourcePartition`/`sourceOffset` locate the dead-lettered message on `notification.high-priority.DLT`. A malformed payload has no `source`/`eventId`, so its record has only the reason and these coordinates. No `data` (MFA code etc.) is ever stored.
