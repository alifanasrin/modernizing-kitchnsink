package kitchen.sink.demo.service.grant;

import kitchen.sink.demo.exception.InvalidGrantException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrantHandlerFactoryTest {

    private static GrantHandler handler(String type) {
        GrantHandler h = mock(GrantHandler.class);
        when(h.grantType()).thenReturn(type);
        return h;
    }

    @Test
    @Tag("critical")
    void forGrantType_returnsTheHandlerOfThatType() {
        GrantHandler code = handler("authorization_code");
        GrantHandler refresh = handler("refresh_token");
        GrantHandlerFactory factory = new GrantHandlerFactory(List.of(code, refresh));

        assertThat(factory.forGrantType("authorization_code")).isSameAs(code);
        assertThat(factory.forGrantType("refresh_token")).isSameAs(refresh);
    }

    @Test
    @Tag("critical")
    void forGrantType_refusesUnknownOrMissingTypes() {
        GrantHandlerFactory factory = new GrantHandlerFactory(List.of(handler("authorization_code")));

        assertThatThrownBy(() -> factory.forGrantType("password")).isInstanceOf(InvalidGrantException.class);
        assertThatThrownBy(() -> factory.forGrantType(null)).isInstanceOf(InvalidGrantException.class);
    }

    @Test
    void constructor_failsWhenTwoHandlersClaimTheSameType() {
        assertThatThrownBy(() -> new GrantHandlerFactory(List.of(handler("refresh_token"), handler("refresh_token"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
