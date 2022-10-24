package me.lucko.bytebin.util;

import io.jooby.Context;
import io.jooby.StatusCode;
import io.jooby.exception.StatusCodeException;
import me.lucko.bytebin.http.PostHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class AuthorizationHandler {

    /** Logger instance */
    private static final Logger LOGGER = LogManager.getLogger(PostHandler.class);

    private final Map<String, String> authKeys;

    public AuthorizationHandler(Map<String, String> authKeys) {
        this.authKeys = authKeys;
    }

    public void checkAuthorization(Context context) {
        String authKey = context.header("Authorization").valueOrNull();
        if (authKey == null) {
            LOGGER.info("[GET] Denied access to client with no auth key");
            throw new StatusCodeException(StatusCode.UNAUTHORIZED, "Authorization header not present");
        }

        if (!this.authKeys.containsKey(authKey)) {
            LOGGER.info("[GET] Denied access to key: " + authKey);
            throw new StatusCodeException(StatusCode.UNAUTHORIZED, "Invalid authentication key");
        }
    }
}
