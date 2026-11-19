# CLAUDE.md

Guidance for Claude Code (claude.ai/code) in this repository.

## Overview

`member-service`: Spring Boot 4.1.2 / Java 21 service with the read-only member REST APIs and a Kafka consumer that creates members, split out of `../auth-service`. Base package `kitchen.sink.member`, port 8081 (`SERVER_PORT`). Build with Maven (`mvn test`, `mvn spring-boot:run`).

## Architecture

`controller` → `service` (interface in `service/`, implementation in `service/impl/`) → `repository` → `entity`. Constructor injection, package-private controller methods, records for DTOs.

- `GET /members?pageNo=&pageSize=` returns a `PaginatedResponse<MemberResponse>`. `pageNo` is 1-based (default 1, `@Min(1)`), `pageSize` default 10 (`@Min(1) @Max(50)`). Sorted by last name, then first name.
- `GET /members/{email}` returns one member (the path variable is `@Email`, normalized by `util/EmailNormalizer`) or 404 via `MemberNotFoundException`.
- Errors are handled in `exception/GlobalExceptionHandler` as `ErrorResponse` (status, message, timestamp); 401/403 from the filter chain use the same shape (`security/RestAuthenticationHandlers`).

## Security

Every endpoint needs `Authorization: Bearer <jwt>`, except `/actuator/health`. The JWT is the RS256 access token issued by `../auth-service`; this service never signs tokens and holds only the public key.

- `config/JwtConfig` builds the decoder from `app.security.jwt.public-key` (the PEM *contents* of `JWT_PUBLIC_KEY`, same value as in auth-service; no default, the app will not start without a valid key of at least 2048 bits). It checks signature, expiry, `iss` = `app.security.jwt.issuer` (`JWT_ISSUER`) and that `aud` contains `app.security.jwt.audience` (`JWT_AUDIENCE`, default `member-service`).
- So every `oauth_client` in auth-service must have `member-service` in its `aud` list to call this service, in addition to auth-service's own audience.
- `SecurityConfig` is stateless (no session, CSRF off); there are no scopes or roles, any valid token for this audience may read members.

## Data

Owns its own MongoDB database (`MONGODB_URI`, `MONGODB_DATABASE`, default `member-db`; not auth-service's `auth-db`). Indexes come from annotations on `entity/Member` (`spring.data.mongodb.auto-index-creation`): unique `uk_member_email` and `idx_member_last_first_name` for the sorted list. `Member` has no `password_hash`. `MemberRepository` extends the marker `Repository` (no save or delete), so the REST side cannot write.

## Member creation from Kafka

Every registration in auth-service publishes a `MEMBER_REGISTERED` event (contract in `event/MemberRegisteredEvent`, same as auth-service's `MemberRegisteredMessage`; any `1.x` is accepted, unknown fields ignored). Flow: `listener/MemberRegisteredListener` (`@KafkaListener`, String payload, group `member-service`, offset committed per record) -> `MemberRegistrationService.process` -> `MemberEventParser` (JSON + Bean Validation + type/version check; error messages never contain values) -> `MemberStore.createIfAbsent` (`MongoMemberStore`: one atomic `setOnInsert` upsert by normalized email, so redelivery or a duplicate event changes nothing and an existing member is never overwritten). `MemberStore` is the only write path.

`config/KafkaConfig` declares the topic and its `.DLT`, and a `DefaultErrorHandler`: transient failures (e.g. MongoDB down) are retried with exponential backoff (`app.retry.*`), `InvalidMemberEventException` is never retried, and the record is then published to the DLT (`MEMBER_REGISTERED_TOPIC` + `.DLT`). Retries block the consumer thread. Broker: `KAFKA_BOOTSTRAP_SERVERS`. Never log event data (PII), only ids.

## Tests

`MemberControllerTest` (`@WebMvcTest`, filters off), `MemberServiceImplTest` (Mockito), `SecurityConfigTest` (real filter chain and decoder, tokens signed by `TestTokens` with the throwaway key pair in `src/test/resources/test-keys`, public key in `jwt-test.properties`), `JwtConfigTest`, `MemberServiceApplicationTests` (full context, no MongoDB or Kafka needed: `src/test/resources/application.properties` turns off listener start and topic creation), `MemberEventParserImplTest`, `MemberRegistrationServiceImplTest`, `MongoMemberStoreTest`, and `MemberRegisteredKafkaIntegrationTest` (`@EmbeddedKafka`: a valid event reaches the store, a malformed one lands on the DLT unretried).

`src/main/resources/static/openapi.yaml` documents the APIs (served at `/openapi.yaml`, authenticated like everything else).
