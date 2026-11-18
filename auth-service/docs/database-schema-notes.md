# Database Schema Notes

Explains the diagram in `database-schema.md`.

## Collections and indexes

| Collection | Index | Kind |
|---|---|---|
| `member` | `uk_member_email` on `email` | unique |
| `member` | `idx_member_last_first_name` on `last_name, first_name` | plain, backs the sorted member list |
| `oauth_client` | `uk_oauth_client_id` on `clientId` | unique |
| `oauth_authorization` | `uk_oauth_authorization_request_id` on `requestId` | unique, sparse |
| `oauth_authorization` | `uk_oauth_authorization_code_hash` on `codeHash` | unique, sparse |
| `oauth_authorization` | `ttl_oauth_authorization_expires_at` on `expiresAt` | TTL, removes abandoned and used grants |
| `session` | `idx_session_user_id` on `userId` | plain |
| `session` | `ttl_session_expires_at` on `expiresAt` | TTL |
| `session` | `uk_session_refresh_token_hash` on `refreshTokenHash` | unique, finds the session of a refresh token |

An `oauth_authorization` document gets the `userId` (the member's Mongo `_id`) only when the code is issued, after a correct password (V011 dropped the old `challengeId` index).

## Other stores

- **`admin_users`:** migration V003 creates unique indexes on `email` and `admin_id`, but no entity or code uses the collection.
- **Mongock:** keeps its own change-log and lock collections in the same database to track applied migrations.
- **Valkey:** holds the bucket4j rate-limit buckets (per IP, and per IP + client or session for the token endpoints). They are key-value entries with expiry, not tables.
- **Removed:** the `mfa_challenge` collection (V007) was dropped by V008, which moved the challenge into `member.mfa`; V012 dropped that index and unset the field when MFA was removed. V013 unset `verified`, `email_verification_hash` and `email_verification_expires_at` when email verification was removed.
- **Not stored:** raw passwords, authorization codes and refresh tokens. Only hashes are kept.
- **Member ids:** there is no business `memberId`; members, sessions and grants are linked by the member's Mongo `_id` (`userId` on sessions and grants) and looked up publicly by email. `V014` removed `memberId` and its index, re-keyed sessions, and split `name` into `first_name`/`last_name`.
