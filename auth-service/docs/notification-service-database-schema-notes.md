# Notification-Service Database Schema Notes

Explains `notification-service-database-schema.mmd`. The service uses one MongoDB database, `notification`, with one collection, `notification_log`. Source: `model/NotificationLog` in the notification-service repo.

## Indexes

| Index | Fields | Kind |
|---|---|---|
| `uk_source_event_id` | `source`, `eventId` | unique, partial (only when both fields exist) |
| `ttl_created_at` | `createdAt` | TTL, `app.log.retention` (default 24 h) |

- The unique pair makes a redelivered event idempotent per producer. A duplicate insert race raises `DuplicateKeyException` and the event is skipped.
- The TTL index deletes records 24 hours after creation. That is also the de-duplication window, so a repeat event after that is processed again.

## Status

`PENDING` moves to `SENT`, `FAILED` or `EXPIRED`. `REJECTED` means the event was never accepted (malformed, unsupported type, channel or version, or bad data).

## What is and isn't stored

- **Stored:** who asked (`source`, `eventId`), what and how (`notificationType`, `channel`), who for (`recipientUserId`, a masked `recipientAddress`), the outcome, and where a dead-lettered record sits on the DLT (`sourceTopic`, `sourcePartition`, `sourceOffset`).
- **Never stored:** the MFA code or any other event `data`.
- `recipientUserId` is the Mongo ObjectId of the member in auth-service's database. It is a reference by value only, as the two databases are separate.
