package kitchen.sink.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Set;

/**
 * An application registered to use the authorization-code + PKCE flow. All clients are public (browser or mobile
 * apps cannot keep a secret), so there is no client secret: PKCE is what binds the code to the app that asked for it.
 * Clients are registered by inserting a document; an unknown {@code clientId} or an unregistered redirect URI is
 * always refused.
 */
@Document(collection = "oauth_client")
@Getter
@NoArgsConstructor
public class OAuthClient {
    @Id
    private String id;

    /** The public identifier the app sends; unique. */
    private String clientId;

    /** A display name; not used in tokens. */
    private String name;

    /** The audiences ({@code aud} claim) of the access tokens issued to this client; at least one is required. */
    private List<String> aud;

    /** Allowed redirect URIs; a request must match one of them exactly. */
    private Set<String> redirectUris;

    private boolean active;

    public OAuthClient(String clientId, String name, List<String> aud, Set<String> redirectUris) {
        this.clientId = clientId;
        this.name = name;
        this.aud = aud;
        this.redirectUris = redirectUris;
        this.active = true;
    }

    /** Tells whether the client is enabled and the redirect URI is registered (exact match). */
    public boolean allows(String redirectUri) {
        return active && redirectUris != null && redirectUris.contains(redirectUri);
    }
}
