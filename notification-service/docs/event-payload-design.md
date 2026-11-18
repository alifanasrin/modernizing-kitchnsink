# Notification Event Payload – Design

Status: **Draft for review** · Service: `notification-service` · Scope today: MFA codes via email

## 1. Context and constraints

- No REST API for sending. The only entry point is a Kafka consumer.
- Every send attempt is tracked: date/time, success/failure, notification type.
- Today the only use is MFA codes, but the payload must carry any future notification (email now, other channels later) without a breaking change.
- The producer (auth service) owns code generation and expiry. This service only **delivers and records**.

## 2. Design principles

1. **Envelope + payload split.** A stable envelope (identity, routing, tracing) wraps a type-specific `data` map. New notification types add a `notificationType` and template, not new top-level fields.
2. **Content is not in the event.** The event says *what* to send (`notificationType` + `data`); this service owns the template/subject/body. Producers never ship HTML.
3. **Channel is explicit.** `channel` (`EMAIL` now) lets other channels (e.g. PUSH) reuse the same topic and contract later.
4. **Idempotent by `eventId`.** Kafka is at-least-once. A duplicate event must never send a second MFA email.
5. **Time-bound.** `expiresAt` lets the consumer drop a stale MFA code instead of sending a useless one after a delay or retry storm.
6. **Sensitive data minimised.** The MFA code travels in `data` but is never logged and never stored in the tracking table.

## 3. Event contract

