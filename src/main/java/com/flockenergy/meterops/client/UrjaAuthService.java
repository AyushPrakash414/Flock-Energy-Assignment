package com.flockenergy.meterops.client;

import com.flockenergy.meterops.config.AppProperties;
import com.flockenergy.meterops.exception.UpstreamPortalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the authentication session with the upstream Urja portal.
 * <p>
 * The portal uses form-based login ({@code POST /login}) with an
 * {@code Origin} header guard against cross-site submissions.
 * On success it sets a {@code __Secure-better-auth.session_token}
 * cookie with a 1-hour TTL.
 * <p>
 * This service caches the session token in an {@link AtomicReference}
 * and uses a {@link ReentrantLock} to prevent thundering-herd
 * re-authentication on concurrent 401s.
 */
@Service
public class UrjaAuthService {

    private static final Logger log = LoggerFactory.getLogger(UrjaAuthService.class);
    private static final String COOKIE_NAME = "__Secure-better-auth.session_token";

    private final RestClient restClient;
    private final AppProperties props;

    private final AtomicReference<String> sessionToken = new AtomicReference<>();
    private final ReentrantLock loginLock = new ReentrantLock();

    public UrjaAuthService(RestClient restClient, AppProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    /**
     * Returns a valid session token, authenticating if necessary.
     */
    public String getSessionToken() {
        String token = sessionToken.get();
        if (token != null) {
            return token;
        }
        return authenticate();
    }

    /**
     * Forces a fresh login and returns the new session token.
     * Thread-safe: only one login proceeds at a time; concurrent
     * callers receive the result of the winning login.
     */
    public String authenticate() {
        loginLock.lock();
        try {
            // Double-check: another thread may have already logged in
            String existing = sessionToken.get();
            if (existing != null) {
                return existing;
            }

            log.info("Authenticating with Urja portal");

            String formBody = "email=" + encodeValue(props.getEmail())
                    + "&password=" + encodeValue(props.getPassword());

            // We need the raw HTTP response to extract the Set-Cookie header.
            // RestClient.exchange() gives us access to the response.
            String newToken = restClient.post()
                    .uri("/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Origin", props.getBaseUrl())
                    .header("Referer", props.getBaseUrl() + "/login")
                    .body(formBody)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()
                                && response.getStatusCode().value() != 303) {
                            throw new UpstreamPortalException(
                                    "Portal login failed with status " + response.getStatusCode(),
                                    response.getStatusCode().value());
                        }

                        List<String> cookies = response.getHeaders().getOrDefault("Set-Cookie", List.of());
                        return cookies.stream()
                                .filter(c -> c.contains(COOKIE_NAME))
                                .map(this::extractTokenValue)
                                .findFirst()
                                .orElseThrow(() -> new UpstreamPortalException(
                                        "Login succeeded but no session cookie was returned"));
                    });

            sessionToken.set(newToken);
            log.info("Successfully authenticated with Urja portal");
            return newToken;

        } finally {
            loginLock.unlock();
        }
    }

    /**
     * Clears the cached token, forcing re-authentication on the next call.
     */
    public void invalidateSession() {
        log.debug("Invalidating cached portal session");
        sessionToken.set(null);
    }

    /**
     * Extracts the token value from a Set-Cookie header string.
     * Example input:
     * {@code __Secure-better-auth.session_token=abc123.xyz; Max-Age=3600; Path=/; ...}
     */
    private String extractTokenValue(String setCookieHeader) {
        String prefix = COOKIE_NAME + "=";
        int start = setCookieHeader.indexOf(prefix);
        if (start == -1) {
            throw new UpstreamPortalException("Cookie header does not contain " + COOKIE_NAME);
        }
        String afterPrefix = setCookieHeader.substring(start + prefix.length());
        int end = afterPrefix.indexOf(';');
        return end == -1 ? afterPrefix : afterPrefix.substring(0, end);
    }

    private String encodeValue(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
