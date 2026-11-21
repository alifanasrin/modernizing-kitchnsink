# OAuth 2.1 PKCE Flow

Authorization code with PKCE for the public client in DEMO-UI. The backend (auth-service) is the authorization server; the browser only carries redirects, and every call to the backend is made by the Auth BFF (the DEMO-UI server). The Auth UI only renders pages and talks to the BFF.

```mermaid
sequenceDiagram
    autonumber
    actor B as User / Browser
    participant UI as Auth UI
    participant BFF as Auth BFF
    participant API as auth-service

    rect rgba(150,170,170,0.15)
    Note over B,API: 1 · Start: authorize request (server side)
    B->>UI: Open /login (no active flow)
    UI-->>B: Redirect to /api/oauth/start?state= (state = page the user came from)
    B->>BFF: GET /api/oauth/start?state=
    Note over BFF: Creates code_verifier (random).<br/>code_challenge = BASE64URL(SHA-256(verifier))
    BFF->>API: GET /oauth2/authorize<br/>response_type=code, client_id, redirect_uri,<br/>code_challenge, method=S256, state
    alt Scenario A · client not registered, or redirect URI not an exact match
        Note over API: Nothing is stored. The redirect URI cannot be trusted,<br/>so the user is never redirected to it.
        API--xBFF: 400 Bad Request
        Note over BFF: No cookies are set.
        BFF--xB: Redirect to /invalid-client (own page, not the client's redirect URI)
        B->>UI: GET /invalid-client
        UI-->>B: Show "invalid client" error page
        Note over B,API: Journey stops here. The user cannot log in or continue.
    else Scenario B · valid request
        Note over API: Store grant, status REQUESTED:<br/>random requestId, challenge, state, TTL
        API-->>BFF: 302 to login page ?request_id=
        Note over BFF: Reads request_id from the Location header.<br/>Sets HttpOnly cookies (10 min):<br/>pkce-verifier, oauth-request, oauth-state
        BFF-->>B: Redirect to /login?request_id=
        B->>UI: GET /login?request_id=
        UI-->>B: Render login form
    end
    end

    rect rgba(150,170,170,0.15)
    Note over B,API: 🔒 User now starts their authentication step with their password
    end

    rect rgba(150,170,170,0.15)
    Note over B,API: 3 · Authorization code exchange
    API-->>BFF: 302 Location = redirect_uri?code=#38;state=#38;iss=
    BFF-->>UI: 302 to redirect_uri with code
    UI-->>B: Navigate to /callback
    B->>BFF: GET /callback?code=#38;state=
    Note over BFF: state must equal the oauth-state cookie (or both absent)#59;<br/>verifier cookie must exist. Otherwise the flow aborts.
    end

    rect rgba(150,170,170,0.15)
    Note over B,API: 4 · Token exchange
    BFF->>API: POST /oauth2/token<br/>grant_type=authorization_code, code,<br/>client_id, code_verifier
    Note over API: GrantHandlerFactory picks AuthorizationCodeGrantHandler.<br/>1) Mark code USED atomically (a used or unknown code is just refused with 400).<br/>2) Check client.<br/>3) PKCE: BASE64URL(SHA-256(code_verifier)) must equal the stored challenge.
    alt any check fails
        API--xBFF: 400 invalid_grant, same message for every refusal<br/>(the code stays burnt: no verifier guessing)
        BFF--xB: Redirect to /login?error=token_failed
        B->>UI: GET /login?error=token_failed
        UI-->>B: Show error, restart flow
    else all checks pass
        Note over API: Create session: SHA-256 of refresh secret,<br/>clientId, ip, TTL
        API-->>BFF: Response:<br/>access JWT (RS256, 15 min) + refresh token #lt;sessionId#gt;.#lt;secret#gt; (7 d)
        Note over BFF: Sets HttpOnly cookies: access-token,<br/>refresh-token (path /api/auth only).<br/>Clears the flow cookies.
        BFF-->>B: Redirect to state, else /home
    end
    end
```

## Rules the flow enforces

- PKCE is `S256` only; `plain` is not accepted. The verifier never leaves the HttpOnly cookie and the Auth BFF.
- Redirect URIs are matched exactly. An unregistered client or URI gets a 400 from auth-service and is never redirected to; the Auth BFF sends the user to its own `/invalid-client` page.
- `state` and `iss` (RFC 9207) are echoed unchanged on every redirect to the client, success and error.
- The authorization code is single use and lives 60 seconds. It is marked used before anything else is checked, so a wrong verifier cannot be retried.
- New grant types need only a new `GrantHandler` service.
