package kitchen.sink.demo.service.impl;

import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.service.grant.GrantHandler;
import kitchen.sink.demo.service.grant.GrantHandlerFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenExchangeServiceImplTest {

    @Test
    void exchange_delegatesToTheHandlerOfTheGrantType() {
        GrantHandler refresh = mock(GrantHandler.class);
        when(refresh.grantType()).thenReturn("refresh_token");
        TokenRequest request = new TokenRequest("refresh_token", "spa", null, null, "s.r");
        TokenResponse tokens = new TokenResponse("jwt", "Bearer", 900, "s.r2", "s");
        when(refresh.handle(request, "ip")).thenReturn(tokens);

        TokenExchangeServiceImpl service = new TokenExchangeServiceImpl(new GrantHandlerFactory(List.of(refresh)));

        assertThat(service.exchange(request, "ip")).isSameAs(tokens);
        verify(refresh).validateMandatoryParams(request);
    }

    @Test
    void exchange_invalidParams_neverReachesHandle() {
        GrantHandler refresh = mock(GrantHandler.class);
        when(refresh.grantType()).thenReturn("refresh_token");
        TokenRequest request = new TokenRequest("refresh_token", "spa", null, null, null);
        doThrow(new kitchen.sink.demo.exception.InvalidGrantException("Missing parameter: refresh_token"))
                .when(refresh).validateMandatoryParams(request);
        TokenExchangeServiceImpl service = new TokenExchangeServiceImpl(new GrantHandlerFactory(List.of(refresh)));

        assertThatThrownBy(() -> service.exchange(request, "ip"))
                .isInstanceOf(kitchen.sink.demo.exception.InvalidGrantException.class);
        verify(refresh, never()).handle(any(), any());
    }
}
