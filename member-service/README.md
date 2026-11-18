# member-service

Read-only member APIs, protected by the access tokens that `../auth-service` issues.

| Endpoint | Description |
|---|---|
| `GET /members?pageNo=1&pageSize=10` | Page of members sorted by last name, then first name (`pageSize` 1 to 50) |
| `GET /members/{email}` | One member by email, 404 if unknown |
| `GET /actuator/health` | Public health check |

All other requests need `Authorization: Bearer <access token>`.

## Run

```bash
export JWT_PUBLIC_KEY="$(cat /path/to/public.pem)"   # the same public key auth-service verifies with
mvn spring-boot:run                                   # http://localhost:8081
```

| Variable | Default | Meaning |
|---|---|---|
| `JWT_PUBLIC_KEY` | none, required | PEM contents of auth-service's public key |
| `JWT_ISSUER` | `http://localhost:8080/auth-service` | Required `iss` of tokens |
| `JWT_AUDIENCE` | `member-service` | Value that must be in the token's `aud` |
| `MONGODB_URI` / `MONGODB_DATABASE` | `mongodb://localhost:27017` / `auth-db` | The database auth-service writes to |
| `SERVER_PORT` | `8081` | Port |

A client calling this service needs `member-service` in its `aud` list in auth-service's `oauth_client` collection, for example `aud: ['auth-service', 'member-service']`. Existing clients must be updated, or their tokens get a 401 here.
