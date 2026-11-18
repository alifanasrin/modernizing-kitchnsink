package kitchen.sink.demo.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One document per login. Only the SHA-256 hash of the current refresh-token secret is stored; the secret rotates
 * on every refresh. The session is the unit of revocation: revoking it invalidates its refresh token.
 */
@Document(collection = "session")
@Getter
@NoArgsConstructor
public class Session {
    @Id
    private String id;

    /** The Mongo {@code _id} (hex ObjectId) of the owning member. */
    @NotBlank
    private String userId;

    @NotBlank
    private String refreshTokenHash;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant lastUsedAt;

    @NotNull
    private Instant expiresAt;

    private String ipAddress;

    /** The OAuth client the session was issued to; its refresh token only works for this client. Null on old sessions. */
    private String clientId;

    public Session(String userId, String clientId, String refreshTokenHash, Instant now, Instant expiresAt,
                   String ipAddress) {
        this(null, userId, clientId, refreshTokenHash, now, expiresAt, ipAddress);
    }

    /** A session whose id was chosen by the caller, because the tokens that name it were created first. */
    public Session(String id, String userId, String clientId, String refreshTokenHash, Instant now, Instant expiresAt,
                   String ipAddress) {
        this.id = id;
        this.userId = userId;
        this.clientId = clientId;
        this.refreshTokenHash = refreshTokenHash;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
    }

    }
