package kitchen.sink.demo.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitchen.sink.demo.dto.request.TokenRequest;
import kitchen.sink.demo.dto.response.TokenResponse;
import kitchen.sink.demo.service.TokenExchangeService;
import kitchen.sink.demo.service.grant.GrantHandler;
import kitchen.sink.demo.service.grant.GrantHandlerFactory;
import org.springframework.stereotype.Service;

/** The token endpoint's logic: hands the request to the handler of its grant type. */
@Service
public class TokenExchangeServiceImpl implements TokenExchangeService {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeServiceImpl.class);

    private final GrantHandlerFactory grantHandlers;

    public TokenExchangeServiceImpl(GrantHandlerFactory grantHandlers) {
        this.grantHandlers = grantHandlers;
    }

    @Override
    public TokenResponse exchange(TokenRequest request, String ipAddress) {
        log.debug("token exchange for client {}", request.clientId());
        GrantHandler handler = grantHandlers.forGrantType(request.grantType());
        handler.validateMandatoryParams(request);
        return handler.handle(request, ipAddress);
    }
}
