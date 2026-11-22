# CLAUDE.md

Guidance for Claude Code (claude.ai/code) in this repository.

## Overview

`member-service`: Spring Boot 4.1.2 / Java 21 service with the read-only member REST APIs and `POST /members`, which auth-service calls to create members, split out of `../auth-service`. Base package `kitchen.sink.member`, port 8081 (`SERVER_PORT`). Build with Maven (`mvn test`, `mvn spring-boot:run`).

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

Owns its own MongoDB database (`MONGODB_URI`, `MONGODB_DATABASE`, default `member-db`; not auth-service's `auth-db`). Indexes come from annotations on `entity/Member` (`spring.data.mongodb.auto-index-creation`): unique `uk_member_email` and `idx_member_last_first_name` for the sorted list. `Member` has no `password_hash`. `MemberRepository` extends `MongoRepository`; the REST controllers only call the read methods, and the only write is `MemberService.createMember`, called by `POST /members`.

## Member creation (POST /members)

After a registration commits, auth-service calls `POST /members` (`MemberController#createMember`, body `CreateMemberRequest`: `email`, `phoneNo`, `firstName`, optional `lastName`, validated like auth-service's registration; unknown/extra fields are not needed, no password ever). The email is normalized, then `MemberService.createMember` (`MemberRepository.save`; a duplicate email hits the unique `uk_member_email` index, the `DuplicateKeyException` is caught and returned as `false`) -> 201 with the `MemberResponse`, or 409 `MemberAlreadyExistsException`. `createMember` is the only write path. Never log request data (PII).

**Security note (temporary):** `SecurityConfig` permits `POST /members` without a token only because auth-service has no service-client token yet. It must not be exposed publicly (no gateway or ingress route), only reachable from the internal network. Future scope: introduce service clients (backend services with their own identity, OAuth 2 client-credentials grant) and allow this API only to them, with a dedicated permission such as `members:create` held by auth-service, so nobody can create a member without registering. The same note is on `MemberController#createMember`. Kafka is no longer used (the consumer, DLT and `KafkaConfig` were removed).

## Tests

`MemberControllerTest` (`@WebMvcTest`, filters off), `MemberServiceImplTest` (Mockito), `SecurityConfigTest` (real filter chain and decoder, tokens signed by `TestTokens` with the throwaway key pair in `src/test/resources/test-keys`, public key in `jwt-test.properties`), `JwtConfigTest`, `MemberServiceApplicationTests` (full context; it needs a reachable MongoDB, as before).

`src/main/resources/static/openapi.yaml` documents the APIs (served at `/openapi.yaml`, authenticated like everything else).