Topic: `notification.high-priority` (accepts only `MFA_CODE`, `OTP`, `FORGOT_PASSWORD`, `VERIFY_EMAIL`, `ACCOUNT_BLOCKED`; any other `notificationType` is rejected to the DLT with the reason "not accepted on the high-priority topic") · Key: `recipient.userId` (keeps one user's events ordered on one partition) · Value: JSON (UTF-8)

```json
{
  "eventId": "7b0f6a52-3c1e-4d6b-9a55-2f1c8e0d4a11",
  "eventType": "NOTIFICATION_REQUESTED",
  "schemaVersion": "1.0",
  "occurredAt": "2026-11-03T10:15:30Z",
  "source": "auth-service",
  "correlationId": "c3d1f0aa-8f3e-47c4-bb0e-5a9c1d2e7f90",

  "notification": {
    "channel": "EMAIL",
    "notificationType": "MFA_CODE",
    "priority": "HIGH",
    "expiresAt": "2026-11-03T10:20:30Z",
    "locale": "en-US"
  },

  "recipient": {
    "userId": "u-10293",
    "email": "user@example.com",
    "name": "Asha"
  },

  "data": {
    "code": "482913",
    "validForSeconds": 300
  }
}
```

### Field reference

| Field | Required | Notes |
|---|---|---|
| `eventId` | yes | UUID, unique per request. Idempotency key; unique-indexed in tracking store. |
| `eventType` | yes | Always `NOTIFICATION_REQUESTED` for now. Leaves room for other event kinds on the same schema family. |
| `schemaVersion` | yes | `major.minor`. Consumer rejects unknown major versions to DLT. Additive changes bump minor only. |
| `occurredAt` | yes | ISO-8601 UTC, when the producer created the event. |
| `source` | yes | Producing service name; for audit and debugging. |
| `correlationId` | yes | Trace id from the originating login/request; copied into logs and tracking record. |
| `notification.channel` | yes | `EMAIL` (the only supported channel). Selects the template text and the sender. Anything else goes to the DLT as unsupported. |
| `notification.notificationType` | yes | Enum-like string, maps 1:1 to a template. Today: `MFA_CODE`. |
| `notification.priority` | no | `HIGH` / `NORMAL` / `LOW`. Default `NORMAL`. Reserved for future topic/queue routing; no behaviour yet. |
| `notification.expiresAt` | no, **recommended** | If now > `expiresAt` the event is recorded as `EXPIRED` and not sent. Required for `MFA_CODE`. |
| `notification.locale` | no | BCP-47. Template selection; default `en-US`. |
| `recipient.userId` | yes | Stable user id; also the Kafka key. |
| `recipient.email` | yes for `EMAIL` | Target address. |
| `recipient.name` | no | Greeting only. |
| `data` | yes | Flat map of template variables. Validated per `notificationType` (see below). |

### Notification templates (per-type `data` schemas)

`notification.notificationType` plus `notification.channel` select the template: a channel can have its own text. EMAIL has a subject, greeting and HTML body. The producer picks a template and supplies its `data`; the
service owns the texts (`src/main/resources/templates/<TYPE>/EMAIL/{subject.txt,body.txt,body.html}`). Unknown templates or invalid `data`
are rejected (dead-letter topic) before anything is recorded or sent. Error messages never echo `data` values.

| `notificationType` | Purpose | Required `data` | Optional `data` | Subject |
|---|---|---|---|---|
| `MFA_CODE` | Second login factor | `code` (4-10 digits), `validForSeconds` (> 0) | | Your verification code |
| `OTP` | Generic one-time passcode (e.g. email verification) | `code` (4-10 digits), `validForSeconds` (> 0) | `purpose` (<= 100 chars of letters, digits, spaces and `.,'-`; default "verify your identity") | Your one-time passcode |
| `FORGOT_PASSWORD` | Password reset | `resetLink` (http/https URL), `validForSeconds` (> 0) | | Reset your password |
| `ACCOUNT_BLOCKED` | Warn that the account was blocked after too many wrong passwords and ask to reset the password (EMAIL channel only) | `validForSeconds` (> 0) | | Your account has been blocked |
| `VERIFY_EMAIL` | Confirm a new member's email address (EMAIL channel only) | `verificationLink` (http/https URL), `validForSeconds` (> 0) | | Verify your email address |

Template placeholders: every `data` key, plus `{{name}}` (recipient name, default "there") and `{{minutes}}`
(from `validForSeconds`). Values are substituted in one pass and HTML-escaped in the HTML body.

Adding a template = the EMAIL files (`subject.txt`, `body.txt`, `body.html`), a registration with its `data` fields in `TemplateRenderer`, and a row here. No envelope change.

Example, `MFA_CODE` (code 482913, valid 300 s):

| Channel | Text |
|---|---|
| EMAIL | subject `Your verification code`; body `Hi Ann, Your verification code is: 482913 ...` (plus HTML) |

### Why these choices

- **`data` as a flat map, not typed top-level fields:** keeps the envelope stable; per-type validation lives in one place.
- **Recipient inside the event, not looked up:** this service has no user store and shouldn't call another service just to deliver. Trade-off: the producer must send the current email address.
- **Code in the event vs. generated here:** producer must know the code to verify it later; generating here would force a callback. Cost: the code transits Kafka, so topic access must be locked down and retention short (see §6).

## 4. Tracking record

One row/document per `eventId`, written when the event is consumed and updated with the outcome.

| Field | Notes |
|---|---|
| `id` | internal PK |
| `eventId` | unique; idempotency |
| `correlationId` | from event |
| `channel` | `EMAIL` |
| `notificationType` | `MFA_CODE` |
| `recipientUserId` | from event |
| `recipientAddress` | store masked (`u***@example.com`) – enough to debug, not a full PII copy. *Open question #2.* |
| `status` | `PENDING` → `SENT` \| `FAILED` \| `EXPIRED` |
| `attempts` | int |
| `failureReason` | short code/message on failure (never includes the code or full SMTP body) |
| `requestedAt` | event `occurredAt` |
| `createdAt` | consumed at |
| `sentAt` | set on success (date + time, UTC) |
| `updatedAt` | last transition |

**Never stored:** `data.code` or any other `data` value.

## 5. Processing flow

1. Consume → deserialize → validate envelope and `data` for the type. Invalid → DLT (`notification.high-priority.DLT`), no retry.
2. Insert tracking row (`PENDING`) keyed by `eventId`. Unique-key conflict = duplicate → skip (ack).
3. If `expiresAt` passed → `EXPIRED`, ack.
4. Render template, send via `JavaMailSender`.
5. Success → `SENT` + `sentAt`. Transient failure (SMTP timeout) → bounded retry with backoff, `attempts++`. Exhausted or permanent failure → `FAILED` + `failureReason`, event to DLT.
6. Offsets committed only after the tracking row reaches a terminal state.

Caveat: a crash between "mail sent" and "row marked SENT" can cause a rare duplicate on redelivery. For MFA this is acceptable (user receives two valid codes at worst); an exactly-once guarantee with SMTP is not possible.

## 6. Security notes

- Restrict produce/consume ACLs on `notification.high-priority` to `auth-service` / `notification-service`.
- Short topic retention (e.g. 1 hour) since payloads contain live codes. DLT retention equally short or the DLT message must have `data` redacted before publish.
- Do not log `data`; log `eventId`, `correlationId`, `notificationType`, `status`.
- MFA codes must also be treated as single-use by the producer; this service does not validate them.

## 7. Open questions (need your call before implementation)

1. **Tracking store:** Postgres/MySQL (JPA), MongoDB, or something else? No datastore is configured in the project yet.
2. **Recipient address in tracking:** masked (recommended) or full?
3. **Kafka details:** broker address, topic name (`notification.high-priority` OK?), consumer group id, JSON vs Avro/schema registry (JSON assumed).
4. **SMTP provider:** host/credentials source for `spring-boot-starter-mail`; local dev with MailHog/Mailpit OK?
5. **Retry policy:** 3 retries (after the first attempt) with exponential backoff (1s, 2s, 4s), then DLT. *Confirmed.*

## 8. Next steps (after approval)

1. Add dependencies: `spring-kafka`, persistence starter, validation (+ test deps).
2. Implement event DTOs, `data` validator for `MFA_CODE`, tracking entity/repository.
3. Kafka listener with DLT + retry, mail sender with `MFA_CODE` template.
4. Tests: validation, idempotency, expiry, success/failure tracking.
