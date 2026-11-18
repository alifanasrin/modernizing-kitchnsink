package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.config.JwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the redirects back to a client's registered redirect URI (RFC 6749 section 4.1.2): the success redirect with
 * the code and the error redirect. Both echo the client's {@code state} unchanged when it sent one (section 4.1.2 /
 * 4.1.2.1) and carry the issuer identifier {@code iss} (RFC 9207), so a client talking to several authorization
 * servers can tell which one answered.
 */
@Component
public class ClientRedirects {

    private final String issuer;

    public ClientRedirects(JwtProperties properties) {
        this.issuer = properties.issuer();
    }

    public String success(String redirectUri, String code, String state) {
        return withStateAndIssuer(UriComponentsBuilder.fromUriString(redirectUri).queryParam("code", code), state);
    }

    public String error(String redirectUri, String error, String description, String state) {
        return withStateAndIssuer(UriComponentsBuilder.fromUriString(redirectUri).queryParam("error", error)
                .queryParam("error_description", description), state);
    }

    private String withStateAndIssuer(UriComponentsBuilder builder, String state) {
        if (state != null) {
            builder.queryParam("state", state);
        }
        return builder.queryParam("iss", issuer).build().encode().toUriString();
    }
}
