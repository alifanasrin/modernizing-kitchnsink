package kitchen.sink.demo.service.grant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kitchen.sink.demo.exception.InvalidGrantException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Finds the {@link GrantHandler} for a {@code grant_type}. Two handlers for one type fail at startup. */
@Service
public class GrantHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(GrantHandlerFactory.class);

    static final String UNSUPPORTED = "Unsupported grant_type";

    private final Map<String, GrantHandler> handlers;

    public GrantHandlerFactory(List<GrantHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(GrantHandler::grantType,
                Function.identity()));
        log.info("grant types registered: {}", this.handlers.keySet());
    }

    /** Returns the handler for the grant type, or throws {@link InvalidGrantException} if there is none. */
    public GrantHandler forGrantType(String grantType) {
        GrantHandler handler = grantType == null ? null : handlers.get(grantType);
        if (handler == null) {
            log.warn("token request refused, unsupported grant type");
            throw new InvalidGrantException(UNSUPPORTED);
        }
        return handler;
    }
}
