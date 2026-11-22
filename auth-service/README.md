# auth-service: Member Registration and Authentication (Spring Boot + MongoDB)

`auth-service` is a migration of the JBoss EAP `kitchensink` quickstart (Jakarta EE, JSF, JPA/relational DB) to Spring Boot 4.1.2 on Java 21, backed by MongoDB.

## Stack

- Java 21, Spring Boot 4.1.2, Maven (wrapper included)
- Spring Web MVC, Bean Validation, Spring Data MongoDB
- Mongock (versioned DB migrations), MapStruct, Lombok
- springdoc OpenAPI / Swagger UI, Spring Boot Actuator
- JUnit 5, Mockito, JaCoCo

## Prerequisites

The service will not start (or log in) unless these are available:

| Requirement | Default | Notes |
|---|---|---|
| JDK 21 | | |
| MongoDB | `mongodb://localhost:27017`, database `membership` (a **replica set** in production, for transactions) | Set `spring.mongodb.uri` in `application.yaml` for another instance |
| Valkey / Redis | `localhost:6379` (`VALKEY_HOST`, `VALKEY_PORT`) | Stores the per-IP rate-limit buckets; if unreachable, requests are rejected with 503 |
| JWT RSA key pair | env vars `JWT_PRIVATE_KEY` + `JWT_PUBLIC_KEY` (the PEM text itself) | PKCS#8 private key and X.509 public key, 2048 bits or more; tokens are signed RS256. The app refuses to start without a valid, matching pair |

Traces are never exported (no collector needed); trace ids are only used in logs and `traceparent` propagation.

Start the dependencies quickly with Docker:

```bash
docker run -d --name mongo  -p 27017:27017 mongo:7
docker run -d --name valkey -p 6379:6379 valkey/valkey:8
```

Create the key pair once (skip if you already have one). Keep the files outside the repository, or in the git-ignored `keys/` directory:

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl pkey -in keys/private.pem -pubout -out keys/public.pem
```

The service reads the key **contents** from environment variables, not file paths:

```bash
export JWT_PRIVATE_KEY="$(cat keys/private.pem)"
export JWT_PUBLIC_KEY="$(cat keys/public.pem)"
```

The whole PEM text is expected, including the `BEGIN`/`END` lines; a literal `
` instead of line breaks also works (useful for `.env` files and CI secrets). In IntelliJ, add both under Run Configuration > Environment variables (use the `
` form). Never commit the private key; changing the pair invalidates existing access tokens.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `JWT_ISSUER` | `http://localhost:8080/auth-service` | The `iss` claim of access tokens; tokens with another issuer are rejected |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | none (required) | The PEM contents of the RS256 signing and verification keys |
| `MONGO_TRANSACTIONS_ENABLED` | `true` | Enables `@Transactional` on MongoDB. Needs a replica set (production). On a standalone local MongoDB set it to `false`, or transactional methods fail |
| `VALKEY_HOST` / `VALKEY_PORT` | `localhost` / `6379` | Rate-limit store |
| `MONGO_CONNECT_TIMEOUT_MS`, `MONGO_SERVER_SELECTION_TIMEOUT_MS`, `MONGO_SOCKET_TIMEOUT_MS`, `MONGO_MAX_POOL_SIZE`, `MONGO_WAIT_QUEUE_TIMEOUT_MS` | 2000, 3000, 10000, 50, 3000 | Mongo client tuning |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Share of requests that get a trace id (no export) |

## Build and run

```bash
./mvnw clean package          # build the jar into target/
./mvnw spring-boot:run        # run the app on http://localhost:8080
```

Or run the jar directly:

```bash
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
```

On startup, Mongock applies the migrations in `kitchen.sink.demo.migration`:

- `V001`: unique index `uk_member_email` on `email`
- `V002`: `$jsonSchema` validator limiting `name` to 25 characters (replaced by `V014`, which splits `name` into `first_name`/`last_name` and removes `memberId`)
- later units add the session collection and its TTL index, the OAuth collections and (removed again by `V012`) the MFA challenge

## Tests

```bash
./mvnw test                                # all tests
./mvnw test -Dtest=ClassName#methodName    # a single test
```

- Controller tests use `@WebMvcTest`; service tests use Mockito.
- `AuthApplicationTests` is a `@SpringBootTest` and needs running MongoDB (and the other services above).
- The JaCoCo report is written to `target/site/jacoco/index.html`.
Example:

```bash
curl -X POST http://localhost:8080/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com","phoneNo":"1234567890","password":"correct horse battery staple"}'

# login is the hosted form at GET /login (reached from GET /oauth2/authorize); its POST /login answers 302 to the client
# redirect URI with the authorization code, and tokens come from POST /oauth2/token
```

### Validation rules

- `firstName`, `lastName`: each required, at most 25 characters, no digits
- `email`: required, valid email format, unique
- `phoneNo`: required, 10 to 12 digits
- `password`: 15 to 64 characters, counted in Unicode code points. Any character is allowed (spaces, Unicode, symbols), nothing is trimmed or truncated, and there are no composition rules. This follows the password guidelines of NIST SP 800-63B and OWASP. The limits are fixed constants in `PasswordPolicy`, the same in every environment

### Error responses

All errors use the same body: `{ "status": 400, "message": "...", "timestamp": "..." }`.

| Status | Cause |
|---|---|
| 400 | Request body or parameter validation failed |
| 409 | A member with that email already exists |

### Docs and health

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/openapi.yaml
- Health: http://localhost:8080/actuator/health

## Project layout

```
controller/   REST endpoints
service/      interfaces, impl/ holds implementations
repository/   Spring Data MongoDB repositories
entity/       Member document
dto/          request and response records
mapper/       MapStruct mappers
exception/    custom exceptions and GlobalExceptionHandler
config/       Mongo, validation and Swagger configuration
migration/    Mongock change units
```

- Registration: ⧉ https://claude.ai/artifact/CmHgCxCRc9mz4zj9dLDAVh
- Login: ⧉ https://claude.ai/artifact/5eESVpgEYWXWUYRAeCz9ju
- PKCE implementation: https://claude.ai/artifact/3fgVrvctUMBEPcEJiyb6c2