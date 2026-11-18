# Login Flow

Password only (there is no second factor and no account blocking). Login ends with a one-time authorization code, not tokens. See `oauth2-pkce-flow.md` for what happens next.

```mermaid
---
title: User Authentication Flow
---
sequenceDiagram
    autonumber
    actor B as User / Browser
    participant UI as Auth UI
    participant BFF as Auth BFF
    participant API as auth-service

    rect rgba(150,170,170,0.15)
    Note over B,API: 1 · Password
    B->>UI: Enter email + password
    UI->>BFF: Submit credentials
    BFF->>API: POST /auth/login<br/>email, password, requestId (from cookie)
    alt Unknown or expired requestId
        API--xBFF: 400 (member is not looked up)
        BFF--xUI: Login failed
        UI-->>B: Show error, restart the flow
    else Wrong email or password
        API--xBFF: Same 401 either way<br/>401 Invalid email or password
        BFF--xUI: Login failed
        UI-->>B: Show error
    else Correct password
        Note over API: Password accepted. No session or tokens yet.<br/>The grant becomes a one-time authorization code.
        API-->>BFF: 302<br/>Location = redirect_uri?code=#38;state=#38;iss=
        BFF-->>UI: 302 to redirect_uri with code
        UI-->>B: Navigate to /callback
        Note over B,API: User is authenticated. Continue with the OAuth 2.1 PKCE flow, authorization code exchange.
    end
    end
```
