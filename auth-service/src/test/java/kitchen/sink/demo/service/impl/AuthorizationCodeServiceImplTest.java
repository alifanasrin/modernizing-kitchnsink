package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.config.JwtProperties;
import kitchen.sink.demo.dto.request.AuthorizationRequest;
import kitchen.sink.demo.dto.response.AuthorizationCodeResponse;
import kitchen.sink.demo.entity.AuthorizationGrant;
import kitchen.sink.demo.entity.OAuthClient;
import kitchen.sink.demo.exception.InvalidAuthorizationRequestException;
import kitchen.sink.demo.repository.AuthorizationGrantStore;
import kitchen.sink.demo.repository.OAuthClientRepository;
import kitchen.sink.demo.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationCodeServiceImplTest {

    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    static final String URI = "https://app.example.com/cb";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    static final String ISSUER = "https://auth.example.com";
    static final String LOGIN = "http://localhost:3000/login";

    @Mock OAuthClientRepository clients;
    @Mock AuthorizationGrantStore grants;
    AuthorizationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationCodeServiceImpl(clients, grants, Clock.fixed(NOW, ZoneOffset.UTC),
                new ClientRedirects(new JwtProperties(null, null, ISSUER, null, null, null)), LOGIN);
    }

    private static AuthorizationRequest request(String type, String client, String uri, String challenge, String method) {
        return new AuthorizationRequest(type, client, uri, challenge, method);
    }

    private void givenClient() {
        when(clients.findByClientId("spa")).thenReturn(Optional.of(new OAuthClient("spa", "Web app", java.util.List.of("api"), Set.of(URI))));
    }

    @Test
    @Tag("critical")
    void authorize_storesTheRequestAndRedirectsToTheLoginPage() {
        givenClient();

        String location = service.authorize(request("code", "spa", URI, CHALLENGE, "S256"));

        ArgumentCaptor<AuthorizationGrant> grant = ArgumentCaptor.forClass(AuthorizationGrant.class);
        verify(grants).save(grant.capture());
        assertThat(location).isEqualTo(LOGIN + "?request_id=" + grant.getValue().getRequestId());
        assertThat(grant.getValue().getCodeChallenge()).isEqualTo(CHALLENGE);
        assertThat(grant.getValue().getClientId()).isEqualTo("spa");
        assertThat(grant.getValue().getUserId()).isNull();
        assertThat(grant.getValue().getCodeHash()).isNull();
    }

    @Test
    @Tag("critical")
    void authorize_neverRedirects_whenClientIsUnknown() {
        when(clients.findByClientId("spa")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(request("code", "spa", URI, CHALLENGE, "S256")))
                .isInstanceOf(InvalidAuthorizationRequestException.class);
        verify(grants, never()).save(any());
    }

    @Test
    @Tag("critical")
    void authorize_neverRedirects_whenRedirectUriIsNotRegisteredExactly() {
        givenClient();

        assertThatThrownBy(() -> service.authorize(request("code", "spa", URI + "/", CHALLENGE, "S256")))
                .isInstanceOf(InvalidAuthorizationRequestException.class);
        assertThatThrownBy(() -> service.authorize(request("code", "spa", "https://evil.example/cb", CHALLENGE, "S256")))
                .isInstanceOf(InvalidAuthorizationRequestException.class);
        assertThatThrownBy(() -> service.authorize(request("code", "spa", null, CHALLENGE, "S256")))
                .isInstanceOf(InvalidAuthorizationRequestException.class);
    }

    @Test
    @Tag("critical")
    void authorize_redirectsWithAnError_whenPkceIsMissingOrNotS256() {
        givenClient();

        assertThat(service.authorize(request("code", "spa", URI, null, "S256")))
                .startsWith(URI + "?error=invalid_request");
        assertThat(service.authorize(request("code", "spa", URI, "short", "S256")))
                .startsWith(URI + "?error=invalid_request");
        assertThat(service.authorize(request("code", "spa", URI, CHALLENGE, "plain")))
                .startsWith(URI + "?error=invalid_request");
        assertThat(service.authorize(request("code", "spa", URI, CHALLENGE, null)))
                .startsWith(URI + "?error=invalid_request");
        verify(grants, never()).save(any());
    }

    @Test
    void authorize_redirectsWithAnError_whenResponseTypeIsNotCode() {
        givenClient();

        assertThat(service.authorize(request("token", "spa", URI, CHALLENGE, "S256")))
                .startsWith(URI + "?error=unsupported_response_type")
                .doesNotContain("state");
    }

    @Test
    void validate_acceptsAnOpenRequest_andRefusesAnUnknownOne() {
        when(grants.findOpen("r1", NOW)).thenReturn(Optional.of(new AuthorizationGrant("r1", "spa", URI, CHALLENGE, NOW, NOW)));
        when(grants.findOpen("r2", NOW)).thenReturn(Optional.empty());

        service.validate("r1");
        assertThatThrownBy(() -> service.validate("r2")).isInstanceOf(InvalidAuthorizationRequestException.class);
    }

    @Test
    @Tag("critical")
    void authorize_storesTheState_andEchoesItWithTheIssuerInAnErrorRedirect() {
        givenClient();

        String ok = service.authorize(new AuthorizationRequest("code", "spa", URI, CHALLENGE, "S256", "xyz 1&2"));
        ArgumentCaptor<AuthorizationGrant> grant = ArgumentCaptor.forClass(AuthorizationGrant.class);
        verify(grants).save(grant.capture());
        assertThat(grant.getValue().getState()).isEqualTo("xyz 1&2");
        assertThat(ok).doesNotContain("xyz");

        assertThat(service.authorize(new AuthorizationRequest("token", "spa", URI, CHALLENGE, "S256", "xyz 1&2")))
                .startsWith(URI + "?error=unsupported_response_type")
                .contains("&state=xyz%201%262&iss=https://auth.example.com");
    }

    @Test
    void authorize_errorRedirectCarriesTheIssuer_butNoStateWhenNoneWasSent() {
        givenClient();

        assertThat(service.authorize(request("token", "spa", URI, CHALLENGE, "S256")))
                .endsWith("&iss=https://auth.example.com")
                .doesNotContain("state");
    }

    @Test
    @Tag("critical")
    void complete_redirectsWithTheCodeOnce_andStoresOnlyItsHash() {
        AuthorizationGrant issued = new AuthorizationGrant(null, "spa", URI, CHALLENGE, NOW,
                NOW.plusSeconds(60));
        when(grants.issueCode(eq("r1"), eq("ABCD2345"), any(), eq(NOW), eq(NOW.plusSeconds(60))))
                .thenReturn(Optional.of(issued));

        AuthorizationCodeResponse r = service.complete("r1", "ABCD2345");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(grants).issueCode(eq("r1"), eq("ABCD2345"), hash.capture(), any(), any());
        assertThat(r.redirectTo()).startsWith(URI + "?code=");
        String code = r.redirectTo().substring((URI + "?code=").length(), r.redirectTo().indexOf("&iss="));
        assertThat(hash.getValue()).isEqualTo(TokenHasher.hash(code)).isNotEqualTo(code);
        assertThat(r.expiresIn()).isEqualTo(60);
    }

    @Test
    @Tag("critical")
    void complete_echoesTheStateAndIssuer() {
        AuthorizationGrant issued = new AuthorizationGrant("r1", "spa", URI, CHALLENGE, "s-1", NOW, NOW);
        when(grants.issueCode(any(), any(), any(), any(), any())).thenReturn(Optional.of(issued));

        String redirect = service.complete("r1", "ABCD2345").redirectTo();

        assertThat(redirect).contains("&state=s-1&iss=https://auth.example.com");
    }

    @Test
    @Tag("critical")
    void complete_failsWhenNoPendingRequestMatches() {
        when(grants.issueCode(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete("r1", "ABCD2345"))
                .isInstanceOf(InvalidAuthorizationRequestException.class);
    }
}
